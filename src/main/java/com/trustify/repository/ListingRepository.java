package com.trustify.repository;

import com.trustify.model.Listing;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ListingRepository extends MongoRepository<Listing, String> {
    List<Listing> findByStatus(Listing.ListingStatus status);
}
