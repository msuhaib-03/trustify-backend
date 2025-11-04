package com.trustify.service;

import com.trustify.dto.ListingDTO;
import com.trustify.model.Listing;

import java.nio.file.AccessDeniedException;
import java.security.Principal;
import java.util.List;

public interface ListingService {
    Listing createListing(ListingDTO dto, Principal principal);

    List<Listing> getAllActiveListings();

    Listing getListingById(String id);

    void deleteListing(String id, Principal principal) throws AccessDeniedException;
}
