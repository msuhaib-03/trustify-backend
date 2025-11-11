package com.trustify.service;

import com.stripe.model.PaymentIntent;
import com.trustify.dto.CaptureResponse;
import com.trustify.dto.CreateTransactionRequest;
import com.trustify.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;


public interface TransactionService {
    // create transaction and create PaymentIntent with capture_method=manual
    Transaction createAndAuthorize(CreateTransactionRequest req);

    // capture / release escrow
    CaptureResponse capture(String transactionId);

    // refund or cancel
    void refund(String transactionId, Long amountCents); // amountCents null => full

    // handle webhook events
    void handlePaymentIntentSucceeded(String paymentIntentId);

    void handlePaymentIntentCancelled(String paymentIntentId);

    Transaction getTransaction(String id);

    Page<Transaction> listForUser(String userId, Pageable pageable);
}
