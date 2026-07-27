package com.cognix.rentalcoreapi.modules.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * EMAIL via the Brevo transactional email HTTP API (adapted from
 * sacco-core-api's BrevoEmailSender). Active only when
 * {@code app.mail.brevo.enabled=true}; otherwise {@link LoggingEmailSender} is
 * used instead. Credentials/sender identity are env-backed — no secrets in code.
 * Sends synchronously and throws on failure.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.mail.brevo.enabled", havingValue = "true")
public class BrevoEmailSender implements EmailSender {

    private static final String SEND_PATH = "/v3/smtp/email";

    private final RestClient restClient;
    private final String fromEmail;
    private final String fromName;

    public BrevoEmailSender(
            @Value("${app.mail.brevo.api-key:}") String apiKey,
            @Value("${app.mail.brevo.from-email:}") String fromEmail,
            @Value("${app.mail.brevo.from-name:RentFlow}") String fromName) {
        this.fromEmail = fromEmail;
        this.fromName = fromName;
        // Use the static builder rather than injecting a RestClient.Builder
        // bean, which isn't auto-configured in every setup.
        this.restClient = RestClient.builder()
                .baseUrl("https://api.brevo.com")
                .defaultHeader("api-key", apiKey)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public void send(String toEmail, String subject, String htmlBody) {
        Map<String, Object> payload = Map.of(
                "sender", Map.of("email", fromEmail, "name", fromName),
                "to", List.of(Map.of("email", toEmail)),
                "subject", subject,
                "htmlContent", htmlBody);

        restClient.post()
                .uri(SEND_PATH)
                .body(payload)
                .retrieve()
                .toBodilessEntity();

        log.info("Sent email to {} via Brevo: {}", toEmail, subject);
    }
}
