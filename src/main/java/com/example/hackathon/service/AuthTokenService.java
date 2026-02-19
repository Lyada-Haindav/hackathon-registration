package com.example.hackathon.service;

import com.example.hackathon.exception.BadRequestException;
import com.example.hackathon.model.AuthToken;
import com.example.hackathon.model.AuthTokenType;
import com.example.hackathon.model.User;
import com.example.hackathon.repository.AuthTokenRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
public class AuthTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthTokenRepository authTokenRepository;

    public AuthTokenService(AuthTokenRepository authTokenRepository) {
        this.authTokenRepository = authTokenRepository;
    }

    public String createToken(User user, AuthTokenType type, long ttlMinutes) {
        authTokenRepository.deleteByUserIdAndType(user.getId(), type);
        authTokenRepository.deleteByExpiresAtBefore(Instant.now());

        AuthToken authToken = new AuthToken();
        authToken.setUserId(user.getId());
        authToken.setEmail(user.getEmail());
        authToken.setType(type);
        authToken.setToken(generateTokenValue());
        authToken.setExpiresAt(Instant.now().plusSeconds(Math.max(ttlMinutes, 1) * 60));

        return authTokenRepository.save(authToken).getToken();
    }

    public AuthToken getValidToken(String tokenValue, AuthTokenType type) {
        AuthToken token = authTokenRepository.findByTokenAndType(tokenValue, type)
                .orElseThrow(() -> new BadRequestException("Invalid or expired link"));

        if (token.getExpiresAt() == null || token.getExpiresAt().isBefore(Instant.now())) {
            authTokenRepository.deleteById(token.getId());
            throw new BadRequestException("Invalid or expired link");
        }
        return token;
    }

    public void consumeToken(String tokenValue, AuthTokenType type) {
        AuthToken token = getValidToken(tokenValue, type);
        authTokenRepository.deleteById(token.getId());
    }

    private String generateTokenValue() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
