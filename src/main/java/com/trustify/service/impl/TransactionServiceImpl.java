package com.trustify.service.impl;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.Transfer;
import com.stripe.param.*;
import com.trustify.dto.*;
import com.trustify.model.*;
import com.trustify.repository.*;
import com.trustify.service.EmailService;
import com.trustify.service.FraudService;
import com.trustify.service.TimelineLogService;
import com.trustify.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final PaymentEventRepository eventRepository;
    private final DisputeRepository disputeRepository;
    private final TimelineLogService timelineLogService;
    private final FraudService fraudService;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final ListingRepository listingRepository;

    @Value("${STRIPE_SECRET_KEY}")
    private String stripeSecret;


    // ---------- create & authorize ----------
    @Override
    public CreateTransactionResult createAndAuthorize(CreateTransactionRequest req) {

        Stripe.apiKey = stripeSecret;

        // buyerId / sellerId can be a MongoDB ObjectId OR an email (see CreateTransactionRequest comment)
        User buyer = userRepository.findById(req.getBuyerId())
                .or(() -> userRepository.findByEmail(req.getBuyerId()))
                .orElseThrow(() -> new RuntimeException("Buyer not found: " + req.getBuyerId()));
        User seller = userRepository.findById(req.getSellerId())
                .or(() -> userRepository.findByEmail(req.getSellerId()))
                .orElseThrow(() -> new RuntimeException("Seller not found: " + req.getSellerId()));

        //if (isBlacklisted(req.getBuyerId()) || !isSellerVerified(req.getSellerId())) --> old logic before current fraud + rating system
        if(buyer.getFraudScore() > 70 || seller.getFraudScore() > 70)
        {
            Transaction tx = Transaction.builder()
                    .listingId(req.getListingId())
                    .buyerId(req.getBuyerId())
                    .sellerId(req.getSellerId())
                    .buyerEmail(buyer.getEmail())
                    .sellerEmail(seller.getEmail())
                    .type(req.getType())
                    .amountCents(req.getAmountCents())
                    .depositCents(req.getDepositCents())
                    .currency(req.getCurrency() != null ? req.getCurrency() : "usd")
                    .status(Transaction.TransactionStatus.MANUAL_REVIEW)
                    .rentalDurationUnits(req.getRentalDurationUnits())
                    .rentalStartDate(req.getType() == Transaction.TransactionType.RENT ? LocalDate.now() : null)
                    .rentalEnd(computeRentalEnd(req))
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

            // Timeline Log for manual review
            timelineLogService.log(
                    tx.getId(),
                    req.getBuyerId(),
                    null,
                    "Transaction moved to manual review due to risk",
                    TimelineLog.ActionType.ADMIN_OVERRIDE,
                    TimelineLog.ActorType.SYSTEM
            );

            throw new RuntimeException("Transaction placed on manual review");
        }

        // Stripe minimum is $0.50 = 50 cents. Validate before calling Stripe.
        long depositCentsVal = req.getDepositCents() != null ? req.getDepositCents() : 0L;
        long totalCents = req.getAmountCents() + depositCentsVal;
        if (totalCents < 50) {
            throw new RuntimeException(
                    "Amount too low for payment processing. Minimum is $0.50 USD (~PKR 141). " +
                            "Please set a higher price for your listing."
            );
        }

        try {
            // PI = rentalFee + deposit (0 for sales).
            // By authorizing the total we can later:
            //   capture(rentalFee)              → Stripe auto-releases deposit back to buyer (no damage)
            //   capture(rentalFee + damageAmt)  → Stripe auto-releases (deposit - damage) to buyer
            // This removes the need to issue a separate Stripe refund on an already-captured charge.
            long depositCents = req.getDepositCents() != null ? req.getDepositCents() : 0L;
            long totalPiAmount = req.getAmountCents() + depositCents;

            // create PaymentIntent with manual capture (escrow)
            PaymentIntentCreateParams params =
                    PaymentIntentCreateParams.builder()
                            .setAmount(totalPiAmount)
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
                    .buyerEmail(buyer.getEmail())
                    .sellerEmail(seller.getEmail())
                    .type(req.getType())
                    .amountCents(req.getAmountCents())     // rental fee × duration (what seller earns)
                    .depositCents(req.getDepositCents())    // deposit only (returned to buyer)
                    .currency(pi.getCurrency())
                    .status(Transaction.TransactionStatus.AUTHORIZED)
                    .stripePaymentIntentId(pi.getId())
                    .authorizedAmountCents(totalPiAmount)   // full PI amount (fee + deposit)
                    .rentalDurationUnits(req.getRentalDurationUnits())
                    .rentalStartDate(req.getType() == Transaction.TransactionType.RENT ? LocalDate.now() : null)
                    .rentalEnd(computeRentalEnd(req))
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            transactionRepository.save(tx);

            // For rentals, mark the listing as RENTED so it's hidden from browse
            // until the item is returned. For sales it stays ACTIVE until the buyer
            // confirms receipt (requestRelease), at which point it becomes SOLD.
            if (req.getType() == Transaction.TransactionType.RENT) {
                updateListingStatus(req.getListingId(), Listing.ListingStatus.RENTED);
            }

            eventRepository.save(
                    PaymentEvent.builder()
                            .transactionId(tx.getId())
                            .stripeObjectId(pi.getId())
                            .type("PAYMENT_INTENT_CREATED")
                            .actor("SYSTEM")
                            .createdAt(Instant.now())
                            .build()
            );


            // TIMELINELOG SERVICE: log transaction creation and payment initiation
            timelineLogService.log(
                    tx.getId(),
                    null,
                    "SYSTEM",
                    "Stripe PaymentIntent created",
                    TimelineLog.ActionType.PAYMENT_INITIATED,
                    TimelineLog.ActorType.SYSTEM
            );

            timelineLogService.log(
                    tx.getId(),
                    req.getBuyerId(),
                    null,
                    "Transaction created and payment initiated",
                    TimelineLog.ActionType.TRANSACTION_CREATED,
                    TimelineLog.ActorType.USER
            );

            // ✅ Notify both parties that the transaction has been created
            boolean isRental = req.getType() == Transaction.TransactionType.RENT;
            if (isRental) {
                emailService.sendEmail(
                        buyer.getEmail(),
                        "Rental Confirmed — Transaction " + tx.getId(),
                        "<h3>Your rental request has been confirmed!</h3>" +
                                "<p>Transaction ID: <b>" + tx.getId() + "</b></p>" +
                                "<p>Listing ID: " + tx.getListingId() + "</p>" +
                                "<p>Your payment is securely held in escrow and will be released once the rental is complete.</p>"
                );
                emailService.sendEmail(
                        seller.getEmail(),
                        "New Rental Request — Transaction " + tx.getId(),
                        "<h3>You have a new rental request!</h3>" +
                                "<p>Transaction ID: <b>" + tx.getId() + "</b></p>" +
                                "<p>Listing ID: " + tx.getListingId() + "</p>" +
                                "<p>Please prepare the item for the renter. Payment is held in escrow and will be released once the rental concludes.</p>"
                );
            } else {
                emailService.sendEmail(
                        buyer.getEmail(),
                        "Order Placed — Transaction " + tx.getId(),
                        "<h3>Your order has been placed!</h3>" +
                                "<p>Transaction ID: <b>" + tx.getId() + "</b></p>" +
                                "<p>Listing ID: " + tx.getListingId() + "</p>" +
                                "<p>Your payment is securely held in escrow. It will be released to the seller once you confirm delivery.</p>"
                );
                emailService.sendEmail(
                        seller.getEmail(),
                        "New Sale Order — Transaction " + tx.getId(),
                        "<h3>You have a new sale order!</h3>" +
                                "<p>Transaction ID: <b>" + tx.getId() + "</b></p>" +
                                "<p>Listing ID: " + tx.getListingId() + "</p>" +
                                "<p>Please ship the item promptly. Payment is held in escrow and will be released once the buyer confirms delivery.</p>"
                );
            }


            // ✅ Return wrapper (Transaction + clientSecret)
            return new CreateTransactionResult(
                    tx,
                    pi.getClientSecret()
            );
        } catch (StripeException e) {
            throw new RuntimeException("Stripe PI create failed: " + e.getMessage(), e);
        }
    }

    // ─── ID-tolerant role helpers ─────────────────────────────────────────────
    /**
     * Returns true if {@code userId} (email or ObjectId) resolves to the transaction's buyer.
     * Handles the case where buyerId may have been stored as email or as ObjectId.
     */
    private boolean isTheBuyer(Transaction tx, String userId) {
        if (userId == null) return false;
        if (userId.equals(tx.getBuyerId())) return true;
        if (userId.equals(tx.getBuyerEmail())) return true;
        try {
            User u = userRepository.findByEmail(userId)
                    .or(() -> userRepository.findById(userId))
                    .orElse(null);
            if (u != null) {
                if (u.getId() != null && u.getId().equals(tx.getBuyerId())) return true;
                if (u.getEmail() != null && u.getEmail().equals(tx.getBuyerId())) return true;
                if (u.getEmail() != null && u.getEmail().equals(tx.getBuyerEmail())) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Returns true if {@code userId} (email or ObjectId) resolves to the transaction's seller.
     */
    private boolean isTheSeller(Transaction tx, String userId) {
        if (userId == null) return false;
        if (userId.equals(tx.getSellerId())) return true;
        if (userId.equals(tx.getSellerEmail())) return true;
        try {
            User u = userRepository.findByEmail(userId)
                    .or(() -> userRepository.findById(userId))
                    .orElse(null);
            if (u != null) {
                if (u.getId() != null && u.getId().equals(tx.getSellerId())) return true;
                if (u.getEmail() != null && u.getEmail().equals(tx.getSellerId())) return true;
                if (u.getEmail() != null && u.getEmail().equals(tx.getSellerEmail())) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    // ---------------- Request Release ----------------
    @Override
    public void requestRelease(String id, String userId, String note) {
        Transaction tx = getTransaction(id);
        if (!isTheBuyer(tx, userId)) throw new RuntimeException("Only buyer can request release");
        if (!tx.getStatus().equals(Transaction.TransactionStatus.AUTHORIZED)) {
            throw new RuntimeException("Transaction not in authorized state");
        }

        // Record who / when / why
        tx.setReleaseRequestedAt(Instant.now());
        tx.setReleaseRequestedBy(userId);
        tx.setReleaseRequestedNote(note);
        tx.setStatus(Transaction.TransactionStatus.PENDING_RELEASE);
        transactionRepository.save(tx);

        eventRepository.save(PaymentEvent.builder()
                .transactionId(tx.getId())
                .type("RELEASE_REQUESTED")
                .actor(userId)
                .createdAt(Instant.now())
                .build()
        );

        // ── SALE: buyer confirming receipt = auto-release to seller ─────────
        // There is nothing the seller needs to verify at this point — they
        // already shipped/handed over the item.  Capture immediately so the
        // seller receives funds without having to take any action.
        if (tx.getType() == Transaction.TransactionType.SALE) {
            timelineLogService.log(
                    tx.getId(),
                    userId,
                    null,
                    "Buyer confirmed receipt — payment auto-released to seller",
                    TimelineLog.ActionType.PAYMENT_RELEASED,
                    TimelineLog.ActorType.USER
            );
            // capture() re-reads the tx; PENDING_RELEASE is in its allowed-status list.
            this.capture(id, userId, tx.getAuthorizedAmountCents());
            // Item sold — remove it from browse permanently
            updateListingStatus(tx.getListingId(), Listing.ListingStatus.SOLD);
            return;
        }

        // ── RENT: stay at PENDING_RELEASE if needed (rare — rentals normally
        // use completeRental, but kept for completeness / admin override paths)
        timelineLogService.log(
                tx.getId(),
                userId,
                null,
                "Buyer requested payment release",
                TimelineLog.ActionType.PAYMENT_HELD,
                TimelineLog.ActorType.USER
        );
    }

    // ---------------- Confirm Release (Step 2) ----------------
    @Override
    public CaptureResponse capture(String transactionId, String actorUserId, Long amountToCaptureCents) {
        Stripe.apiKey = stripeSecret;
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        // dispute + admin override
        if (tx.getStatus() == Transaction.TransactionStatus.PENDING_DISPUTE) {
            throw new RuntimeException("Transaction locked due to dispute");
        }

        if (!tx.getStatus().equals(Transaction.TransactionStatus.PENDING_RELEASE) &&
                !tx.getStatus().equals(Transaction.TransactionStatus.AUTHORIZED) &&
                !tx.getStatus().equals(Transaction.TransactionStatus.PARTIALLY_RELEASED) &&
                !tx.getStatus().equals(Transaction.TransactionStatus.RENTAL_RETURNED) &&
                !tx.getStatus().equals(Transaction.TransactionStatus.DAMAGE_RESOLVED)) {
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
                Long authorizedAmount =
                        tx.getAuthorizedAmountCents() != null ? tx.getAuthorizedAmountCents() : 0L;

                if (amountToCaptureCents != null &&
                        amountToCaptureCents > 0 &&
                        amountToCaptureCents <= authorizedAmount) {

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

            // Transfer to seller's connected account (optional — skipped for test accounts)
            if (tx.getSellerStripeAccountId() != null && amountToTransfer > 0) {
                try {
                    Map<String, Object> transferParams = new HashMap<>();
                    transferParams.put("amount", amountToTransfer);
                    transferParams.put("currency", tx.getCurrency());
                    transferParams.put("destination", tx.getSellerStripeAccountId());
                    Transfer.create(transferParams);
                } catch (Exception transferEx) {
                    // Non-fatal: Stripe transfer failed but the capture itself succeeded.
                    System.err.println("[Capture] Transfer to seller failed (non-critical): " + transferEx.getMessage());
                }
            }

            // Post-capture side-effects — wrapped individually so a failure here
            // NEVER blocks the caller (e.g. finalizeRefund) from updating the status.
            try {
                fraudService.rewardUser(tx.getSellerId());
                fraudService.rewardUser(tx.getBuyerId());
            } catch (Exception e) {
                System.err.println("[Capture] Fraud reward failed (non-critical): " + e.getMessage());
            }

            try {
                eventRepository.save(PaymentEvent.builder()
                        .transactionId(tx.getId())
                        .stripeObjectId(captured.getId())
                        .type("CAPTURED")
                        .actor(actorUserId)
                        .createdAt(Instant.now())
                        .build()
                );
                timelineLogService.log(
                        tx.getId(),
                        actorUserId,
                        null,
                        "Payment captured and released to seller",
                        TimelineLog.ActionType.PAYMENT_RELEASED,
                        TimelineLog.ActorType.USER
                );
            } catch (Exception e) {
                System.err.println("[Capture] Event/timeline log failed (non-critical): " + e.getMessage());
            }

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

            // TIMELINE LOG FOR REFUND
            timelineLogService.log(
                    tx.getId(),
                    "ADMIN",
                    "ADMIN",
                    "Refund issued to buyer",
                    TimelineLog.ActionType.REFUND_ISSUED,
                    TimelineLog.ActorType.ADMIN
            );

        } catch (StripeException e) {
            throw new RuntimeException("Stripe refund failed: " + e.getMessage(), e);
        }
    }

    // ====================================================
    // ---------------- Dispute (Scaffold) ----------------
    // =====================================================
    @Override
    public void openDispute(String txId, String userId, DisputeRequest req) {
        Transaction tx = getTransaction(txId);
        if (!isTheBuyer(tx, userId)) throw new RuntimeException("Only buyer can open dispute");
        // ADMIN OVERRIDE + DISPUTE
        if (disputeRepository.findByTransactionId(txId).isPresent()) {
            throw new RuntimeException("Dispute already exists");
        }

        // ✅ Save dispute to DB
        Dispute dispute = Dispute.builder()
                .transactionId(tx.getId())
                .openedBy(userId)
                .reason(req.getReason())
                .evidence(req.getEvidence())
                .status("OPEN")
                .createdAt(Instant.now())
                .build();
        disputeRepository.save(dispute);

        // Fraud Score + Rating
        fraudService.penalizeUser(tx.getSellerId());

        // ✅ Update transaction status
        tx.setStatus(Transaction.TransactionStatus.PENDING_DISPUTE);
        transactionRepository.save(tx);

        // notify admin
        // ✅ Optional: notify admin (can be email or dashboard flag)
        eventRepository.save(PaymentEvent.builder()
                .transactionId(tx.getId())
                .type("DISPUTE_OPENED")
                .actor(userId)
                .createdAt(Instant.now())
                .build()
        );

        // TIMELINE LOG FOR DISPUTE OPENED
        timelineLogService.log(
                tx.getId(),
                userId,
                null,
                "Buyer opened a dispute",
                TimelineLog.ActionType.DISPUTE_RAISED,
                TimelineLog.ActorType.USER
        );
    }

    @Override
    public void adminResolveDispute(String transactionId, String adminUserId, ResolveDisputeRequest req) {
        Transaction tx = getTransaction(transactionId);

        if (!tx.getStatus().equals(Transaction.TransactionStatus.PENDING_DISPUTE)) {
            throw new RuntimeException("Transaction not in dispute state");
        }

        Dispute dispute = disputeRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new RuntimeException("Dispute not found for transaction"));

        Long deductionCents = req.getDeductionCents() != null ? req.getDeductionCents() : 0L;
        Long platformFeeCents = tx.getPlatformFeeCents() != null ? tx.getPlatformFeeCents() : 0L;

        try {
            switch(req.getDecision()){

                // 🔴 CASE 1: FULL REFUND TO BUYER
                case "REFUND_BUYER":

                    if (tx.getStripeChargeId() != null) {
                        RefundCreateParams params = RefundCreateParams.builder()
                                .setCharge(tx.getStripeChargeId())
                                .setAmount(tx.getAmountCapturedCents())
                                .build();
                        Refund.create(params);
                    }

                    tx.setStatus(Transaction.TransactionStatus.REFUNDED);
                    break;


                // 🟢 CASE 2: RELEASE FULL TO SELLER
                case "RELEASE_SELLER":

                    long fullAmount = tx.getAmountCapturedCents() - platformFeeCents;

                    if (tx.getSellerStripeAccountId() != null && fullAmount > 0) {
                        Transfer.create(Map.of(
                                "amount", fullAmount,
                                "currency", tx.getCurrency(),
                                "destination", tx.getSellerStripeAccountId()
                        ));
                    }

                    tx.setStatus(Transaction.TransactionStatus.COMPLETED);
                    break;

                // 🟡 CASE 3: PARTIAL SPLIT
                case "PARTIAL":

                    long refundAmount = tx.getAmountCapturedCents() - deductionCents;

                    // refund buyer
                    if (refundAmount > 0 && tx.getStripeChargeId() != null) {
                        Refund.create(RefundCreateParams.builder()
                                .setCharge(tx.getStripeChargeId())
                                .setAmount(refundAmount)
                                .build());
                    }

                    // pay seller
                    long sellerAmount = deductionCents - platformFeeCents;

                    if (tx.getSellerStripeAccountId() != null && sellerAmount > 0) {
                        Transfer.create(Map.of(
                                "amount", sellerAmount,
                                "currency", tx.getCurrency(),
                                "destination", tx.getSellerStripeAccountId()
                        ));
                    }

                    tx.setStatus(Transaction.TransactionStatus.PARTIALLY_RELEASED);
                    break;

                default:
                    throw (new RuntimeException("Invalid decision"));
            }

            // 3️⃣ Update transaction and save
            tx.setUpdatedAt(Instant.now());
            transactionRepository.save(tx);

            // Fraud score + Rating
            if(req.getDecision().equals("REFUND_BUYER")){
                fraudService.penalizeUser(tx.getSellerId());
            }else if(req.getDecision().equals("RELEASE_SELLER")){
                fraudService.rewardUser(tx.getSellerId());
            }

            // ADMIN OVERRIDE + DISPUTE
            // ✅ Update dispute (THIS WAS MISSING 🔥)
            dispute.setStatus("RESOLVED");
            dispute.setResolvedAt(Instant.now());
            dispute.setResolvedBy(adminUserId);
            dispute.setResolutionNote(req.getAdminNote());
            dispute.setDecision(req.getDecision());
            dispute.setRefundAmountCents(tx.getAmountCapturedCents());
            disputeRepository.save(dispute);

            // 4️⃣ Log event
            eventRepository.save(PaymentEvent.builder()
                    .transactionId(tx.getId())
                    .type("DISPUTE_RESOLVED" + req.getDecision())
                    .actor(adminUserId)
                    .createdAt(Instant.now())
                    .build()
            );

            // TIMELINE LOG FOR DISPUTE RESOLVED
            timelineLogService.log(
                    tx.getId(),
                    adminUserId,
                    null,
                    "Admin resolved dispute" + req.getDecision(),
                    TimelineLog.ActionType.DISPUTE_RESOLVED,
                    TimelineLog.ActorType.ADMIN
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

            // TIMELINE LOG FOR PAYMENT SUCCESS
            timelineLogService.log(
                    tx.getId(),
                    null,
                    "SYSTEM",
                    "Payment authorized by Stripe",
                    TimelineLog.ActionType.PAYMENT_AUTHORIZED,
                    TimelineLog.ActorType.SYSTEM
            );
        });

        // Note: actual capture happens in our manual flow, not automatically on PI success, so we don't change to RELEASED here.

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

            // TIMELINE LOG FOR PAYMENT CANCELLED
            timelineLogService.log(
                    tx.getId(),
                    null,
                    "SYSTEM",
                    "Payment cancelled",
                    TimelineLog.ActionType.TRANSACTION_COMPLETED,
                    TimelineLog.ActorType.SYSTEM
            );
            // If a rental was in progress when the PI was cancelled, restore the listing
            // so it's available again. Sales never reach RENTED so this is safe to check.
            if (tx.getListingId() != null && tx.getType() == Transaction.TransactionType.RENT) {
                listingRepository.findById(tx.getListingId()).ifPresent(l -> {
                    if (l.getStatus() == Listing.ListingStatus.RENTED) {
                        l.setStatus(Listing.ListingStatus.ACTIVE);
                        listingRepository.save(l);
                    }
                });
            }
        });
    }

    // -------------------- Rental-specific methods --------------------

    public void startRental(String transactionId, String userEmail) {
        Transaction tx = getTransaction(transactionId);

        if (!Boolean.TRUE.equals(tx.getBuyerAcceptedCondition())) {
            throw new RuntimeException("Accept condition first");
        }

        if (!isTheBuyer(tx, userEmail)) {
            throw new RuntimeException("Only renter can start rental");
        }
        tx.setRenterPickedUp(true);
        tx.setStatus(Transaction.TransactionStatus.RENTAL_IN_PROGRESS);
        transactionRepository.save(tx);

        // TIMELINE LOG FOR RENTAL STARTED
        timelineLogService.log(
                tx.getId(),
                userEmail,
                userEmail,
                "Item picked up by renter",
                TimelineLog.ActionType.RENTAL_STARTED,
                TimelineLog.ActorType.USER
        );
    }

    public void completeRental(String transactionId, String userEmail) {
        Transaction tx = getTransaction(transactionId);
        if (!isTheBuyer(tx, userEmail) && !isTheSeller(tx, userEmail)) {
            throw new RuntimeException("Only renter or owner can mark rental complete");
        }
        tx.setRenterReturned(true);
        tx.setStatus(Transaction.TransactionStatus.RENTAL_RETURNED);
        transactionRepository.save(tx);

        timelineLogService.log(
                tx.getId(),
                userEmail,
                userEmail,
                "Item returned by renter",
                TimelineLog.ActionType.RENTAL_RETURNED,
                TimelineLog.ActorType.USER
        );

        // ── No-deposit shortcut ──────────────────────────────────────────────
        // If there is no security deposit the seller cannot report damage, so
        // there is nothing for them to do.  Capture the rental fee immediately
        // and complete the transaction — no seller action required.
        long deposit = tx.getDepositCents() != null ? tx.getDepositCents() : 0L;
        if (deposit == 0) {
            this.finalizeRefund(transactionId, tx.getSellerId());
        }
        // If deposit > 0, the status stays RENTAL_RETURNED and the seller's
        // "No Damage" / "Deduct Damage" buttons will appear on their dashboard.
    }

    /**
     * Seller reports damage and finalizes the rental.
     *
     * Strategy — uses Stripe partial capture so no separate refund call is needed:
     *   • The PI was authorized for (rentalFee + deposit).
     *   • We capture (rentalFee + damageAmount) → seller receives both.
     *   • Stripe automatically cancels the remaining (deposit − damageAmount)
     *     authorization and returns those funds to the buyer's card.
     *   • damageAmount must not exceed the deposit.
     */
    public void deductDamage(String transactionId, Long damageAmountCents, String userId) {
        Transaction tx = getTransaction(transactionId);

        if (!isTheSeller(tx, userId)) {
            throw new RuntimeException("Only seller can report damage");
        }
        if (tx.getStatus() != Transaction.TransactionStatus.RENTAL_RETURNED) {
            throw new RuntimeException("Transaction is not in RENTAL_RETURNED state");
        }

        long deposit = tx.getDepositCents() != null ? tx.getDepositCents() : 0L;
        if (damageAmountCents > deposit) {
            throw new RuntimeException("Damage amount exceeds deposit");
        }
        if (damageAmountCents <= 0) {
            throw new RuntimeException("Damage amount must be positive");
        }

        // Capture rentalFee + damage → Stripe auto-releases (deposit − damage) to buyer.
        long captureAmount = tx.getAmountCents() + damageAmountCents;
        this.capture(transactionId, userId, captureAmount);

        // Override status → rental is now fully complete.
        tx = getTransaction(transactionId);
        tx.setStatus(Transaction.TransactionStatus.RENT_COMPLETED);
        tx.setUpdatedAt(Instant.now());
        transactionRepository.save(tx);

        // Item returned (with damage) — make it available for rent again
        updateListingStatus(tx.getListingId(), Listing.ListingStatus.ACTIVE);

        eventRepository.save(PaymentEvent.builder()
                .transactionId(tx.getId())
                .type("DAMAGE_DEDUCTED")
                .actor(userId)
                .createdAt(Instant.now())
                .build()
        );

        timelineLogService.log(
                tx.getId(),
                userId,
                null,
                "Damage deducted from deposit; rental fee and damage amount captured",
                TimelineLog.ActionType.DAMAGE_REPORTED,
                TimelineLog.ActorType.USER
        );
    }

    //  ======= CONDITION ACCEPTANCE =============
    @Override
    public void acceptedConditions(String transactionId, String buyerId) {
        Transaction tx = getTransaction(transactionId);

        // only buyer can accept conditions
        if(!isTheBuyer(tx, buyerId)){
            throw new RuntimeException("Only buyer can accept conditions");
        }

        // Prevent double acceptance
        if(Boolean.TRUE.equals(tx.getBuyerAcceptedCondition())){
            throw new RuntimeException("Conditions already accepted");
        }

        // Must be in correct state to accept conditions
        if(!tx.getStatus().equals(Transaction.TransactionStatus.AUTHORIZED)){
            throw new RuntimeException("Transaction not in a state to accept conditions");
        }

        tx.setBuyerAcceptedCondition(true);
        tx.setConditionAcceptedAt(Instant.now());
        transactionRepository.save(tx);

        eventRepository.save(PaymentEvent.builder()
                .transactionId(tx.getId())
                .type("CONDITION_ACCEPTED")
                .actor(buyerId)
                .createdAt(Instant.now())
                .build()
        );

        // Timeline log service
        timelineLogService.log(
                tx.getId(),
                buyerId,
                null,
                "Buyer accepted conditions",
                TimelineLog.ActionType.CONDITION_ACCEPTED,
                TimelineLog.ActorType.USER
        );
    }

    /**
     * Seller finalizes a clean return (no damage).
     *
     * Strategy — uses Stripe partial capture so no separate refund call is needed:
     *   • The PI was authorized for (rentalFee + deposit).
     *   • We capture only rentalFee → Stripe automatically cancels the deposit
     *     authorization and returns those funds to the buyer's card.
     *   • If deposit == 0 this becomes a regular full capture.
     */
    public void finalizeRefund(String transactionId, String userId) {
        Transaction tx = getTransaction(transactionId);

        if (!isTheSeller(tx, userId)) {
            throw new RuntimeException("Only seller can finalize the rental");
        }
        // PARTIALLY_RELEASED / RELEASED means a previous capture() call succeeded in Stripe
        // but an exception was thrown before we could override the status to RENT_COMPLETED.
        // In that case the Stripe money is already settled — skip the capture and just fix the status.
        boolean alreadyCaptured = tx.getStatus() == Transaction.TransactionStatus.PARTIALLY_RELEASED
                || tx.getStatus() == Transaction.TransactionStatus.RELEASED;

        if (!alreadyCaptured) {
            if (tx.getStatus() != Transaction.TransactionStatus.RENTAL_RETURNED &&
                    tx.getStatus() != Transaction.TransactionStatus.DAMAGE_RESOLVED &&
                    tx.getStatus() != Transaction.TransactionStatus.RENTAL_IN_PROGRESS) {
                // RENTAL_IN_PROGRESS is allowed so the scheduler can auto-finalize
                // rentals whose end date passed without the renter clicking "Return".
                throw new RuntimeException("Transaction is not in a returnable state");
            }
            // Capture only the rental fee; Stripe auto-releases the deposit portion.
            long rentalFee = tx.getAmountCents();
            this.capture(transactionId, userId, rentalFee);
        }
        // else: Stripe already settled — skip capture, fall through to status override below

        // Override the status set by capture() → mark rental fully complete.
        tx = getTransaction(transactionId);
        tx.setStatus(Transaction.TransactionStatus.RENT_COMPLETED);
        tx.setUpdatedAt(Instant.now());
        transactionRepository.save(tx);

        // Item returned — put it back on the platform for future rentals
        updateListingStatus(tx.getListingId(), Listing.ListingStatus.ACTIVE);

        eventRepository.save(PaymentEvent.builder()
                .transactionId(tx.getId())
                .type("RENTAL_FINALIZED")
                .actor(userId)
                .createdAt(Instant.now())
                .build()
        );

        timelineLogService.log(
                tx.getId(),
                userId,
                null,
                "Seller released rental fee; deposit returned to renter",
                TimelineLog.ActionType.TRANSACTION_COMPLETED,
                TimelineLog.ActorType.USER
        );
    }


    // ---------- helper implementations ----------
    @Override
    public Transaction getTransaction(String id) {
        return transactionRepository.findById(id).orElseThrow(() -> new RuntimeException("Transaction not found"));
    }

    @Override
    public Page<Transaction> listForUser(String userId, Pageable pageable) {
        // userId from JWT is always the email.
        // Old transactions stored email as buyerId/sellerId.
        // New transactions store the MongoDB ObjectId.
        // Resolve email → ObjectId and pass BOTH to the query so both sets are visible.
        String resolvedObjectId = userId; // fallback: same as email
        try {
            User user = userRepository.findByEmail(userId).orElse(null);
            if (user != null) {
                resolvedObjectId = user.getId();
            }
        } catch (Exception ignored) {}
        return transactionRepository.findByBuyerOrSeller(userId, resolvedObjectId, pageable);
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

    // ─── Listing status helper ───────────────────────────────────────────────
    /**
     * Updates the status of the associated listing.
     * Silently no-ops if the listing doesn't exist or listingId is null.
     */
    private void updateListingStatus(String listingId, Listing.ListingStatus newStatus) {
        if (listingId == null) return;
        listingRepository.findById(listingId).ifPresent(l -> {
            l.setStatus(newStatus);
            listingRepository.save(l);
        });
    }

    // ─── Rental end-date helper ───────────────────────────────────────────────
    /**
     * Computes the expected rental end date from the request.
     * For PER_DAY listings: today + N days.
     * For PER_HOUR listings: today + ceil(N / 24) days (stored as LocalDate — no time component).
     * Returns null for sale transactions or when duration is not set.
     */
    private LocalDate computeRentalEnd(CreateTransactionRequest req) {
        if (req.getType() != Transaction.TransactionType.RENT) return null;
        if (req.getRentalDurationUnits() == null || req.getRentalDurationUnits() <= 0) return null;
        int units = req.getRentalDurationUnits();
        if ("PER_HOUR".equalsIgnoreCase(req.getRentalPeriod())) {
            // Convert hours to days (ceiling) for LocalDate storage
            return LocalDate.now().plusDays((long) Math.ceil(units / 24.0));
        }
        // Default: PER_DAY
        return LocalDate.now().plusDays(units);
    }


}
