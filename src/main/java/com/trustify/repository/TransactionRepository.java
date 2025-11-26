package com.trustify.repository;

import com.trustify.model.Transaction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends MongoRepository<Transaction, String> {
    Optional<Transaction> findByStripePaymentIntentId(String paymentIntentId);
    List<Transaction> findByBuyerId(String buyerId);
    List<Transaction> findBySellerId(String sellerId);

    // Auto-cancel if seller never accepted within X hours
    List<Transaction> findAllByStatusAndCreatedAtBefore(String status, LocalDateTime time);

    // Auto-deliver shipped items
    List<Transaction> findAllByStatusAndShippedAtBefore(String status, LocalDateTime time);

    // Auto rental end
    //List<Transaction> findAllByRentalEndDateBeforeAndStatus(LocalDate time, Transaction.TransactionStatus status);
    List<Transaction> findAllByRentalEndBeforeAndStatus(LocalDate now, String rentActive);

    // For daily reminders
    @Query("{ 'rentalEnd': { $lte: ?0, $gte: ?1 } }")
    List<Transaction> findAllEndingWithinDays(LocalDate end, LocalDate start);


}
