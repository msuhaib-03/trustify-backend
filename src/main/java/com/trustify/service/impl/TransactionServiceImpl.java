package com.trustify.service.impl;

import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.Transfer;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.TransferCreateParams;
import com.trustify.model.Transaction;
import com.trustify.repository.TransactionRepository;
import com.trustify.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {
    private final TransactionRepository txRepo;

    @Override
    public Transaction createTransactionAndPaymentIntent(Transaction t) throws Exception {

        // Example inside TransactionServiceImpl.createTransactionAndPaymentIntent(...)
        boolean suspicious = false;
        if (t.getAmount() > 200_000L) { // e.g., > 2000.00 in cents
            suspicious = true;
        }

        // velocity check (example pseudo)
        long recentCount = txRepo.countByBuyerEmailAndCreatedAtAfter(t.getBuyerEmail(), Instant.now().minus(10, ChronoUnit.MINUTES));
        if (recentCount > 5) suspicious = true;

        if (suspicious) {
            t.setStatus(Transaction.Status.HELD);
            txRepo.save(t);
            // optionally return with a flag telling frontend the transaction is under review
            return t;
        }
        // Save a PENDING transaction first
        t.setStatus(Transaction.Status.PENDING);
        t.setCreatedAt(Instant.now());
        txRepo.save(t);

        // create Stripe PaymentIntent
        PaymentIntentCreateParams.Builder builder = PaymentIntentCreateParams.builder()
                .setAmount(t.getAmount())
                .setCurrency(t.getCurrency())
                .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.AUTOMATIC) // we will hold in Stripe; "manual" optional
                .addPaymentMethodType("card");

        // if you want to require statement descriptor or metadata:
        builder.putMetadata("transactionId", t.getId());
        builder.putMetadata("productId", t.getProductId());
        PaymentIntentCreateParams params = builder.build();

        PaymentIntent intent = PaymentIntent.create(params);

        t.setStripePaymentIntentId(intent.getId());
        t.setStatus(Transaction.Status.PENDING);
        t.setUpdatedAt(Instant.now());
        txRepo.save(t);
        return t;

    }

    @Override
    public Transaction handlePaymentIntentSucceeded(String paymentIntentId, String chargeId) throws Exception {
        var maybe = txRepo.findByStripePaymentIntentId(paymentIntentId);
        if (maybe.isEmpty()) throw new RuntimeException("Transaction not found for PI: " + paymentIntentId);
        Transaction t = maybe.get();
        t.setStripeChargeId(chargeId);
        // funds are with Stripe: mark PAID or HELD depending on your flow
        t.setStatus(Transaction.Status.PAID); // or HELD if you want admin release
        t.setUpdatedAt(Instant.now());
        return txRepo.save(t);
    }

    @Override
    public Transaction releaseEscrow(String transactionId) throws Exception {
        Transaction t = txRepo.findById(transactionId).orElseThrow();
        if (t.getStatus() != Transaction.Status.PAID && t.getStatus() != Transaction.Status.HELD) {
            throw new RuntimeException("Transaction not ready for release");
        }

        // If you use Stripe Connect: transfer to connected account (sellerStripeAccountId)
        if (t.getSellerStripeAccountId() != null && !t.getSellerStripeAccountId().isBlank()) {
            TransferCreateParams transferParams = TransferCreateParams.builder()
                    .setAmount(t.getAmount())
                    .setCurrency(t.getCurrency())
                    .setDestination(t.getSellerStripeAccountId())
                    .putMetadata("transactionId", t.getId())
                    .build();

            Transfer transfer = Transfer.create(transferParams);
            t.setStatus(Transaction.Status.RELEASED);
            t.setUpdatedAt(Instant.now());
            return txRepo.save(t);
        } else {
            // If not using Connect, you may need server-side payout or manual payout
            // For now mark RELEASED and let finance handle payout.
            t.setStatus(Transaction.Status.RELEASED);
            t.setUpdatedAt(Instant.now());
            return txRepo.save(t);
        }
    }

    @Override
    public Transaction refundTransaction(String transactionId, Long amount) throws Exception {
        Transaction t = txRepo.findById(transactionId).orElseThrow();
        if (t.getStripeChargeId() == null) throw new RuntimeException("No charge id to refund");

        RefundCreateParams.Builder builder = RefundCreateParams.builder()
                .setCharge(t.getStripeChargeId());
        if (amount != null) builder.setAmount(amount);
        Refund refund = Refund.create(builder.build());

        t.setStatus(Transaction.Status.REFUNDED);
        t.setUpdatedAt(Instant.now());
        return txRepo.save(t);
    }

    @Override
    public Transaction getById(String id) {
        return txRepo.findById(id).orElse(null);
    }
}
