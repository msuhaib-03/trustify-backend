package com.trustify.repository;

import com.trustify.model.CnicVerification;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CnicVerificationRepository extends MongoRepository<CnicVerification, String> {
    Optional<CnicVerification> findByUserId(String userId);

    Optional<CnicVerification> findByExtractedCnicNumber(String cnic);

    List<CnicVerification> findByStatus(CnicVerification.VerificationStatus status);
}
