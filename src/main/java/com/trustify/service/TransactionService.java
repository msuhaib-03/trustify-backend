package com.trustify.service;

import com.trustify.dto.*;
import com.trustify.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


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

    // --------- Rental specific ---------
    void startRental(String transactionId, String userEmail);
    void completeRental(String transactionId, String userEmail);
    /**
     * Seller finalizes a clean return: captures only the rental fee; Stripe
     * automatically un-holds the deposit and returns it to the buyer.
     */
    void finalizeRefund(String id, String userId);
    /**
     * Seller reports damage: captures (rentalFee + damageAmount); Stripe
     * automatically returns (deposit − damageAmount) to the buyer.
     */
    void deductDamage(String id, Long damageAmountCents, String userId);

    // --------- Condition acceptance --------
    void acceptedConditions(String transactionId, String buyerId);
}
