package com.trustify.scheduler;

import com.trustify.model.Transaction;
import com.trustify.repository.TransactionRepository;
import com.trustify.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private final TransactionRepository txRepo;
    private final EmailService emailService;
    private final JavaMailSender mailSender;

    // every day at 09:00 server time
    @Scheduled(cron = "0 0 9 * * *")
    public void sendDailyReminders() {
        // TODO: query DB for upcoming payments / returns and send emails.
//        SimpleMailMessage msg = new SimpleMailMessage();
//        msg.setTo("user@example.com"); // placeholder
//        msg.setSubject("Trustify reminder (test)");
//        msg.setText("This is a test reminder. Replace with real logic.");
//        mailSender.send(msg);
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        // Fetch transactions that end tomorrow
        List<Transaction> rentalsEndingTomorrow = txRepo.findAllEndingWithinDays(tomorrow, today);

        for (Transaction tx : rentalsEndingTomorrow) {
            if (!tx.isReminderSent()) { // optional flag to prevent duplicates
                emailService.sendEmail(
                        tx.getBuyerEmail(),
                        "Rental Ending Soon",
                        "<p>Your rental for item <b>" + tx.getListingId() +
                                "</b> ends tomorrow. Please prepare to return it.</p>"
                );

                // Mark reminder as sent
                tx.setReminderSent(true);
                txRepo.save(tx);
            }
        }
    }

}