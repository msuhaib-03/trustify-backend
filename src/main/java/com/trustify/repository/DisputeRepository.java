package com.trustify.repository;

import com.trustify.model.Dispute;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DisputeRepository extends MongoRepository<Dispute, String> {
    List<Dispute> findByTransactionId(String transactionId);
    List<Dispute> findByStatus(String status); // for admin dashboard
}


