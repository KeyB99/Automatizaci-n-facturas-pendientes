package com.monitor.invoices;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class InvoiceMonitorJob {

    private static final Logger log = LoggerFactory.getLogger(InvoiceMonitorJob.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final JdbcTemplate jdbcTemplate;
    private final EmailService emailService;

    public InvoiceMonitorJob(JdbcTemplate jdbcTemplate, EmailService emailService) {
        this.jdbcTemplate = jdbcTemplate;
        this.emailService = emailService;
    }

    /**
     * Se ejecuta cada 15 minutos.
     * Cron: segundo=0, minuto=cada15, hora=*, dia=*, mes=*, diasemana=*
     */
    @Scheduled(cron = "0 */15 * * * ?")
    public void monitorMissingInvoices() {

        // Fecha dinámica para el SELECT: desde hace 30 días
        String fechaDesde = LocalDate.now().minusDays(30).format(DATE_FMT) + " 00:00:00";

        log.info("============================================================");
        log.info("Iniciando monitoreo de facturas faltantes (Flujo en 2 pasos)...");
        log.info("Consultando faltantes desde: {}", fechaDesde);

        String insertSql = """
                WITH rangos AS (
                    SELECT
                        iv.company,
                        iv.prefix,
                        MIN(iv.bill_number::BIGINT) AS mn,
                        MAX(iv.bill_number::BIGINT) AS mx
                    FROM billing.invoice iv
                    INNER JOIN core.company co ON iv.company = co.code
                    INNER JOIN core.company_info ci ON iv.company = ci.company
                    WHERE iv.instant >= now()::DATE - '4 DAY'::INTERVAL
                      --AND ci.value = 'GASTONCITO'
                      AND iv.prefix IS NOT NULL


                      AND iv.prefix NOT IN ('FLY', 'GO', 'FLYPASS', 'fly')
                    GROUP BY co.name, iv.company, iv.prefix
                )
                INSERT INTO billing.invoice_control
                SELECT
                    r.company ,
                    r.prefix,
                    gs.n number
                FROM rangos r
                CROSS JOIN LATERAL generate_series(r.mn, r.mx) AS gs(n)
                ON CONFLICT (company,prefix,number) DO NOTHING;
                """;

        String selectSql = """
                WITH resume AS (
                    SELECT
                        iv.company,
                        iv.prefix,
                        MIN(iv.bill_number::BIGINT) AS mn,
                        MAX(iv.bill_number::BIGINT) AS mx
                    FROM billing.invoice iv
                    WHERE iv.instant >= ?::TIMESTAMP
                      AND iv.prefix IS NOT NULL
                      AND iv.prefix NOT IN ('FLY', 'GO', 'FLYPASS', 'fly')
                    GROUP BY iv.company, iv.prefix
                )
                SELECT 
                   co.name,
                   ctrl.company::TEXT || '-' || ctrl.prefix || '-' || ctrl.number::TEXT AS prefix_number
                FROM billing.invoice_control ctrl
                INNER JOIN core.company co ON ctrl.company = co.code
                INNER JOIN resume re
                    ON ctrl.company = re.company 
                    AND ctrl.prefix = re.prefix 
                    AND ctrl.number >= re.mn 
                    AND ctrl.number <= re.mx
                WHERE NOT EXISTS (
                    SELECT 1 
                    FROM billing.invoice iv 
                    WHERE iv.company = ctrl.company
                      AND iv.prefix = ctrl.prefix    
                      AND iv.bill_number = ctrl.number::TEXT
                )
                ORDER BY co.name, ctrl.company, ctrl.prefix, ctrl.number
                """;

        try {
            log.info("Paso 1: Insertando datos en invoice_control...");
            jdbcTemplate.update(insertSql);

            log.info("Paso 2: Consultando faltantes...");
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(selectSql, fechaDesde);

            if (rows.isEmpty()) {
                log.info("✅ No se encontraron facturas faltantes.");
                // Enviar correo de que todo está OK
                emailService.sendNoMissingInvoicesAlert(fechaDesde);
            } else {
                log.warn("⚠️  Se encontraron {} facturas faltantes.", rows.size());
                rows.forEach(
                        row -> log.warn("  → Empresa: {} | Factura: {}", row.get("name"), row.get("prefix_number")));

                // Enviar correo de alerta
                emailService.sendMissingInvoicesAlert(rows, fechaDesde);
            }

        } catch (Exception e) {
            log.error("❌ Error ejecutando la consulta de facturas faltantes: {}", e.getMessage(), e);
        }

        log.info("Monitoreo finalizado.");
        log.info("============================================================");
    }
}
