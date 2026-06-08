package com.trustify.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Stores the admin-configurable security deposit percentage for each listing category.
 * depositPercentage is applied to the listing's declaredValuePkr to compute the
 * security deposit charged to the renter.
 *
 * Example: Electronics = 90 → deposit = 90% of declared item value.
 */
@Document(collection = "category_deposit_config")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CategoryDepositConfig {

    @Id
    private String id;

    /** Must match one of the frontend category values: Electronics, Fashion, Furniture, Sports, Books, Other */
    private String category;

    /** 0–100 — percentage of declaredValuePkr to charge as security deposit */
    @Builder.Default
    private int depositPercentage = 50;

    @Builder.Default
    private Instant updatedAt = Instant.now();
}
