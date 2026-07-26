package com.cognix.rentalcoreapi.modules.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fallback {@link EmailSender} used when Brevo isn't enabled (dev / no
 * credentials). Instead of sending, it logs the email — so invite links are
 * still testable locally without a live email provider.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.mail.brevo.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

    @Override
    public void send(String toEmail, String subject, String htmlBody) {
        log.info("[EMAIL DISABLED] Would send to {} — subject: \"{}\"\n{}",
                toEmail, subject, htmlBody);
    }
}
