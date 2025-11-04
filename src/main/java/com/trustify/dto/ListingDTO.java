package com.trustify.dto;

import com.trustify.model.Listing;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ListingDTO {
    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Price is required")
    private Double price;

    @NotNull(message = "Listing type is required (SALE or RENT)")
    private Listing.ListingType type;

    @NotBlank(message = "Category is required")
    private String category;

    private List<String> imageUrls;
}
