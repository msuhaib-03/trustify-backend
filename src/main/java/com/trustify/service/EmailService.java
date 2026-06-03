package com.trustify.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    /**
     * Sends an email asynchronously — caller returns immediately,
     * SMTP handshake happens in a background thread.
     */
    @Async
    public void sendEmail(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true); // HTML enabled

            mailSender.send(message);
            System.out.println("[Email] Sent to " + to + " — subject: " + subject);
        } catch (Exception e) {
            System.err.println("[Email] Failed to send to " + to + ": " + e.getMessage());
        }
    }

    @Async
    public void sendPaymentInitiatedEmail(String email, String txId) {
        sendEmail(email,
                "Payment Initiated - Transaction " + txId,
                "<h3>Your payment has been initiated.</h3><p>Transaction ID: " + txId + "</p>");
    }

    @Async
    public void sendEscrowReleasedEmail(String email, String txId) {
        sendEmail(email,
                "Escrow Released",
                "<p>Your escrow for transaction <b>" + txId + "</b> has been released.</p>");
    }

    @Async
    public void sendRefundEmail(String email, String txId) {
        sendEmail(email,
                "Refund Issued",
                "<p>Your refund for transaction <b>" + txId + "</b> has been issued successfully.</p>");
    }

    @Async
    public void sendDeliveryReminder(String email, String txId) {
        sendEmail(email,
                "Delivery Reminder",
                "<p>Please confirm your delivery for transaction <b>" + txId + "</b>.</p>");
    }

    @Async
    public void sendPasswordResetEmail(String email, String resetLink) {
        sendEmail(email,
                "Reset Your Trustify Password",
                "<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:24px;'>" +
                        "<div style='text-align:center;margin-bottom:24px;'>" +
                        "<h1 style='color:#6366f1;font-size:28px;margin:0;'>Trustify</h1>" +
                        "<p style='color:#888;font-size:13px;margin:4px 0 0;'>Secure Escrow Marketplace</p>" +
                        "</div>" +
                        "<div style='background:#f9fafb;border-radius:12px;padding:32px;border:1px solid #e5e7eb;'>" +
                        "<h2 style='color:#111827;font-size:22px;margin:0 0 12px;'>Reset Your Password</h2>" +
                        "<p style='color:#4b5563;line-height:1.6;margin:0 0 24px;'>We received a request to reset the password for your Trustify account.</p>" +
                        "<p style='color:#4b5563;line-height:1.6;margin:0 0 24px;'>Click the button below to set a new password. This link is valid for <strong>1 hour</strong>.</p>" +
                        "<div style='text-align:center;margin:32px 0;'>" +
                        "<a href='" + resetLink + "' style='background:linear-gradient(135deg,#6366f1,#8b5cf6);color:white;padding:14px 36px;border-radius:8px;text-decoration:none;font-weight:bold;font-size:16px;display:inline-block;'>Reset Password</a>" +
                        "</div>" +
                        "<p style='color:#6b7280;font-size:13px;margin:0;'>If the button doesn't work, copy and paste this link into your browser:</p>" +
                        "<p style='color:#6366f1;font-size:13px;word-break:break-all;margin:8px 0 0;'>" + resetLink + "</p>" +
                        "</div>" +
                        "<p style='color:#9ca3af;font-size:12px;text-align:center;margin:24px 0 0;'>If you didn't request a password reset, you can safely ignore this email. Your password won't change.</p>" +
                        "</div>");
    }


}
