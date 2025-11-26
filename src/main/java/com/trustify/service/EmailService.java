package com.trustify.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendEmail(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true); // HTML enabled

            mailSender.send(message);
        } catch (Exception e) {
            System.out.println("Failed to send email: " + e.getMessage());
        }
    }

    public void sendPaymentInitiatedEmail(String email, String txId) {
        sendEmail(email,
                "Payment Initiated - Transaction " + txId,
                "<h3>Your payment has been initiated.</h3><p>Transaction ID: " + txId + "</p>");
    }

    public void sendEscrowReleasedEmail(String email, String txId) {
        sendEmail(email,
                "Escrow Released",
                "<p>Your escrow for transaction <b>" + txId + "</b> has been released.</p>");
    }

    public void sendRefundEmail(String email, String txId) {
        sendEmail(email,
                "Refund Issued",
                "<p>Your refund for transaction <b>" + txId + "</b> has been issued successfully.</p>");
    }

    public void sendDeliveryReminder(String email, String txId) {
        sendEmail(email,
                "Delivery Reminder",
                "<p>Please confirm your delivery for transaction <b>" + txId + "</b>.</p>");
    }


}
