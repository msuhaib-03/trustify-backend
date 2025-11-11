package com.trustify.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReminderScheduler {
    private final JavaMailSender mailSender;

    // every day at 09:00 server time
    @Scheduled(cron = "0 0 9 * * *")
    public void sendDailyReminders() {
        // TODO: query DB for upcoming payments / returns and send emails.
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo("user@example.com"); // placeholder
        msg.setSubject("Trustify reminder (test)");
        msg.setText("This is a test reminder. Replace with real logic.");
        mailSender.send(msg);
    }
}
