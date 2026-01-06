package com.trustify.repository;

import com.trustify.model.BlacklistedToken;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TokenBlacklistRepository extends MongoRepository<BlacklistedToken, String> {
    boolean existsByToken(String token);
}
