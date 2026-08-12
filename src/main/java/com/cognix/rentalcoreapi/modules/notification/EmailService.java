package com.cognix.rentalcoreapi.modules.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * Application-level email operations. Composes branded HTML and delegates
 * delivery to the active {@link EmailSender} (Brevo or the logging fallback).
 */
@Service
@RequiredArgsConstructor
public class EmailService {

    private final EmailSender emailSender;

    /**
     * Sends a "you've been invited" email with a link to accept and set a
     * password.
     */
    public void sendInvite(String toEmail, String inviteeName,
                           String accountName, String acceptUrl) {
        String subject = "You've been invited to " + accountName + " on RentFlow";
        String body = inviteHtml(inviteeName, accountName, acceptUrl);
        emailSender.send(toEmail, subject, body);
    }

    private String inviteHtml(String inviteeName, String accountName, String acceptUrl) {
        String safeName = HtmlUtils.htmlEscape(inviteeName == null ? "there" : inviteeName);
        String safeAccount = HtmlUtils.htmlEscape(accountName == null ? "RentFlow" : accountName);
        String safeUrl = HtmlUtils.htmlEscape(acceptUrl);

        return """
                <!DOCTYPE html>
                <html>
                <head><meta charset="utf-8"/>
                <meta name="viewport" content="width=device-width, initial-scale=1.0"/></head>
                <body style="margin:0; padding:28px 12px; background-color:#eef2ef; \
                font-family:-apple-system,'Segoe UI',Roboto,sans-serif;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"><tr><td align="center">
                    <table role="presentation" width="100%%" style="max-width:560px;" cellpadding="0" cellspacing="0">
                      <tr><td bgcolor="#0a4a38" style="background-color:#0a4a38; border-radius:16px 16px 0 0; \
                padding:24px 28px; color:#ffffff; font-size:18px; font-weight:600;">RentFlow</td></tr>
                      <tr><td style="background-color:#ffffff; padding:32px 28px; border:1px solid #e2e8e4; border-top:none;">
                        <p style="margin:0 0 12px; font-size:16px; color:#14291d;">Hi %s,</p>
                        <p style="margin:0 0 20px; font-size:14.5px; line-height:1.7; color:#3c463f;">
                          You've been invited to join <strong>%s</strong> on RentFlow. Click below to set your \
                password and get started.</p>
                        <div style="text-align:center; margin:8px 0 20px;">
                          <a href="%s" style="display:inline-block; padding:12px 28px; background-color:#0F6E56; \
                color:#ffffff; text-decoration:none; border-radius:8px; font-size:15px; font-weight:600;">\
                Accept invitation</a>
                        </div>
                        <p style="margin:0; font-size:12.5px; line-height:1.6; color:#8a9490;">
                          If the button doesn't work, copy this link into your browser:<br/>
                          <a href="%s" style="color:#0F6E56;">%s</a></p>
                      </td></tr>
                      <tr><td style="background-color:#f7faf9; border:1px solid #e2e8e4; border-top:none; \
                border-radius:0 0 16px 16px; padding:18px 28px;">
                        <p style="margin:0; font-size:11.5px; color:#8a9490;">\
                This invitation link expires in 72 hours. If you weren't expecting it, you can ignore this email.</p>
                      </td></tr>
                    </table>
                  </td></tr></table>
                </body></html>
                """.formatted(safeName, safeAccount, safeUrl, safeUrl, safeUrl);
    }

    /**
     * Sends a password-reset email with a one-time link to choose a new password.
     */
    public void sendPasswordReset(String toEmail, String name, String resetUrl) {
        String subject = "Reset your RentFlow password";
        String body = passwordResetHtml(name, resetUrl);
        emailSender.send(toEmail, subject, body);
    }

    private String passwordResetHtml(String name, String resetUrl) {
        String safeName = HtmlUtils.htmlEscape(name == null ? "there" : name);
        String safeUrl = HtmlUtils.htmlEscape(resetUrl);

        return """
                <!DOCTYPE html>
                <html>
                <head><meta charset="utf-8"/>
                <meta name="viewport" content="width=device-width, initial-scale=1.0"/></head>
                <body style="margin:0; padding:28px 12px; background-color:#eef2ef; \
                font-family:-apple-system,'Segoe UI',Roboto,sans-serif;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"><tr><td align="center">
                    <table role="presentation" width="100%%" style="max-width:560px;" cellpadding="0" cellspacing="0">
                      <tr><td bgcolor="#0a4a38" style="background-color:#0a4a38; border-radius:16px 16px 0 0; \
                padding:24px 28px; color:#ffffff; font-size:18px; font-weight:600;">RentFlow</td></tr>
                      <tr><td style="background-color:#ffffff; padding:32px 28px; border:1px solid #e2e8e4; border-top:none;">
                        <p style="margin:0 0 12px; font-size:16px; color:#14291d;">Hi %s,</p>
                        <p style="margin:0 0 20px; font-size:14.5px; line-height:1.7; color:#3c463f;">
                          We received a request to reset your password. Click below to choose a new one.</p>
                        <div style="text-align:center; margin:8px 0 4px;">
                          <a href="%s" style="display:inline-block; padding:12px 28px; background-color:#0F6E56; \
                color:#ffffff; text-decoration:none; border-radius:8px; font-size:15px; font-weight:600;">\
                Reset password</a>
                        </div>
                      </td></tr>
                      <tr><td style="background-color:#f7faf9; border:1px solid #e2e8e4; border-top:none; \
                border-radius:0 0 16px 16px; padding:18px 28px;">
                        <p style="margin:0; font-size:11.5px; color:#8a9490;">\
                This reset link expires in 1 hour. If you didn't request it, you can safely ignore this email — \
                your password won't change.</p>
                      </td></tr>
                    </table>
                  </td></tr></table>
                </body></html>
                """.formatted(safeName, safeUrl);
    }
}
