package com.trustify.repository;

import com.trustify.model.CategoryDepositConfig;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CategoryDepositConfigRepository extends MongoRepository<CategoryDepositConfig, String> {
    Optional<CategoryDepositConfig> findByCategory(String category);
}
