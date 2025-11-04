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
    private double price;

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

    //private String location;


}
