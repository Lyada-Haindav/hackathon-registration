package com.example.hackathon.repository;

import com.example.hackathon.model.AuthToken;
import com.example.hackathon.model.AuthTokenType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.Optional;

public interface AuthTokenRepository extends MongoRepository<AuthToken, String> {

    Optional<AuthToken> findByTokenAndType(String token, AuthTokenType type);

    void deleteByUserIdAndType(String userId, AuthTokenType type);

    void deleteByExpiresAtBefore(Instant timestamp);
}
