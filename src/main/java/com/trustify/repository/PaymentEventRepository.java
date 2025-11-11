package com.trustify.repository;

import com.trustify.model.PaymentEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PaymentEventRepository extends MongoRepository<PaymentEvent, String> {
    List<PaymentEvent> findByTransactionId(String txId);
}
