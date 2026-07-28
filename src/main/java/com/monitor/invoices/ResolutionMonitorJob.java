package com.monitor.invoices;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ResolutionMonitorJob {

    private static final Logger log = LoggerFactory.getLogger(ResolutionMonitorJob.class);

    private final MonitorRepository monitorRepository;
    private final EmailService emailService;

    // Control de último envío en memoria: clave = company + "-" + enumeration_code, valor = LocalDate
    private final Map<String, LocalDate> lastEmailSentMap = new ConcurrentHashMap<>();

    public ResolutionMonitorJob(MonitorRepository monitorRepository, EmailService emailService) {
        this.monitorRepository = monitorRepository;
        this.emailService = emailService;
    }

    /**
     * Se ejecuta cada 10 minutos.
     * Cron: segundo=0, minuto=0/10, hora=*, dia=*, mes=*, diasemana=*
     */
    @Scheduled(cron = "0 0/10 * * * ?")
    public void monitorExpiringResolutions() {
        log.info("============================================================");
        log.info("Iniciando monitoreo de resoluciones por vencerse...");

        try {
            List<Map<String, Object>> rows = monitorRepository.getExpiringResolutions();
            List<Map<String, Object>> resolutionsToAlert = new ArrayList<>();
            LocalDate today = LocalDate.now();

            for (Map<String, Object> row : rows) {
                // Validación robusta del tipo de dato de date_difference_days (generalmente Integer pero puede ser Long/BigInteger)
                Object dateDiffObj = row.get("date_difference_days");
                if (dateDiffObj == null) {
                    continue; // Saltar si la fecha es nula
                }
                
                int dateDifference = ((Number) dateDiffObj).intValue();
                
                String company = String.valueOf(row.get("company"));
                String enumerationCode = String.valueOf(row.get("enumeration_code"));
                String key = company + "-" + enumerationCode;
                
                LocalDate lastSent = lastEmailSentMap.get(key);
                boolean shouldSend = false;

                if (dateDifference > 30 && dateDifference <= 60) {
                    // Mayor a 30 días o hasta 2 meses: enviar un correo por mes
                    if (lastSent == null || ChronoUnit.MONTHS.between(lastSent.withDayOfMonth(1), today.withDayOfMonth(1)) >= 1) {
                        shouldSend = true;
                    }
                } else if (dateDifference > 7 && dateDifference <= 30) {
                    // Menor de 30 días pero mayor a 7 días: enviar un correo por semana
                    if (lastSent == null || ChronoUnit.DAYS.between(lastSent, today) >= 7) {
                        shouldSend = true;
                    }
                } else if (dateDifference <= 7) {
                    // 7 días o menos: enviar un correo todos los días
                    if (lastSent == null || lastSent.isBefore(today)) {
                        shouldSend = true;
                    }
                }

                if (shouldSend) {
                    resolutionsToAlert.add(row);
                    lastEmailSentMap.put(key, today);
                }
            }

            if (!resolutionsToAlert.isEmpty()) {
                log.warn("⚠️  Se encontraron {} resoluciones que requieren alerta.", resolutionsToAlert.size());
                emailService.sendExpiringResolutionsAlert(resolutionsToAlert);
            } else {
                log.info("✅ No hay alertas de resoluciones por enviar en este momento.");
            }

        } catch (Exception e) {
            log.error("❌ Error ejecutando la consulta de resoluciones por vencerse: {}", e.getMessage(), e);
        }

        log.info("Monitoreo finalizado.");
        log.info("============================================================");
    }
}
