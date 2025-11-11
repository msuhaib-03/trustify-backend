package com.trustify.service.impl;

import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.Transfer;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.TransferCreateParams;
import com.trustify.dto.CreateTransactionRequest;
import com.trustify.model.PaymentEvent;
import com.trustify.model.Transaction;
import com.trustify.repository.PaymentEventRepository;
import com.trustify.repository.TransactionRepository;
import com.trustify.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final PaymentEventRepository eventRepository;

    @Value("${STRIPE_SECRET_KEY}")
    private String stripeSecret;

    @Override
    public Transaction createAndAuthorize(CreateTransactionRequest req) {
        Stripe.apiKey = stripeSecret;
        if (isBlacklisted(req.getBuyerId()) || !isSellerVerified(req.getSellerId())) {
            Transaction tx = Transaction.builder()...
      .status(Transaction.TransactionStatus.MANUAL_REVIEW)
                    .build();
            transactionRepository.save(tx);
            eventRepository.save(new PaymentEvent(...));
            throw new RuntimeException("Transaction placed on manual review");
        }


        try {
            PaymentIntentCreateParams.Builder params = PaymentIntentCreateParams.builder()
                    .setAmount(req.getAmountCents())
                    .setCurrency(req.getCurrency() == null ? "usd" : req.getCurrency())
                    .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL)
                    .putMetadata("listingId", req.getListingId())
                    .putMetadata("buyerId", req.getBuyerId())
                    .putMetadata("sellerId", req.getSellerId());

            PaymentIntent pi = PaymentIntent.create(params.build());

            Transaction tx = Transaction.builder()
                    .listingId(req.getListingId())
                    .buyerId(req.getBuyerId())
                    .sellerId(req.getSellerId())
                    .type(req.getType())
                    .amountCents(req.getAmountCents())
                    .depositCents(req.getDepositCents())
                    .currency(pi.getCurrency())
                    .status(Transaction.TransactionStatus.AUTHORIZED)
                    .stripePaymentIntentId(pi.getId())
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            transactionRepository.save(tx);

            PaymentEvent ev = new PaymentEvent();
            ev.setTransactionId(tx.getId());
            ev.setType("PAYMENT_INTENT_CREATED");
            ev.setStripeObjectId(pi.getId());
            ev.setActor("SYSTEM");
            ev.setCreatedAt(Instant.now());
            eventRepository.save(ev);

            return tx;
        } catch (Exception e) {
            throw new RuntimeException("Stripe create PI failed: " + e.getMessage(), e);
        }
    }

    @Override
    public PaymentIntent capture(String transactionId) {
        Stripe.apiKey = stripeSecret;
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        if (tx.getStripePaymentIntentId() == null) throw new RuntimeException("No payment intent present");

        try {
            PaymentIntent pi = PaymentIntent.retrieve(tx.getStripePaymentIntentId());
            PaymentIntent captured = pi.capture(); // immediate capture; you can pass capture params if needed

            tx.setStripeChargeId(captured.getCharges().getData().isEmpty() ? null : captured.getCharges().getData().get(0).getId());
            tx.setStatus(Transaction.TransactionStatus.RELEASED);
            tx.setUpdatedAt(Instant.now());
            transactionRepository.save(tx);

            PaymentEvent ev = new PaymentEvent();
            ev.setTransactionId(tx.getId());
            ev.setType("CAPTURED");
            ev.setStripeObjectId(captured.getId());
            ev.setActor("ADMIN");
            ev.setCreatedAt(Instant.now());
            eventRepository.save(ev);

            return captured;
        } catch (Exception e) {
            throw new RuntimeException("Stripe capture failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void refund(String transactionId, Long amountCents) {
        Stripe.apiKey = stripeSecret;
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        if (tx.getStripeChargeId() == null) throw new RuntimeException("No charge to refund");

        try {
            RefundCreateParams.Builder rb = RefundCreateParams.builder()
                    .setCharge(tx.getStripeChargeId());

            if (amountCents != null) rb.setAmount(amountCents);

            Refund refund = Refund.create(rb.build());

            tx.setStatus(Transaction.TransactionStatus.REFUNDED);
            tx.setUpdatedAt(Instant.now());
            transactionRepository.save(tx);

            PaymentEvent ev = new PaymentEvent();
            ev.setTransactionId(tx.getId());
            ev.setType("REFUND");
            ev.setStripeObjectId(refund.getId());
            ev.setActor("ADMIN");
            ev.setCreatedAt(Instant.now());
            eventRepository.save(ev);

        } catch (Exception e) {
            throw new RuntimeException("Stripe refund failed: " + e.getMessage(), e);
        }
    }
    @Override
    public void handlePaymentIntentSucceeded(String paymentIntentId) {
        Optional<Transaction> opt = transactionRepository.findByStripePaymentIntentId(paymentIntentId);
        if (opt.isPresent()) {
            Transaction tx = opt.get();
            tx.setStatus(Transaction.TransactionStatus.AUTHORIZED); // authorized/captureable
            tx.setUpdatedAt(Instant.now());
            transactionRepository.save(tx);

            PaymentEvent ev = new PaymentEvent();
            ev.setTransactionId(tx.getId());
            ev.setType("PI_SUCCEEDED");
            ev.setStripeObjectId(paymentIntentId);
            ev.setActor("SYSTEM");
            ev.setCreatedAt(Instant.now());
            eventRepository.save(ev);
        } else {
            // optionally log - unmapped PI
        }
    }

    @Override
    public void handlePaymentIntentCancelled(String paymentIntentId) {
        Optional<Transaction> opt = transactionRepository.findByStripePaymentIntentId(paymentIntentId);
        opt.ifPresent(tx -> {
            tx.setStatus(Transaction.TransactionStatus.CANCELLED);
            tx.setUpdatedAt(Instant.now());
            transactionRepository.save(tx);

            PaymentEvent ev = new PaymentEvent();
            ev.setTransactionId(tx.getId());
            ev.setType("PI_CANCELLED");
            ev.setStripeObjectId(paymentIntentId);
            ev.setActor("SYSTEM");
            ev.setCreatedAt(Instant.now());
            eventRepository.save(ev);
        });
    }


}
