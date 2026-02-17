package com.example.hackathon.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;

@Component
public class TeamNameGenerator {

    private static final String[] ADJECTIVES = {
            "Quantum", "Rapid", "Dynamic", "Nimble", "Vision", "Binary", "Fusion", "Apex"
    };

    private static final String[] NOUNS = {
            "Coders", "Builders", "Pioneers", "Hackers", "Creators", "Minds", "Innovators", "Titans"
    };

    private final SecureRandom random = new SecureRandom();

    public String generateUniqueName() {
        String adjective = ADJECTIVES[random.nextInt(ADJECTIVES.length)];
        String noun = NOUNS[random.nextInt(NOUNS.length)];
        long suffix = Instant.now().toEpochMilli() % 100000;
        return adjective + noun + suffix;
    }
}
