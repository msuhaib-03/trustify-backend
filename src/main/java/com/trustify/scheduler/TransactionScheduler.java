package com.trustify.scheduler;


import com.trustify.model.Transaction;
import com.trustify.repository.TransactionRepository;
import com.trustify.service.EmailService;
import com.trustify.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TransactionScheduler {

    private final TransactionRepository txRepo;
    private final TransactionService escrowService;
    private final EmailService emailService;

    // 1️⃣ Auto-cancel (seller inactive > 24 hours)
    @Scheduled(cron = "0 0 * * * *") // every hour
    public void autoCancelUnaccepted() {

        List<Transaction> pending =
                txRepo.findAllByStatusAndCreatedAtBefore(
                        "PENDING_SELLER",
                        LocalDateTime.now().minusHours(24)
                );

        for (Transaction tx : pending) {

            // refund automatically
            escrowService.refund(tx.getId(), tx.getAmountCents());

            // emails
            emailService.sendRefundEmail(tx.getBuyerEmail(), tx.getId());
            emailService.sendEmail(tx.getSellerEmail(),
                    "Order Auto-Cancelled",
                    "Seller did not accept in 24 hours.");

            tx.setStatus(Transaction.TransactionStatus.valueOf("AUTO_CANCELLED"));
            txRepo.save(tx);
        }
    }

    // 2️⃣ Auto-confirm delivery (buyer inactive > 48 hrs)
    @Scheduled(cron = "0 */30 * * * *") // every 30 mins
    public void autoDeliverShipped() {

        List<Transaction> shipped =
                txRepo.findAllByStatusAndShippedAtBefore(
                        "SHIPPED",
                        LocalDateTime.now().minusHours(48)
                );

        for (Transaction tx : shipped) {

            escrowService.capture(tx.getId(),"SYSTEM", null);

            emailService.sendEscrowReleasedEmail(tx.getSellerEmail(), tx.getId());
            emailService.sendEmail(tx.getBuyerEmail(),
                    "Delivery Auto-Confirmed",
                    "We auto-confirmed the delivery after 48 hours.");

            tx.setStatus(Transaction.TransactionStatus.valueOf("DELIVERED_AUTO"));
            txRepo.save(tx);
        }
    }


    // 3️⃣ Auto rental end handling
    @Scheduled(cron = "0 0 * * * *") // every hour
    public void autoRentalEnd() {

        List<Transaction> rentals =
                txRepo.findAllByRentalEndBeforeAndStatus(
                        LocalDate.now(),
                        "RENT_ACTIVE"
                );

        for (Transaction tx : rentals) {

            if (!tx.isDamageReported()) {

                // Pass the seller's stored ID so the isTheSeller() check passes.
                // The status guard in finalizeRefund also allows RENTAL_IN_PROGRESS
                // so the scheduler can auto-close rentals that the renter never marked returned.
                escrowService.finalizeRefund(tx.getId(), tx.getSellerId());

                emailService.sendEscrowReleasedEmail(tx.getBuyerEmail(), tx.getId());
                emailService.sendEmail(tx.getSellerEmail(),
                        "Rental Completed",
                        "Deposit released — no damage reported.");

            } else {
                // damage case
                emailService.sendEmail(tx.getSellerEmail(),
                        "Damage Reported",
                        "Admin will now review your damage claim.");

                emailService.sendEmail(tx.getBuyerEmail(),
                        "Damage Case Pending",
                        "Admin is reviewing the damage report.");
            }

            tx.setStatus(Transaction.TransactionStatus.valueOf("RENT_COMPLETED"));
            txRepo.save(tx);
        }
    }

    // 4️⃣ Auto-complete SALE transactions stuck at PENDING_RELEASE
    // Covers transactions created before the auto-capture fix: buyer confirmed
    // receipt but the old code required a seller click that never came.
    @Scheduled(cron = "0 0 * * * *") // every hour
    public void autoCompletePendingReleaseSales() {
        Instant cutoff = Instant.now().minus(1, ChronoUnit.HOURS); // stuck > 1 hour
        List<Transaction> stuck = txRepo.findAllByStatusAndUpdatedAtBefore(
                Transaction.TransactionStatus.PENDING_RELEASE, cutoff);

        for (Transaction tx : stuck) {
            if (tx.getType() != com.trustify.model.Transaction.TransactionType.SALE) continue;
            try {
                escrowService.capture(tx.getId(), tx.getBuyerId(), tx.getAuthorizedAmountCents());
                emailService.sendEscrowReleasedEmail(tx.getSellerEmail(), tx.getId());
            } catch (Exception e) {
                System.err.println("[Scheduler] autoCompletePendingReleaseSales failed for " +
                        tx.getId() + ": " + e.getMessage());
            }
        }
    }

    // 5️⃣ Auto-complete RENTAL_RETURNED transactions after 24 hours
    // Covers cases where (a) the seller never logs in after the item is returned,
    // or (b) old Postman-created transactions that were stuck with no action buttons.
    @Scheduled(cron = "0 0 * * * *") // every hour
    public void autoCompleteReturnedRentals() {
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        List<Transaction> stuck = txRepo.findAllByStatusAndUpdatedAtBefore(
                Transaction.TransactionStatus.RENTAL_RETURNED, cutoff);

        for (Transaction tx : stuck) {
            if (tx.isDamageReported()) continue; // leave damage cases for manual/admin review

            try {
                escrowService.finalizeRefund(tx.getId(), tx.getSellerId());

                emailService.sendEscrowReleasedEmail(tx.getSellerEmail(), tx.getId());
                emailService.sendEmail(tx.getBuyerEmail(),
                        "Rental Auto-Completed",
                        "Your rental has been automatically finalized. " +
                                "Any security deposit has been returned to your card.");
            } catch (Exception e) {
                // Log but don't stop processing other transactions
                System.err.println("[Scheduler] autoCompleteReturnedRentals failed for " +
                        tx.getId() + ": " + e.getMessage());
            }
        }
    }



    // 4️⃣ Daily rental ending reminders
    @Scheduled(cron = "0 0 9 * * *") // 9 AM daily
    public void sendDailyReminders() {

        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        List<Transaction> soonEnding = txRepo.findAllEndingWithinDays(tomorrow, today);

        for (Transaction tx : soonEnding) {
            emailService.sendEmail(tx.getBuyerEmail(),
                    "Rental Ending Soon",
                    "Your rental ends tomorrow. Please prepare to return the item.");
        }
    }

}
