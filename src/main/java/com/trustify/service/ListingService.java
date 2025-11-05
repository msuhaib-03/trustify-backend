package com.trustify.service;

import com.trustify.dto.ListingDTO;
import com.trustify.model.Listing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.nio.file.AccessDeniedException;
import java.security.Principal;
import java.util.List;

public interface ListingService {
    Listing createListing(ListingDTO dto, Principal principal);

    List<Listing> getAllActiveListings();

    Listing getListingById(String id);

    void deleteListing(String id, Principal principal) throws AccessDeniedException;

    List<Listing> getListingsByType(Listing.ListingType type);

    List<Listing> searchListings(String category, Listing.ListingType type, Double priceMax);

    List<Listing> getListingsByUser(Principal principal);

    List<Listing> getAllActiveListings(int page, int size, String sortBy, String sortDir);

    List<Listing> getListingsByType(Listing.ListingType type, int page, int size, String sortBy, String sortDir);

    List<Listing> searchListings(String category, Listing.ListingType type, Double priceMax, int page, int size, String sortBy, String sortDir);

    List<Listing> getListingsByOwner(Principal principal, int page, int size, String sortBy, String sortDir);


}
