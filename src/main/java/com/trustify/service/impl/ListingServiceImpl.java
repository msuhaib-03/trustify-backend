package com.trustify.service.impl;

import com.trustify.dto.ListingDTO;
import com.trustify.model.Listing;
import com.trustify.model.User;
import com.trustify.repository.ListingRepository;
import com.trustify.repository.UserRepository;
import com.trustify.service.ListingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    public List<String> buildFullImageUrls(List<String> imagePaths, HttpServletRequest request) {
        String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
        return imagePaths.stream()
                .map(path -> baseUrl + path)
                .toList();
    }

    @Override
    public List<Listing> getListingsByType(Listing.ListingType type) {
        return listingRepository.findByType(type);
    }

    @Override
    public List<Listing> searchListings(String category, Listing.ListingType type, Double priceMax) {
        return listingRepository.searchListings(category, type, priceMax);
    }

    @Override
    public List<Listing> getListingsByUser(Principal principal) {
        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return listingRepository.findByOwnerId(user.getId());
    }


//    @Override
//    public Page<Listing> getAllActiveListings(Pageable pageable) {
//        return listingRepository.findByStatus(Listing.ListingStatus.ACTIVE, pageable);
//    }

    @Override
    public List<Listing> getAllActiveListings(int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ?
                Sort.by(sortBy).ascending() :
                Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Listing> listingsPage = listingRepository.findByStatus(Listing.ListingStatus.ACTIVE, pageable);

        return listingsPage.getContent();
    }


    @Override
    public List<Listing> getListingsByType(Listing.ListingType type, int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ?
                Sort.by(sortBy).ascending() :
                Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Listing> listingsPage = listingRepository.findByStatusAndType(Listing.ListingStatus.ACTIVE, type, pageable);

        return listingsPage.getContent();
    }


    @Override
    public List<Listing> searchListings(String category, Listing.ListingType type, Double priceMax, int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ?
                Sort.by(sortBy).ascending() :
                Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sort);
        return (List<Listing>) listingRepository.searchListings(category, type, priceMax, pageable);
    }

    @Override
    public List<Listing> getListingsByOwner(Principal principal, int page, int size, String sortBy, String sortDir) {
        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Listing> pageResult = listingRepository.findByOwnerId(user.getId(), pageable);
        return pageResult.getContent();
    }


}
