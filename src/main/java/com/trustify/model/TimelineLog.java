package com.trustify.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "timeline_logs")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TimelineLog {

    @Id
    private String id;

    private String transactionId; // main reference to the transaction

    private String userId;
    private String username;

    private ActorType actorType;
    private ActionType actionType;

    private String description; // human-readable description of the action
    private String relatedEntityId; // e.g. paymentIntentId, disputeId, etc.

    @CreatedDate
    private Instant createdAt;

    public enum ActorType{
        SYSTEM,
        USER,
        ADMIN
    }

    public enum ActionType {
        TRANSACTION_CREATED,
        PAYMENT_INITIATED,
        PAYMENT_AUTHORIZED,
        PAYMENT_HELD,

        CONDITION_ACCEPTED,
        ITEM_PICKED_UP,
        RENTAL_STARTED,
        RENTAL_RETURNED,

        DAMAGE_REPORTED,
        DISPUTE_RAISED,
        DISPUTE_RESOLVED,

        PAYMENT_RELEASED,
        REFUND_ISSUED,
        TRANSACTION_COMPLETED,

        ADMIN_OVERRIDE
    }
}
