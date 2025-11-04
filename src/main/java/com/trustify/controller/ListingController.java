package com.trustify.controller;

import com.trustify.dto.ListingDTO;
import com.trustify.service.ListingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/listings")
@RequiredArgsConstructor
public class ListingController {
    private final ListingService listingService;

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> createListing(@Valid @RequestBody ListingDTO dto, Principal principal) {
        return ResponseEntity.ok(listingService.createListing(dto, principal));
    }

    @GetMapping
    public ResponseEntity<?> getAllListings() {
        return ResponseEntity.ok(listingService.getAllActiveListings());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getListingById(@PathVariable String id) {
        return ResponseEntity.ok(listingService.getListingById(id));
    }

    // This endpoint allows users to delete their own listings by id and bearer token has to be provided
    // which means that only that user can delete his own listing and not someone else's
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> deleteListing(@PathVariable String id, Principal principal) {
        try {
            listingService.deleteListing(id, principal);
        } catch (java.nio.file.AccessDeniedException e) {
            throw new RuntimeException(e);
        }
        return ResponseEntity.ok("Listing removed successfully.");
    }
}
