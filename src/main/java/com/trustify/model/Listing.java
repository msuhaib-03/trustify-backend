package com.trustify.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "listings")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Listing {

    @Id
    private String id;

    private String title;
    private String description;


    /** Asking price in USD (displayed to users as both USD and PKR equivalent). */
    private double price;

    /**
     * For RENT listings only — how the price is billed.
     * Defaults to PER_DAY if not specified.
     */
    @Builder.Default
    private RentalPeriod rentalPeriod = RentalPeriod.PER_DAY;

    /**
     * For RENT listings only — the item's declared market value in PKR.
     * Used together with the category deposit percentage to auto-compute depositAmountUsd.
     */
    private Double declaredValuePkr;

    /**
     * For RENT listings only — security deposit in USD, auto-calculated from
     * declaredValuePkr × categoryDepositPercentage / 100 / PKR_RATE.
     * null / 0 means no deposit; the escrow will auto-release on return.
     */
    private Double depositAmountUsd;


    private String ownerId; // Reference to User ID

    // Whether the item is for SALE or RENT
    private ListingType type; // SALE or RENT

    private String category;
    private List<String> imageUrls;

    @Builder.Default
    private ListingStatus status = ListingStatus.ACTIVE;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedBy
    private Instant updatedAt;

    public enum ListingType {
        SALE,
        RENT
    }

    public enum ListingStatus {
        ACTIVE,
        SOLD,
        RENTED,
        REMOVED
    }

    public enum RentalPeriod {
        PER_HOUR,
        PER_DAY
    }


    //private String location;


}
