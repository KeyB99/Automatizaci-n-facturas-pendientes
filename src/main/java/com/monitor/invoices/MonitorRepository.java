package com.monitor.invoices;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class MonitorRepository {

    private final JdbcTemplate jdbcTemplate;

    public MonitorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Paso 1 de Facturas Faltantes:
     * Inserta los rangos de facturas esperadas en la tabla de control.
     *
     * @param fechaDesde La fecha desde la cual se verifican las facturas.
     */
    public void insertMissingInvoicesControl(String fechaDesde) {
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
                    WHERE iv.instant >= ?::TIMESTAMP
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

        jdbcTemplate.update(insertSql, fechaDesde);
    }

    /**
     * Paso 2 de Facturas Faltantes:
     * Consulta las facturas que faltan cruzando lo esperado vs lo real.
     *
     * @param fechaDesde La fecha desde la cual se verifican las facturas.
     * @return Lista de facturas faltantes.
     */
    public List<Map<String, Object>> getMissingInvoices(String fechaDesde) {
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

        return jdbcTemplate.queryForList(selectSql, fechaDesde);
    }

    /**
     * Consulta las resoluciones de facturación próximas a vencer.
     *
     * @return Lista de resoluciones con sus diferencias de días.
     */
    public List<Map<String, Object>> getExpiringResolutions() {
        String query = """
                SELECT 
                    e.company, 
                    c."name" AS company_name, 
                    e.code AS enumeration_code,
                    e.initial_date,
                    e.final_date,
                    e.current_value,
                    e.final_value,
                    (e.final_date::date - CURRENT_DATE) AS date_difference_days,
                    (e.current_value - e.final_value) AS TOTAL   
                FROM billing.enumeration e
                INNER JOIN  core.company c ON e.company = c.code
                INNER JOIN billing.resolution r ON r.enumeration = e.code
                INNER JOIN machine.device d ON r.device = d.code
                WHERE d.device_type = '1322'
                AND e.status = 'A'
                AND c.status = 'A'
                ORDER BY date_difference_days ASC
                """;

        return jdbcTemplate.queryForList(query);
    }
}
