package com.trustify.repository;

import com.trustify.model.Dispute;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface DisputeRepository extends MongoRepository<Dispute, String> {
    Optional<Dispute> findByTransactionId(String transactionId);
    List<Dispute> findByStatus(String status); // for admin dashboard
}


