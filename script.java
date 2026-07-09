import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InvoiceMonitorJob {

    private static final Logger log = LoggerFactory.getLogger(InvoiceMonitorJob.class);
    private final JdbcTemplate jdbcTemplate;

    public InvoiceMonitorJob(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Ejecuta todos los días a las 2:00 AM (puedes ajustar el cron)
    // Formato: segundo, minuto, hora, día del mes, mes, día de la semana

    @Scheduled(cron = "0 5 0 * * ?") 
    public void monitorMissingInvoices() {
        log.info("Iniciando tarea de monitoreo de facturas faltantes...");

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
                WHERE iv.instant >= '2026-06-01 00:00:00' 
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
                  AND e.prefix = r.prefix 
                  AND e.bill_number::BIGINT = gs.n 
                  AND e.total > 0
            )
            """;

        try {
            List<MissingInvoice> missingInvoices = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new MissingInvoice(
                    rs.getString("name"),
                    rs.getString("prefix_number")
                )
            );

            if (missingInvoices.isEmpty()) {
                log.info("Excelente: No se encontraron facturas faltantes en los parqueaderos.");
            } else {
                log.warn("¡Atención! Se encontraron {} facturas faltantes.", missingInvoices.size());
                
                // Aquí puedes agregar tu lógica de alertas (enviar email, Slack, guardar en BD, etc.)
                for (MissingInvoice missing : missingInvoices) {
                    log.warn("Faltante en parqueadero '{}' - Factura: {}", 
                             missing.name(), missing.prefixNumber());
                }
            }