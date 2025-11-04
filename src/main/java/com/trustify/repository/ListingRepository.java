package com.trustify.repository;

import com.trustify.model.Listing;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface ListingRepository extends MongoRepository<Listing, String> {
    List<Listing> findByStatus(Listing.ListingStatus status);
    List<Listing> findByType(Listing.ListingType type);

    @Query("{ 'category': { $regex: ?0, $options: 'i' }, 'type': ?1, 'price': { $lte: ?2 } }")
    List<Listing> searchListings(String category, Listing.ListingType type, Double priceMax);

    List<Listing> findByOwnerId(String ownerId);



}
