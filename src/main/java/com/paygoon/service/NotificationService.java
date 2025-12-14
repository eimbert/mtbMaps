package com.paygoon.service;

import com.paygoon.model.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    public void sendVerificationEmail(AppUser user, String token, Duration expiration) {
        String link = "/api/auth/verify?token=" + token;
        String body = "Hola " + user.getName() + ",\n\n" +
                "Gracias por registrarte en PayGoon. Haz clic en el siguiente enlace para verificar tu correo:" +
                "\n" + link + "\n\n" +
                "El enlace vencerá en " + expiration.toHours() + " horas.";

        log.info("[EMAIL] Enviando correo de verificación a {} con enlace {}", user.getEmail(), link);
        log.debug("[EMAIL BODY]\n{}", body);
    }
}
