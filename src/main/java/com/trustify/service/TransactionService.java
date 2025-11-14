package com.trustify.service;

import com.stripe.model.PaymentIntent;
import com.trustify.dto.*;
import com.trustify.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;


public interface TransactionService {
    // create transaction and create PaymentIntent with capture_method=manual
    // ---------- Create & authorize ----------
    CreateTransactionResult createAndAuthorize(CreateTransactionRequest req);

    // capture / release escrow
    CaptureResponse capture(String transactionId,  String actorUserId, Long amountToCaptureCents);

    // refund or cancel
    void refund(String transactionId, Long amountCents); // amountCents null => full

    // ---------- Manual release flow ----------
    void requestRelease(String transactionId, String userId, String note);

    // ---------- Disputes ----------
    void openDispute(String transactionId, String userId, DisputeRequest disputeRequest);
    void adminResolveDispute(String transactionId, String adminUserId, ResolveDisputeRequest req);

    // handle webhook events
    void handlePaymentIntentSucceeded(String paymentIntentId);
    void handlePaymentIntentCancelled(String paymentIntentId);

    // ---------- Transaction queries ----------
    Transaction getTransaction(String id);
    Page<Transaction> listForUser(String userId, Pageable pageable);
}
