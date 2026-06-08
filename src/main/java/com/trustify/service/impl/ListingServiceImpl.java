package com.trustify.service.impl;

import com.trustify.dto.ListingDTO;
import com.trustify.model.CategoryDepositConfig;
import com.trustify.model.Listing;
import com.trustify.model.User;
import com.trustify.repository.CategoryDepositConfigRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ListingServiceImpl implements ListingService {

    private static final double PKR_RATE = 282.0; // 1 USD = 282 PKR (keep in sync with frontend)
    private final ListingRepository listingRepository;
    private final UserRepository userRepository;
    private final CategoryDepositConfigRepository depositConfigRepository;

    @Override
    public Listing createListing(ListingDTO dto, Principal principal) {
        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Auto-compute security deposit for RENT listings
        Double depositAmountUsd = null;
        if (dto.getType() == Listing.ListingType.RENT && dto.getDeclaredValuePkr() != null && dto.getDeclaredValuePkr() > 0) {
            int pct = depositConfigRepository.findByCategory(dto.getCategory())
                    .map(CategoryDepositConfig::getDepositPercentage)
                    .orElse(defaultDepositPct(dto.getCategory()));
            depositAmountUsd = (dto.getDeclaredValuePkr() * pct / 100.0) / PKR_RATE;
            // Round to 2 decimal places
            depositAmountUsd = Math.round(depositAmountUsd * 100.0) / 100.0;
        }

        Listing listing = Listing.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .price(dto.getPrice())
                .type(dto.getType())
                .category(dto.getCategory())
                .imageUrls(dto.getImageUrls())
                .ownerId(user.getId())
                .declaredValuePkr(dto.getDeclaredValuePkr())
                .depositAmountUsd(depositAmountUsd)
                .rentalPeriod(dto.getRentalPeriod() != null ? dto.getRentalPeriod() : Listing.RentalPeriod.PER_DAY)
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
        if (imagePaths == null) return new ArrayList<>();
        String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
        return imagePaths.stream()
                .filter(p -> p != null && !p.isBlank())
                .map(p -> p.startsWith("http") ? p : baseUrl + p)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public List<Listing> getListingsByType(Listing.ListingType type) {
        return listingRepository.findByType(type);
    }

    @Override
    public Page<Listing> searchListings(String category, Listing.ListingType type, Double priceMax, int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ?
                Sort.by(sortBy).ascending() :
                Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sort);
        return listingRepository.searchListings(category, type, priceMax, pageable);
    }

    @Override
    public List<Listing> getListingsByUser(Principal principal) {
        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return listingRepository.findByOwnerId(user.getId());
    }

    @Override
    public List<ListingDTO> getAllActiveListings(int page, int size, String sortBy, String sortDir, Principal principal) {
        Sort sort = sortDir.equalsIgnoreCase("asc") ?
                Sort.by(sortBy).ascending() :
                Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Listing> listingsPage = listingRepository.findByStatus(Listing.ListingStatus.ACTIVE, pageable);

        Set<String> userFavorites; // empty by default

        if (principal != null) {
            userFavorites = userRepository.findByEmail(principal.getName())
                    .map(User::getFavoriteListingIds)
                    .orElse(Set.of());
        } else {
            userFavorites = Set.of();
        }
        // ✅ Convert listings to DTOs and set `isFavorite`
        return listingsPage.getContent().stream()
                .map(listing -> mapToDTO(listing, userFavorites))
                .toList();

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
    public List<Listing> getListingsByOwner(Principal principal, int page, int size, String sortBy, String sortDir) {
        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Listing> pageResult = listingRepository.findByOwnerId(user.getId(), pageable);
        return pageResult.getContent();
    }

@Override
public boolean toggleFavorite(String listingId, Principal principal) {
    User user = userRepository.findByEmail(principal.getName())
            .orElseThrow(() -> new RuntimeException("User not found"));

    Listing listing = listingRepository.findById(listingId)
            .orElseThrow(() -> new RuntimeException("Listing not found"));

    Set<String> favorites = user.getFavoriteListingIds();

    boolean added;
    if (favorites.contains(listingId)) {
        favorites.remove(listingId);
        added = false;
    } else {
        favorites.add(listingId);
        added = true;
    }

    user.setFavoriteListingIds(favorites);
    userRepository.save(user);

    return added;
}

    @Override
    public List<ListingDTO> getFavoriteListings(Principal principal) {
        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Set<String> favoriteIds = user.getFavoriteListingIds();
        List<Listing> listings = listingRepository.findAllById(favoriteIds);

        return listings.stream()
                .map(listing -> mapToDTO(listing, favoriteIds))
                .toList();
    }

    /** Default deposit percentages used when the admin hasn't configured a category yet. */
    private int defaultDepositPct(String category) {
        if (category == null) return 50;
        return switch (category) {
            case "Electronics" -> 90;
            case "Furniture"   -> 70;
            case "Books"       -> 80;
            case "Sports"      -> 60;
            case "Fashion"     -> 50;
            default            -> 50;
        };
    }


    private ListingDTO mapToDTO(Listing listing, Set<String> userFavorites) {
        ListingDTO dto = new ListingDTO();
        dto.setId(listing.getId());
        dto.setTitle(listing.getTitle());
        dto.setDescription(listing.getDescription());
        dto.setPrice(listing.getPrice());
        dto.setType(listing.getType());
        dto.setCategory(listing.getCategory());
        dto.setImageUrls(listing.getImageUrls());
        dto.setFavorite(userFavorites.contains(listing.getId()));
        dto.setDeclaredValuePkr(listing.getDeclaredValuePkr());
        dto.setDepositAmountUsd(listing.getDepositAmountUsd());
        dto.setRentalPeriod(listing.getRentalPeriod());
        return dto;
    }


}
