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

    /**
     * Envía un correo HTML con el reporte de estado de facturas recientes por dispositivo.
     *
     * @param rows Lista de filas retornadas por getRecentInvoicesStatus()
     */
    public void sendRecentInvoicesStatusAlert(List<Map<String, Object>> rows) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(DESTINATARIO);
            helper.setSubject("📊 Reporte Estado Facturas Recientes — " + LocalDateTime.now().format(DT_FMT));
            helper.setText(buildRecentInvoicesHtmlBody(rows), true);

            mailSender.send(message);
            log.info("✉️  Correo de reporte de facturas recientes enviado a {} con {} dispositivos.", DESTINATARIO, rows.size());

        } catch (Exception e) {
            log.error("❌ Error enviando correo de reporte de facturas recientes: {}", e.getMessage(), e);
        }
    }

    /**
     * Construye el cuerpo HTML del reporte de estado de facturas recientes,
     * agrupado por empresa.
     */
    private String buildRecentInvoicesHtmlBody(List<Map<String, Object>> rows) {

        // Agrupar por empresa
        Map<String, List<Map<String, Object>>> porEmpresa = rows.stream()
                .collect(Collectors.groupingBy(
                        row -> String.valueOf(row.get("company"))
                ));

        StringBuilder sb = new StringBuilder();

        sb.append("""
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; background: #f4f4f4; padding: 20px; color: #333; }
                    .container { background: white; border-radius: 8px; padding: 30px; max-width: 1000px; margin: auto;
                                 box-shadow: 0 2px 8px rgba(0,0,0,0.1); border-top: 5px solid #2980b9; }
                    h2 { color: #1a5276; border-bottom: 2px solid #2980b9; padding-bottom: 8px; }
                    h3 { color: #2c3e50; margin-top: 24px; }
                    .badge { background: #2980b9; color: white; border-radius: 12px;
                             padding: 2px 10px; font-size: 13px; margin-left: 8px; }
                    table { width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 13px; }
                    th { background: #1a5276; color: white; padding: 10px; text-align: left; }
                    td { padding: 8px 10px; border-bottom: 1px solid #eee; }
                    tr:nth-child(even) td { background: #f2f8fd; }
                    .footer { margin-top: 30px; font-size: 12px; color: #888; text-align: center; }
                    .info-box { background: #eaf4fb; border-left: 4px solid #2980b9; padding: 12px 16px;
                                border-radius: 4px; margin-bottom: 20px; }
                    .date-old   { color: #c0392b; font-weight: bold; }
                    .date-mid   { color: #d35400; font-weight: bold; }
                    .date-ok    { color: #27ae60; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h2>📊 Reporte: Estado de Facturas Recientes por Dispositivo</h2>
                    <div class="info-box">
                        <strong>🕐 Generado:</strong> """).append(LocalDateTime.now().format(DT_FMT)).append("""
                        <br>
                        <strong>📦 Total dispositivos:</strong> """).append(rows.size()).append("""
                        <br>
                        <em>Los dispositivos se muestran ordenados por fecha de última factura (más antiguos primero).</em>
                    </div>
            """);

        for (Map.Entry<String, List<Map<String, Object>>> entry : porEmpresa.entrySet()) {
            String empresa = entry.getKey();
            List<Map<String, Object>> dispositivos = entry.getValue();

            sb.append("<h3>🏢 ").append(empresa)
              .append(" <span class='badge'>").append(dispositivos.size()).append(" dispositivos</span></h3>")
              .append("<table>")
              .append("<tr>")
              .append("<th>#</th>")
              .append("<th>Dispositivo</th>")
              .append("<th>Nick Name</th>")
              .append("<th>Última Factura</th>")
              .append("<th>Prefijo</th>")
              .append("<th>Última Fecha</th>")
              .append("</tr>");

            int idx = 1;
            for (Map<String, Object> d : dispositivos) {
                String device    = String.valueOf(d.get("device"));
                String nickName  = String.valueOf(d.get("nick_name"));
                String bill      = String.valueOf(d.get("bill"));
                String prefix    = String.valueOf(d.get("prefix"));
                String lastDate  = String.valueOf(d.get("last_date"));

                // Colorear según antigüedad de la última factura
                String dateClass = "date-ok";
                try {
                    java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(
                            lastDate.replace(" ", "T").substring(0, 19));
                    long hoursAgo = java.time.Duration.between(ldt, java.time.LocalDateTime.now()).toHours();
                    if (hoursAgo > 48) dateClass = "date-old";
                    else if (hoursAgo > 24) dateClass = "date-mid";
                } catch (Exception ignored) { /* si no parsea, mantiene color verde */ }

                sb.append("<tr>")
                  .append("<td>").append(idx++).append("</td>")
                  .append("<td>").append(device).append("</td>")
                  .append("<td>").append(nickName).append("</td>")
                  .append("<td>").append(bill).append("</td>")
                  .append("<td>").append(prefix).append("</td>")
                  .append("<td class='").append(dateClass).append("'>").append(lastDate).append("</td>")
                  .append("</tr>");
            }

            sb.append("</table>");
        }

        sb.append("""
                    <div class='footer'>
                        Este correo fue generado automáticamente por el monitor de facturas.<br>
                        Ejecutándose a las 8:00 AM y 5:00 PM | Sistema de Monitoreo Gastoncito
                    </div>
                </div>
            </body>
            </html>
            """);

        return sb.toString();
    }

    /**
     * Envía un correo HTML con la lista de resoluciones por vencerse detectadas.
     *
     * @param resoluciones Lista de filas con información de las resoluciones
     */
    public void sendExpiringResolutionsAlert(List<Map<String, Object>> resoluciones) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(DESTINATARIO);
            helper.setSubject("⚠️ Alerta: Resoluciones por vencerse — " + LocalDateTime.now().format(DT_FMT));
            helper.setText(buildResolutionsHtmlBody(resoluciones), true);

            mailSender.send(message);
            log.info("✉️  Correo de alerta enviado a {} con {} resoluciones por vencerse.", DESTINATARIO, resoluciones.size());

        } catch (Exception e) {
            log.error("❌ Error enviando correo de alerta de resoluciones: {}", e.getMessage(), e);
        }
    }

    /**
     * Construye el cuerpo HTML del correo con la tabla de resoluciones por vencerse,
     * agrupadas por empresa.
     */
    private String buildResolutionsHtmlBody(List<Map<String, Object>> rows) {

        // Agrupar resoluciones por empresa
        Map<String, List<Map<String, Object>>> porEmpresa = rows.stream()
                .collect(Collectors.groupingBy(
                        row -> String.valueOf(row.get("company_name"))
                ));

        StringBuilder sb = new StringBuilder();

        sb.append("""
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; background: #f4f4f4; padding: 20px; color: #333; }
                    .container { background: white; border-radius: 8px; padding: 30px; max-width: 900px; margin: auto;
                                 box-shadow: 0 2px 8px rgba(0,0,0,0.1); border-top: 5px solid #e67e22; }
                    h2 { color: #d35400; border-bottom: 2px solid #e67e22; padding-bottom: 8px; }
                    h3 { color: #2c3e50; margin-top: 24px; }
                    .badge { background: #e67e22; color: white; border-radius: 12px;
                             padding: 2px 10px; font-size: 13px; margin-left: 8px; }
                    table { width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 14px; }
                    th { background: #2c3e50; color: white; padding: 10px; text-align: left; }
                    td { padding: 8px 10px; border-bottom: 1px solid #eee; }
                    tr:nth-child(even) td { background: #f9f9f9; }
                    .footer { margin-top: 30px; font-size: 12px; color: #888; text-align: center; }
                    .info-box { background: #fef9e7; border-left: 4px solid #f39c12; padding: 12px 16px;
                                border-radius: 4px; margin-bottom: 20px; }
                    .urgente { color: #c0392b; font-weight: bold; }
                    .medio { color: #d35400; font-weight: bold; }
                    .bajo { color: #f39c12; font-weight: bold; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h2>⚠️ Alerta: Resoluciones por vencerse</h2>
                    <div class="info-box">
                        <strong>🕐 Hora de revisión:</strong> """).append(LocalDateTime.now().format(DT_FMT)).append("""
                        <br>
                        <strong>📦 Total resoluciones en alerta:</strong> """).append(rows.size()).append("""
                    </div>
                """);

        // Una tabla por empresa
        for (Map.Entry<String, List<Map<String, Object>>> entry : porEmpresa.entrySet()) {
            String empresa = entry.getKey();
            List<Map<String, Object>> resoluciones = entry.getValue();

            sb.append("<h3>🏢 ").append(empresa)
              .append(" <span class='badge'>").append(resoluciones.size()).append("</span></h3>")
              .append("<table>")
              .append("<tr>")
              .append("<th>Resolución</th>")
              .append("<th>F. Inicial</th>")
              .append("<th>F. Final</th>")
              .append("<th>Días Restantes</th>")
              .append("<th>V. Actual</th>")
              .append("<th>V. Final</th>")
              .append("<th>Diferencia Val.</th>")
              .append("</tr>");

            for (Map<String, Object> res : resoluciones) {
                String enumCode = String.valueOf(res.get("enumeration_code"));
                String fInicial = String.valueOf(res.get("initial_date"));
                String fFinal = String.valueOf(res.get("final_date"));
                String vActual = String.valueOf(res.get("current_value"));
                String vFinal = String.valueOf(res.get("final_value"));
                String difVal = String.valueOf(res.get("TOTAL"));
                
                int dateDifference = 0;
                Object dateDiffObj = res.get("date_difference_days");
                if (dateDiffObj != null) {
                    dateDifference = ((Number) dateDiffObj).intValue();
                }

                String claseDias = "";
                String textoDias = "";
                if (dateDifference < 0) {
                    claseDias = "urgente";
                    textoDias = "Vencida hace " + Math.abs(dateDifference) + " días";
                } else if (dateDifference == 0) {
                    claseDias = "urgente";
                    textoDias = "¡Vence hoy!";
                } else {
                    textoDias = dateDifference + " días";
                    if (dateDifference <= 7) claseDias = "urgente";
                    else if (dateDifference <= 30) claseDias = "medio";
                    else claseDias = "bajo";
                }

                sb.append("<tr>")
                  .append("<td>").append(enumCode).append("</td>")
                  .append("<td>").append(fInicial).append("</td>")
                  .append("<td>").append(fFinal).append("</td>")
                  .append("<td class='").append(claseDias).append("'>").append(textoDias).append("</td>")
                  .append("<td>").append(vActual).append("</td>")
                  .append("<td>").append(vFinal).append("</td>")
                  .append("<td>").append(difVal).append("</td>")
                  .append("</tr>");
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
}
