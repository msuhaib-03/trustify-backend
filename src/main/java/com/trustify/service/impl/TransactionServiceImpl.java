package com.trustify.service.impl;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Dispute;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.Transfer;
import com.stripe.param.*;
import com.trustify.dto.*;
import com.trustify.model.PaymentEvent;
import com.trustify.model.Transaction;
import com.trustify.repository.PaymentEventRepository;
import com.trustify.repository.TransactionRepository;
import com.trustify.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final PaymentEventRepository eventRepository;

    @Value("${STRIPE_SECRET_KEY}")
    private String stripeSecret;


    // ---------- create & authorize ----------
    @Override
    public CreateTransactionResult createAndAuthorize(CreateTransactionRequest req) {

        Stripe.apiKey = stripeSecret;

        // basic anti-fraud checks; replace with your real logic
        if (isBlacklisted(req.getBuyerId()) || !isSellerVerified(req.getSellerId())) {
            Transaction tx = Transaction.builder()
                    .listingId(req.getListingId())
                    .buyerId(req.getBuyerId())
                    .sellerId(req.getSellerId())
                    .type(req.getType())
                    .amountCents(req.getAmountCents())
                    .depositCents(req.getDepositCents())
                    .currency(req.getCurrency() != null ? req.getCurrency() : "usd")
                    .status(Transaction.TransactionStatus.MANUAL_REVIEW)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            transactionRepository.save(tx);

            PaymentEvent ev = PaymentEvent.builder()
                    .transactionId(tx.getId())
                    .type("MANUAL_REVIEW")
                    .actor("SYSTEM")
                    .createdAt(Instant.now())
                    .build();
            eventRepository.save(ev);

            throw new RuntimeException("Transaction placed on manual review");
        }

        try {
            // create PaymentIntent with manual capture (escrow)
            PaymentIntentCreateParams params =
                    PaymentIntentCreateParams.builder()
                            .setAmount(req.getAmountCents())
                            .setCurrency(req.getCurrency() != null ? req.getCurrency() : "usd")
                            .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL)
                            .addPaymentMethodType("card")
                            .putMetadata("listingId", req.getListingId())
                            .putMetadata("buyerId", req.getBuyerId())
                            .putMetadata("sellerId", req.getSellerId())
                            .build();

            PaymentIntent pi = PaymentIntent.create(params);

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
                    .authorizedAmountCents(req.getAmountCents())
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            transactionRepository.save(tx);

            eventRepository.save(
                    PaymentEvent.builder()
                            .transactionId(tx.getId())
                            .stripeObjectId(pi.getId())
                            .type("PAYMENT_INTENT_CREATED")
                            .actor("SYSTEM")
                            .createdAt(Instant.now())
                            .build()
            );

            // ✅ Return wrapper (Transaction + clientSecret)
            return new CreateTransactionResult(
                    tx,
                    pi.getClientSecret()
            );
        } catch (StripeException e) {
            throw new RuntimeException("Stripe PI create failed: " + e.getMessage(), e);
        }
    }

    // ---------------- Request Release (Step 1) ----------------
    @Override
    public void requestRelease(String id, String userId, String note) {
        Transaction tx = getTransaction(id);
        if (!tx.getBuyerId().equals(userId)) throw new RuntimeException("Only buyer can request release");
        if (!tx.getStatus().equals(Transaction.TransactionStatus.AUTHORIZED)) {
            throw new RuntimeException("Transaction not in authorized state");
        }

        tx.setStatus(Transaction.TransactionStatus.PENDING_RELEASE);
        tx.setReleaseRequestedAt(Instant.now());
        tx.setReleaseRequestedBy(userId);
        tx.setReleaseRequestedNote(note);
        transactionRepository.save(tx);

        eventRepository.save(PaymentEvent.builder()
                .transactionId(tx.getId())
                .type("RELEASE_REQUESTED")
                .actor(userId)
                .createdAt(Instant.now())
                .build()
        );

        // TODO: enqueue email notification to seller
    }

    // ---------------- Confirm Release (Step 2) ----------------
    @Override
    public CaptureResponse capture(String transactionId, String actorUserId, Long amountToCaptureCents) {
        Stripe.apiKey = stripeSecret;
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        if (!tx.getStatus().equals(Transaction.TransactionStatus.PENDING_RELEASE) &&
                !tx.getStatus().equals(Transaction.TransactionStatus.AUTHORIZED) &&
                !tx.getStatus().equals(Transaction.TransactionStatus.PARTIALLY_RELEASED)) {
            throw new RuntimeException("Transaction not in releasable state");
        }

        if (tx.getStripePaymentIntentId() == null) {
            throw new RuntimeException("No payment intent present");
        }

        try {
            PaymentIntentRetrieveParams retrieveParams = PaymentIntentRetrieveParams.builder()
                    .addExpand("charges")
                    .build();

            PaymentIntent pi = PaymentIntent.retrieve(tx.getStripePaymentIntentId(), retrieveParams, null);

            PaymentIntent captured;
            if ("requires_capture".equals(pi.getStatus())) {
                PaymentIntentCaptureParams.Builder capBuilder = PaymentIntentCaptureParams.builder().addExpand("charges");
                if (amountToCaptureCents != null && amountToCaptureCents > 0 && amountToCaptureCents <= tx.getAuthorizedAmountCents()) {
                    capBuilder.setAmountToCapture(amountToCaptureCents);
                }
                captured = pi.capture(capBuilder.build());
            } else {
                if ("requires_confirmation".equals(pi.getStatus())) {
                    PaymentIntentConfirmParams confirmParams = PaymentIntentConfirmParams.builder()
                            .setPaymentMethod(pi.getPaymentMethod())
                            .build();
                    pi = pi.confirm(confirmParams);
                }
                captured = pi;
            }

            // Determine captured amount
            long capturedAmount = captured.getAmountReceived() != null ? captured.getAmountReceived() : captured.getAmount();
            String chargeId = captured.getLatestCharge() != null ? captured.getLatestCharge() :
                    (captured.getLatestChargeObject() != null ? captured.getLatestChargeObject().getId() : null);
            if (chargeId == null) throw new RuntimeException("Stripe did not return a charge ID after capture");

            // Save captured details
            tx.setStripeChargeId(chargeId);
            tx.setAmountCapturedCents(capturedAmount);
            tx.setStatus(capturedAmount < tx.getAuthorizedAmountCents() ? Transaction.TransactionStatus.PARTIALLY_RELEASED : Transaction.TransactionStatus.RELEASED);
            tx.setUpdatedAt(Instant.now());
            transactionRepository.save(tx);

            // Handle Transfer minus platform fees
            long platformFeeCents = tx.getPlatformFeeCents() != null ? tx.getPlatformFeeCents() : 0;
            long amountToTransfer = capturedAmount - platformFeeCents;

            if (tx.getSellerStripeAccountId() != null && amountToTransfer > 0) {
                Map<String, Object> transferParams = new HashMap<>();
                transferParams.put("amount", amountToTransfer);
                transferParams.put("currency", tx.getCurrency());
                transferParams.put("destination", tx.getSellerStripeAccountId());
                Transfer transfer = Transfer.create(transferParams);
                // Optional: tx.setStripeTransferId(transfer.getId());
                transactionRepository.save(tx);
            }

            eventRepository.save(PaymentEvent.builder()
                    .transactionId(tx.getId())
                    .stripeObjectId(captured.getId())
                    .type("CAPTURED")
                    .actor(actorUserId)
                    .createdAt(Instant.now())
                    .build()
            );

            return new CaptureResponse(tx.getId(), captured.getId(), chargeId, tx.getStatus().name());

        } catch (StripeException e) {
            throw new RuntimeException("Stripe capture failed: " + e.getMessage(), e);
        }
    }

    // ---------- refund ----------
    @Override
    public void refund(String transactionId, Long amountCents) {
        Stripe.apiKey = stripeSecret;
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        if (tx.getStripeChargeId() == null) throw new RuntimeException("No charge to refund");

        try {
            RefundCreateParamsBuilder builder = new RefundCreateParamsBuilder(tx.getStripeChargeId());
            if (amountCents != null) builder.setAmount(amountCents);

            Refund refund = Refund.create(builder.build());

            tx.setStatus(Transaction.TransactionStatus.REFUNDED);
            tx.setUpdatedAt(Instant.now());
            transactionRepository.save(tx);

            PaymentEvent ev = PaymentEvent.builder()
                    .transactionId(tx.getId())
                    .stripeObjectId(refund.getId())
                    .type("REFUND")
                    .actor("ADMIN")
                    .createdAt(Instant.now())
                    .build();
            eventRepository.save(ev);

        } catch (StripeException e) {
            throw new RuntimeException("Stripe refund failed: " + e.getMessage(), e);
        }
    }


    // ---------------- Dispute (Scaffold) ----------------
    @Override
    public void openDispute(String txId, String userId, DisputeRequest req) {
        Transaction tx = getTransaction(txId);
        if (!tx.getBuyerId().equals(userId)) throw new RuntimeException("Only buyer can open dispute");

        // TODO: save dispute to DB
        // Dispute dispute = new Dispute(...);
        // disputeRepository.save(dispute);

        tx.setStatus(Transaction.TransactionStatus.PENDING_DISPUTE);
        transactionRepository.save(tx);

        // TODO: notify admin
    }

    @Override
    public void adminResolveDispute(String transactionId, String adminUserId, ResolveDisputeRequest req) {
        Transaction tx = getTransaction(transactionId);

        // Optional: check admin role if needed
        // if (!isAdmin(adminUserId)) throw new RuntimeException("Unauthorized");

        if (!tx.getStatus().equals(Transaction.TransactionStatus.PENDING_DISPUTE)) {
            throw new RuntimeException("Transaction not in dispute state");
        }

        Long deductionCents = req.getDeductionCents() != null ? req.getDeductionCents() : 0L;
        Long platformFeeCents = tx.getPlatformFeeCents() != null ? tx.getPlatformFeeCents() : 0L;

        try {
            // 1️⃣ Refund buyer if requested
            if (req.isRefundBuyer() && tx.getStripeChargeId() != null) {
                long refundAmount = tx.getAmountCapturedCents() - deductionCents;
                RefundCreateParams params = RefundCreateParams.builder()
                        .setCharge(tx.getStripeChargeId())
                        .setAmount(refundAmount)
                        .build();
                Refund refund = Refund.create(params);
                tx.setStatus(Transaction.TransactionStatus.REFUNDED);
            }

            // 2️⃣ Transfer remaining to seller (minus deduction + platform fees)
            if (tx.getSellerStripeAccountId() != null && tx.getAmountCapturedCents() > 0) {
                long amountToTransfer = tx.getAmountCapturedCents() - deductionCents - platformFeeCents;
                if (amountToTransfer > 0) {
                    Map<String, Object> transferParams = Map.of(
                            "amount", amountToTransfer,
                            "currency", tx.getCurrency(),
                            "destination", tx.getSellerStripeAccountId()
                    );
                    com.stripe.model.Transfer transfer = com.stripe.model.Transfer.create(transferParams);
                    // Optionally store transfer ID
                }
            }

            // 3️⃣ Update transaction and save
            tx.setUpdatedAt(Instant.now());
            transactionRepository.save(tx);

            // 4️⃣ Log event
            eventRepository.save(PaymentEvent.builder()
                    .transactionId(tx.getId())
                    .type("DISPUTE_RESOLVED")
                    .actor(adminUserId)
                    .createdAt(Instant.now())
                    .build()
            );

        } catch (StripeException e) {
            throw new RuntimeException("Stripe operation failed: " + e.getMessage(), e);
        }
    }


    // ---------- webhook handlers ----------
    @Override
    public void handlePaymentIntentSucceeded(String paymentIntentId) {
        Optional<Transaction> opt = transactionRepository.findByStripePaymentIntentId(paymentIntentId);
        opt.ifPresent(tx -> {
            tx.setStatus(Transaction.TransactionStatus.AUTHORIZED);
            tx.setUpdatedAt(Instant.now());
            transactionRepository.save(tx);

            PaymentEvent ev = PaymentEvent.builder()
                    .transactionId(tx.getId())
                    .stripeObjectId(paymentIntentId)
                    .type("PI_SUCCEEDED")
                    .actor("SYSTEM")
                    .createdAt(Instant.now())
                    .build();
            eventRepository.save(ev);
        });
    }

    @Override
    public void handlePaymentIntentCancelled(String paymentIntentId) {
        Optional<Transaction> opt = transactionRepository.findByStripePaymentIntentId(paymentIntentId);
        opt.ifPresent(tx -> {
            tx.setStatus(Transaction.TransactionStatus.CANCELLED);
            tx.setUpdatedAt(Instant.now());
            transactionRepository.save(tx);

            PaymentEvent ev = PaymentEvent.builder()
                    .transactionId(tx.getId())
                    .stripeObjectId(paymentIntentId)
                    .type("PI_CANCELLED")
                    .actor("SYSTEM")
                    .createdAt(Instant.now())
                    .build();
            eventRepository.save(ev);
        });
    }

    // ---------- helper implementations ----------
    @Override
    public Transaction getTransaction(String id) {
        return transactionRepository.findById(id).orElseThrow(() -> new RuntimeException("Transaction not found"));
    }

    @Override
    public Page<Transaction> listForUser(String userId, Pageable pageable) {
        // list both buyer and seller transactions
        return transactionRepository.findAll(pageable);
    }

    // ---- placeholder anti-fraud / verification methods ----
    private boolean isBlacklisted(String buyerId) {
        // implement real blacklist check - DB or ML
        return false;
    }

    private boolean isSellerVerified(String sellerId) {
        // implement seller verification check (CNIC/email/phone/KYC)
        return true;
    }

    // helper builder for refunds (works with stripe-java 21.x)
    private static class RefundCreateParamsBuilder {
        private final RefundCreateParams.Builder builder;

        RefundCreateParamsBuilder(String chargeId) {
            builder = RefundCreateParams.builder().setCharge(chargeId);
        }

        RefundCreateParamsBuilder setAmount(long amount) {
            builder.setAmount(amount);
            return this;
        }

        RefundCreateParams build() {
            return builder.build();
        }
    }


}

// first hit this endpoint to create & authorize payment intent: with bearer token
// http://localhost:8080/api/transactions

// then hit this endpoint to start PaymentIntent capture using Stripe secret key as bearer token
// https://api.stripe.com/v1/payment_intents/pi_3SSEbDGaACRSC93z1eFcN40w
// then again but with /confirm
//https://api.stripe.com/v1/payment_intents/pi_3SSEbDGaACRSC93z1eFcN40w/confirm

// then this endpoint:
// http://localhost:8080/api/transactions/69130c809a462c262a5a2b05/release  --> to release escrow

// and finally this endpoint to refund:
// http://localhost:8080/api/transactions/69130c809a462c262a5a2b05/refund