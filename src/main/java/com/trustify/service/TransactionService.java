package com.trustify.service;

import com.trustify.model.Transaction;
import org.springframework.stereotype.Service;


public interface TransactionService {
    Transaction createTransactionAndPaymentIntent(Transaction tx) throws Exception;
    Transaction handlePaymentIntentSucceeded(String paymentIntentId, String chargeId) throws Exception;
    Transaction releaseEscrow(String transactionId) throws Exception;
    Transaction refundTransaction(String transactionId, Long amount) throws Exception;
    Transaction getById(String id);
}
