package com.paygoon.service;

import com.paygoon.model.AppUser;
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

    public NotificationService(JavaMailSender mailSender,
                               @Value("${app.mail.from:no-reply@paygoon.com}") String fromEmail,
                               @Value("${app.verification.base-url:http://localhost:8080}") String verificationBaseUrl) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
        this.verificationBaseUrl = verificationBaseUrl;
    }

    public void sendVerificationEmail(AppUser user, String token, Duration expiration) {
        String link = verificationBaseUrl + "/api/auth/verify?token=" + token;
        String body = "Hola " + user.getName() + ",\n\n" +
                "Gracias por registrarte en PayGoon. Haz clic en el siguiente enlace para verificar tu correo:" +
                "\n" + link + "\n\n" +
                "El enlace vencerá en " + expiration.toHours() + " horas.";

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setFrom(fromEmail);
        message.setSubject("Verifica tu correo en PayGoon");
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
}
