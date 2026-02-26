package com.paygoon.service;

import com.paygoon.model.AppUser;
import com.paygoon.model.PlanFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JavaMailSender mailSender;
    private final String fromEmail;
    private final String verificationBaseUrl;
    private final String resetPasswordBaseUrl;
    private final String appHomeUrl;
    private final String mailHost;
    private final String mailPort;
    private final String mailUsername;
    private final String mailPassword;

    public NotificationService(JavaMailSender mailSender,
                               @Value("${app.mail.from:no-reply@paygoon.com}") String fromEmail,
                               @Value("${app.verification.base-url:http://localhost:8080}") String verificationBaseUrl,
                               @Value("${app.reset-password.base-url:https://www.tracketeo.bike/reset-password}") String resetPasswordBaseUrl,
                               @Value("${app.home-url:https://tracketeo.bike}") String appHomeUrl,
                               @Value("${spring.mail.host:unknown}") String mailHost,
                               @Value("${spring.mail.port:unknown}") String mailPort,
                               @Value("${spring.mail.username:}") String mailUsername,
                               @Value("${spring.mail.password:}") String mailPassword) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
        this.verificationBaseUrl = verificationBaseUrl;
        this.resetPasswordBaseUrl = resetPasswordBaseUrl;
        this.appHomeUrl = appHomeUrl;
        this.mailHost = mailHost;
        this.mailPort = mailPort;
        this.mailUsername = mailUsername;
        this.mailPassword = mailPassword;
    }

    public void sendVerificationEmail(AppUser user, String token, Duration expiration) {
        String link = verificationBaseUrl + "/api/auth/verify?token=" + token;
        String body = "Hola " + user.getName() + ",\n\n" +
                "Gracias por registrarte en Tracketeo.bike. Haz clic en el siguiente enlace para verificar tu correo:" +
                "\n" + link + "\n\n" +
                "El enlace vencerá en " + expiration.toHours() + " horas.";

        String maskedPassword = (mailPassword == null || mailPassword.isBlank()) ? "(vacía)" : "****";
        log.info("[EMAIL SMTP CONFIG] host={}, port={}, username={}, password={}",
                mailHost, mailPort, mailUsername, maskedPassword);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setFrom(fromEmail);
        message.setSubject("Verifica tu correo en Tracketeo.bike");
        message.setText(body);

        try {
            mailSender.send(message);
            log.info("[EMAIL] Enviando correo de verificación a {} con enlace {}", user.getEmail(), link);
            log.debug("[EMAIL BODY]\n{}", body);
        } catch (MailException ex) {
            log.error("No se pudo enviar el correo de verificación a {}", user.getEmail(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo enviar el correo de verificación");
        }
    }

    public void sendPlanFolderInvitationEmail(AppUser invitedUser, AppUser invitedBy, PlanFolder folder) {
        String inviterName = invitedBy != null && invitedBy.getName() != null && !invitedBy.getName().isBlank()
                ? invitedBy.getName()
                : "Un usuario";
        String folderName = folder != null && folder.getName() != null && !folder.getName().isBlank()
                ? folder.getName()
                : "tu carpeta de plan";

        String body = "Hola " + invitedUser.getName() + ",\n\n" +
                inviterName + " te ha invitado a la carpeta '" + folderName + "' en Tracketeo.bike.\n" +
                "Puedes entrar desde este enlace: " + appHomeUrl + "\n\n" +
                "Una vez dentro de la app, en el apartado 'Planifica tus próximas salidas', verás la invitación para aceptarla o rechazarla.";

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(invitedUser.getEmail());
        message.setFrom(fromEmail);
        message.setSubject("Tienes una invitación en Tracketeo.bike");
        message.setText(body);

        try {
            mailSender.send(message);
            log.info("[EMAIL] Invitación de carpeta enviada a {} para carpeta {}",
                    invitedUser.getEmail(), folder != null ? folder.getId() : null);
        } catch (MailException ex) {
            log.error("No se pudo enviar el correo de invitación a {}", invitedUser.getEmail(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo enviar el correo de invitación");
        }
    }

    public void sendPasswordResetEmail(AppUser user, String token, Duration expiration) {
        String link = resetPasswordBaseUrl + "?token=" + token;
        String body = "Hola " + user.getName() + ",\n\n" +
                "Hemos recibido una solicitud para restablecer tu contraseña en Tracketeo.bike.\n" +
                "Haz clic en el siguiente enlace para establecer una nueva contraseña:\n" + link + "\n\n" +
                "Este enlace vence en " + expiration.toMinutes() + " minutos. Si no solicitaste este cambio, puedes ignorar este correo.";

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setFrom(fromEmail);
        message.setSubject("Recuperación de contraseña en Tracketeo.bike");
        message.setText(body);

        try {
            mailSender.send(message);
            log.info("[EMAIL] Enviando correo de recuperación a {} con enlace {}", user.getEmail(), link);
        } catch (MailException ex) {
            log.error("No se pudo enviar el correo de recuperación a {}", user.getEmail(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo enviar el correo de recuperación");
        }
    }
}
