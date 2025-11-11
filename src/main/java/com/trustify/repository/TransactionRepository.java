package com.trustify.repository;

import com.trustify.model.Transaction;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends MongoRepository<Transaction, String> {
    Optional<Transaction> findByStripePaymentIntentId(String paymentIntentId);
    List<Transaction> findByBuyerId(String buyerId);
    List<Transaction> findBySellerId(String sellerId);
}
