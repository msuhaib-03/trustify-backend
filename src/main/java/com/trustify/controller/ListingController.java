package com.trustify.controller;

import com.trustify.dto.ListingDTO;
import com.trustify.model.Listing;
import com.trustify.service.ImageUploadService;
import com.trustify.service.ListingService;
import com.trustify.service.impl.ListingServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/listings")
@RequiredArgsConstructor
public class ListingController {

    private final ListingService listingService;
    private final ImageUploadService imageUploadService;
    private final ListingServiceImpl listingServiceImpl;


    // Auth required
    @PostMapping("/create")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> createListing(
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam("price") Double price,
            @RequestParam("type") String type,
            @RequestParam("category") String category,
            @RequestParam(value = "images", required = false) List<MultipartFile> images,
            Principal principal
    ){
        try {
            List<String> imageUrls = new ArrayList<>();
            if (images != null && !images.isEmpty()) {
                for (MultipartFile file : images) {
                    String url = imageUploadService.saveImage(file);
                    imageUrls.add(url);
                }
            }

            ListingDTO dto = new ListingDTO();
            dto.setTitle(title);
            dto.setDescription(description);
            dto.setPrice(price);
            dto.setType(Listing.ListingType.valueOf(type.toUpperCase()));
            dto.setCategory(category);
            dto.setImageUrls(imageUrls);

            return ResponseEntity.ok(listingService.createListing(dto, principal));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // no auth required
    // http://localhost:8080/listings?page=0&size=5&sortBy=price&sortDir=asc
    // this is how it is going to be working in postman
    // this is how it worked earlier
    // http://localhost:8080/api/listings
    @GetMapping
    public ResponseEntity<?> getAllListings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            Principal principal
    ) {
        return ResponseEntity.ok(listingService.getAllActiveListings(page, size, sortBy, sortDir, principal));
      //  return ResponseEntity.ok(listingService.getAllActiveListings());
    }

    // no auth required
    @GetMapping("/{id}")
    public ResponseEntity<ListingDTO> getListingById(@PathVariable String id, HttpServletRequest request) {
        Listing listing = listingService.getListingById(id);

        ListingDTO dto = new ListingDTO();
        dto.setTitle(listing.getTitle());
        dto.setDescription(listing.getDescription());
        dto.setPrice(listing.getPrice());
        dto.setType(listing.getType());
        dto.setCategory(listing.getCategory());
        dto.setImageUrls(listingServiceImpl.buildFullImageUrls(listing.getImageUrls(), request));

        return ResponseEntity.ok(dto);
      //  return ResponseEntity.ok(listingService.getListingById(id));
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

    // Bearer token authorization will be required to upload images
    // Under Body -> form-data, key as 'file', type as 'file' and value as the image file to be uploaded
    @PostMapping("/upload-image")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            String fileUrl = imageUploadService.saveImage(file);
            return ResponseEntity.ok(Map.of("imageUrl", fileUrl));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // no auth required
    @GetMapping("/rent")
    public ResponseEntity<List<Listing>> getAllRentListings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        return ResponseEntity.ok(listingService.getListingsByType(Listing.ListingType.RENT, page, size, sortBy, sortDir));

        //return ResponseEntity.ok(listingService.getListingsByType(Listing.ListingType.RENT));
    }

    // no auth required
    @GetMapping("/sell")
    public ResponseEntity<List<Listing>> getAllSellListings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        return ResponseEntity.ok(listingService.getListingsByType(Listing.ListingType.SALE, page, size, sortBy, sortDir));
        // return ResponseEntity.ok(listingService.getListingsByType(Listing.ListingType.SALE));
    }

    // no auth required
    @GetMapping("/search")
    public ResponseEntity<Page<Listing>> searchListings(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Listing.ListingType type,
            @RequestParam(required = false) Double priceMax,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        return ResponseEntity.ok(listingService.searchListings(
                category != null ? category : "",
                type != null ? type : Listing.ListingType.SALE,
                priceMax != null ? priceMax : Double.MAX_VALUE,
                page, size, sortBy, sortDir
        ));
    }

    // auth required
    @GetMapping("/mine")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<Listing>> getMyListings(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
            ) {
        return ResponseEntity.ok(listingService.getListingsByOwner(principal, page, size, sortBy, sortDir));
        //return ResponseEntity.ok(listingService.getListingsByUser(principal));
    }

    // this requires auth
    @PostMapping("/{listingId}/favorite")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> toggleFavorite(
            @PathVariable String listingId,
            Principal principal
    ) {
        try {
            boolean isFavorite = listingService.toggleFavorite(listingId, principal);
            return ResponseEntity.ok(Map.of("message", isFavorite ? "Added to favorites" : "Removed from favorites"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // this requires auth
    @GetMapping("/favorites")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getUserFavorites(Principal principal) {
        try {
            return ResponseEntity.ok(listingService.getFavoriteListings(principal));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }


}
