package com.monitor.invoices;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final String DESTINATARIO = "alertaaccess@gmail.com";
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Envía un correo HTML con la lista de facturas faltantes detectadas.
     *
     * @param facturasFaltantes Lista de filas con 'name' y 'prefix_number'
     * @param fechaDesde        Fecha desde la cual se realizó la consulta
     */
    public void sendMissingInvoicesAlert(List<Map<String, Object>> facturasFaltantes, String fechaDesde) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(DESTINATARIO);
            helper.setSubject("⚠️ Facturas Faltantes Detectadas — " + LocalDateTime.now().format(DT_FMT));
            helper.setText(buildHtmlBody(facturasFaltantes, fechaDesde), true);

            mailSender.send(message);
            log.info("✉️  Correo de alerta enviado a {} con {} facturas faltantes.", DESTINATARIO, facturasFaltantes.size());

        } catch (Exception e) {
            log.error("❌ Error enviando correo de alerta: {}", e.getMessage(), e);
        }
    }

    /**
     * Construye el cuerpo HTML del correo con la tabla de facturas faltantes,
     * agrupadas por empresa.
     */
    private String buildHtmlBody(List<Map<String, Object>> rows, String fechaDesde) {

        // Agrupar facturas por empresa
        Map<String, List<String>> porEmpresa = rows.stream()
                .collect(Collectors.groupingBy(
                        row -> String.valueOf(row.get("name")),
                        Collectors.mapping(row -> String.valueOf(row.get("prefix_number")), Collectors.toList())
                ));

        StringBuilder sb = new StringBuilder();

        sb.append("""
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; background: #f4f4f4; padding: 20px; color: #333; }
                    .container { background: white; border-radius: 8px; padding: 30px; max-width: 800px; margin: auto;
                                 box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
                    h2 { color: #c0392b; border-bottom: 2px solid #c0392b; padding-bottom: 8px; }
                    h3 { color: #2c3e50; margin-top: 24px; }
                    .badge { background: #e74c3c; color: white; border-radius: 12px;
                             padding: 2px 10px; font-size: 13px; margin-left: 8px; }
                    table { width: 100%; border-collapse: collapse; margin-top: 10px; }
                    th { background: #2c3e50; color: white; padding: 10px; text-align: left; }
                    td { padding: 8px 10px; border-bottom: 1px solid #eee; }
                    tr:nth-child(even) td { background: #f9f9f9; }
                    .footer { margin-top: 30px; font-size: 12px; color: #888; text-align: center; }
                    .info-box { background: #fef9e7; border-left: 4px solid #f39c12; padding: 12px 16px;
                                border-radius: 4px; margin-bottom: 20px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h2>⚠️ Alerta: Facturas Faltantes Detectadas</h2>
                    <div class="info-box">
                        <strong>📅 Período consultado:</strong> Desde """).append(fechaDesde).append("""
                         hasta hoy<br>
                        <strong>🕐 Hora de detección:</strong> """).append(LocalDateTime.now().format(DT_FMT)).append("""
                        <br>
                        <strong>📦 Total faltantes:</strong> """).append(rows.size()).append("""
                    </div>
                """);

        // Una tabla por empresa
        for (Map.Entry<String, List<String>> entry : porEmpresa.entrySet()) {
            String empresa = entry.getKey();
            List<String> facturas = entry.getValue();

            sb.append("<h3>🏢 ").append(empresa)
              .append(" <span class='badge'>").append(facturas.size()).append(" faltantes</span></h3>")
              .append("<table>")
              .append("<tr><th>#</th><th>Número de Factura</th></tr>");

            int idx = 1;
            for (String factura : facturas) {
                sb.append("<tr><td>").append(idx++).append("</td><td>").append(factura).append("</td></tr>");
            }

            sb.append("</table>");
        }

        sb.append("""
                    <div class='footer'>
                        Este correo fue generado automáticamente por el monitor de facturas.<br>
                        Ejecutándose cada 10 minutos | Sistema de Monitoreo Gastoncito
                    </div>
                </div>
            </body>
            </html>
            """);

        return sb.toString();
    }

    /**
     * Envía un correo notificando que no se encontraron facturas faltantes.
     */
    public void sendNoMissingInvoicesAlert(String fechaDesde) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(DESTINATARIO);
            helper.setSubject("✅ Todo OK: Sin facturas faltantes — " + LocalDateTime.now().format(DT_FMT));
            
            String htmlBody = """
                <html>
                <head>
                    <style>
                        body { font-family: Arial, sans-serif; background: #f4f4f4; padding: 20px; color: #333; }
                        .container { background: white; border-radius: 8px; padding: 30px; max-width: 800px; margin: auto;
                                     box-shadow: 0 2px 8px rgba(0,0,0,0.1); border-top: 5px solid #27ae60; }
                        h2 { color: #27ae60; }
                        .footer { margin-top: 30px; font-size: 12px; color: #888; text-align: center; }
                        .info-box { background: #eafaf1; border-left: 4px solid #2ecc71; padding: 12px 16px;
                                    border-radius: 4px; margin-bottom: 20px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <h2>✅ Todo al día</h2>
                        <div class="info-box">
                            No se detectaron saltos ni facturas faltantes en la sincronización.
                            <br><br>
                            <strong>📅 Período consultado:</strong> Desde %s hasta hoy<br>
                            <strong>🕐 Hora de revisión:</strong> %s
                        </div>
                        <div class='footer'>
                            Este correo fue generado automáticamente por el monitor de facturas.<br>
                            Sistema de Monitoreo Gastoncito
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(fechaDesde, LocalDateTime.now().format(DT_FMT));

            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("✉️  Correo de confirmación (Todo OK) enviado a {}.", DESTINATARIO);

        } catch (Exception e) {
            log.error("❌ Error enviando correo de confirmación: {}", e.getMessage(), e);
        }
    }
}
