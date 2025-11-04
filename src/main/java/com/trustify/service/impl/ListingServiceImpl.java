package com.trustify.service.impl;

import com.trustify.dto.ListingDTO;
import com.trustify.model.Listing;
import com.trustify.model.User;
import com.trustify.repository.ListingRepository;
import com.trustify.repository.UserRepository;
import com.trustify.service.ListingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.AccessDeniedException;
import java.security.Principal;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ListingServiceImpl implements ListingService {

    private final ListingRepository listingRepository;
    private final UserRepository userRepository;

    @Override
    public Listing createListing(ListingDTO dto, Principal principal) {
        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Listing listing = Listing.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .price(dto.getPrice())
                .type(dto.getType())
                .category(dto.getCategory())
                .imageUrls(dto.getImageUrls())
                .ownerId(user.getId())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return listingRepository.save(listing);
    }

    @Override
    public List<Listing> getAllActiveListings() {
        return listingRepository.findByStatus(Listing.ListingStatus.ACTIVE);
    }

    @Override
    public Listing getListingById(String id) {
        return listingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Listing not found"));
    }

    @Override
    public void deleteListing(String id, Principal principal) throws AccessDeniedException {
        Listing listing = listingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Listing not found"));

        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Ownership check
        if (!listing.getOwnerId().equals(user.getId()) && user.getRole() != User.Role.ADMIN) {
            throw new AccessDeniedException("You cannot delete this listing");
        }

        listingRepository.delete(listing);
    }
}
