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
     * Se ejecuta cada 4 minutos.
     * Cron: segundo=0, minuto=cada4, hora=*, dia=*, mes=*, diasemana=*
     */
    @Scheduled(cron = "0 */4 * * * ?")
    public void monitorMissingInvoices() {

        // Fecha dinámica: desde hace 30 días (para garantizar que detecte los saltos arrastrados)
        String fechaDesde = LocalDate.now().minusDays(30).format(DATE_FMT) + " 00:00:00";

        log.info("============================================================");
        log.info("Iniciando monitoreo de facturas faltantes...");
        log.info("Consultando desde: {}", fechaDesde);

        String sql = """
                WITH rangos AS (
                    SELECT
                        co.name,
                        iv.company,
                        iv.prefix,
                        MIN(iv.bill_number::BIGINT) AS mn,
                        MAX(iv.bill_number::BIGINT) AS mx
                    FROM billing.invoice iv
                    INNER JOIN core.company co ON iv.company = co.code
                    INNER JOIN core.company_info ci ON iv.company = ci.company
                    WHERE iv.instant >= ?::TIMESTAMP
                      AND ci.value = 'GASTONCITO'
                      AND iv.prefix IS NOT NULL
                      AND iv.prefix NOT IN ('FLY', 'GO', 'FLYPASS', 'fly')
                    GROUP BY co.name, iv.company, iv.prefix
                )
                SELECT
                    r.name,
                    r.company::TEXT || '-' || r.prefix || '-' || gs.n::TEXT AS prefix_number
                FROM rangos r
                CROSS JOIN LATERAL generate_series(r.mn, r.mx) AS gs(n)
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM billing.invoice e
                    WHERE e.company = r.company
                      AND e.prefix   = r.prefix
                      AND e.bill_number::BIGINT = gs.n
                      AND e.total > 0
                )
                ORDER BY r.name, gs.n
                """;

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, fechaDesde);

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
