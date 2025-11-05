package com.trustify.repository;

import com.trustify.model.Listing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface ListingRepository extends MongoRepository<Listing, String> {
    List<Listing> findByStatus(Listing.ListingStatus status);
    Page<Listing> findByStatus(Listing.ListingStatus status, Pageable pageable);
    List<Listing> findByType(Listing.ListingType type);

    Page<Listing> findByStatusAndType(Listing.ListingStatus status, Listing.ListingType type, Pageable pageable);


    @Query("{ 'category': { $regex: ?0, $options: 'i' }, 'type': ?1, 'price': { $lte: ?2 } }")
    List<Listing> searchListings(String category, Listing.ListingType type, Double priceMax);

    List<Listing> findByOwnerId(String ownerId);
    Page<Listing> findByOwnerId(String ownerId, Pageable pageable);


    @Query("{ 'category': { $regex: ?0, $options: 'i' }, 'type': ?1, 'price': { $lte: ?2 }, 'status': 'ACTIVE' }")
    Page<Listing> searchListings(String category, Listing.ListingType type, Double priceMax, Pageable pageable);


}
