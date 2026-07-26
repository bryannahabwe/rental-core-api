package com.cognix.rentalcoreapi.modules.notification;

/**
 * Delivers a single HTML email. Exactly one implementation is active at a time:
 * {@link BrevoEmailSender} when {@code app.mail.brevo.enabled=true}, otherwise
 * {@link LoggingEmailSender} (dev/no-credentials fallback that logs instead).
 */
public interface EmailSender {

    void send(String toEmail, String subject, String htmlBody);
}
