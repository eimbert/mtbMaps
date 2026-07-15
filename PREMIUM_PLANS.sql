-- Haz una copia de seguridad antes de ejecutar cambios manuales.

-- Convertir tu cuenta en administradora (acceso ilimitado sin pagar).
UPDATE users
SET account_plan = 'ADMIN', rol = 'ADMIN', premium = 1,
    lifetime_premium = 0, premium_until = NULL
WHERE email = 'TU_EMAIL@DOMINIO.COM';

-- Regalar Premium para siempre a un amigo.
UPDATE users
SET account_plan = 'PREMIUM', premium = 1,
    lifetime_premium = 1, premium_until = NULL
WHERE email = 'EMAIL_DEL_AMIGO@DOMINIO.COM';

-- Devolver una cuenta al plan gratuito.
UPDATE users
SET account_plan = 'FREE', premium = 0,
    lifetime_premium = 0, premium_until = NULL
WHERE email = 'EMAIL_DEL_USUARIO@DOMINIO.COM';
