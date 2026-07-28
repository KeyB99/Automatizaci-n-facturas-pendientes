package com.monitor.invoices;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final MonitorRepository monitorRepository;
    private final EmailService emailService;

    public InvoiceMonitorJob(MonitorRepository monitorRepository, EmailService emailService) {
        this.monitorRepository = monitorRepository;
        this.emailService = emailService;
    }

    /**
     * Se ejecuta cada hora.
     * Cron: segundo=0, minuto=0, hora=todas, dia=*, mes=*, diasemana=*
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void monitorMissingInvoices() {

        LocalDate hoy = LocalDate.now();
        LocalDate fechaInicio;

        // Lógica de 1ra semana vs resto del mes
        if (hoy.getDayOfMonth() <= 7) {
            // Si estamos en la primera semana (días 1 a 7), ir 1 semana atrás del día 1 del
            // mes actual
            fechaInicio = hoy.withDayOfMonth(1).minusDays(7);
        } else {
            // A partir del día 8, empezar desde el 1 del mes actual
            fechaInicio = hoy.withDayOfMonth(1);
        }

        String fechaDesde = fechaInicio.format(DATE_FMT) + " 00:00:00";

        log.info("============================================================");
        log.info("Iniciando monitoreo de facturas faltantes (Flujo en 2 pasos)...");
        log.info("Consultando faltantes desde: {}", fechaDesde);

        try {
            log.info("Paso 1: Insertando datos en invoice_control...");
            monitorRepository.insertMissingInvoicesControl(fechaDesde);

            log.info("Paso 2: Consultando faltantes...");
            List<Map<String, Object>> rows = monitorRepository.getMissingInvoices(fechaDesde);

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
