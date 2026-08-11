package com.monitor.invoices;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Job que consulta el estado de la última factura emitida por dispositivo
 * y envía un reporte por correo a las 8:00 AM y 5:00 PM todos los días.
 *
 * <p>Dispositivos incluidos: tipos CPA, ATM y PG, estado activo.</p>
 * <p>Empresas excluidas: códigos 137225, 101791, 148834, 147707, 147016, 322488, 147688, 147502.</p>
 */
@Service
public class RecentInvoicesJob {

    private static final Logger log = LoggerFactory.getLogger(RecentInvoicesJob.class);

    private final MonitorRepository monitorRepository;
    private final EmailService emailService;

    public RecentInvoicesJob(MonitorRepository monitorRepository, EmailService emailService) {
        this.monitorRepository = monitorRepository;
        this.emailService = emailService;
    }

    /**
     * Se ejecuta a las 8:00 AM y a las 5:00 PM todos los días.
     * Cron: segundo=0, minuto=0, hora=8 y 17, día=*, mes=*, díasemana=*
     */
    @Scheduled(cron = "0 0 8,17 * * ?")
    public void reportRecentInvoicesStatus() {

        log.info("============================================================");
        log.info("Iniciando reporte de estado de facturas recientes...");

        try {
            List<Map<String, Object>> rows = monitorRepository.getRecentInvoicesStatus();

            if (rows.isEmpty()) {
                log.warn("⚠️  La consulta de facturas recientes no retornó resultados.");
            } else {
                log.info("📋 Se encontraron {} dispositivos en el reporte.", rows.size());
                emailService.sendRecentInvoicesStatusAlert(rows);
            }

        } catch (Exception e) {
            log.error("❌ Error ejecutando el reporte de facturas recientes: {}", e.getMessage(), e);
        }

        log.info("Reporte de facturas recientes finalizado.");
        log.info("============================================================");
    }
}
