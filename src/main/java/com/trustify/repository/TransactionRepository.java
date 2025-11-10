package com.trustify.repository;

import com.trustify.model.Transaction;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends MongoRepository<Transaction, String> {
    Optional<Transaction> findByStripePaymentIntentId(String piId);
    List<Transaction> findByBuyerEmail(String buyerEmail);
    List<Transaction> findBySellerEmail(String sellerEmail);

    long countByBuyerEmailAndCreatedAtAfter(String buyerEmail, Instant minus);
}
