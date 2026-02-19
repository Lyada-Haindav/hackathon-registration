package com.example.hackathon.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class TeamNameGenerator {

    private static final String[] ADJECTIVES = {
            "Agile", "Alpha", "Apex", "Astro", "Atomic", "Binary", "Blaze", "Bold", "Bright", "Cosmic",
            "Crest", "Crimson", "Cyber", "Dynamic", "Electric", "Epic", "Flash", "Fusion", "Future", "Glide",
            "Hyper", "Ignite", "Infinite", "Lunar", "Matrix", "Meta", "Nimble", "Nova", "Omega", "Pixel",
            "Prime", "Quantum", "Rapid", "Rocket", "Sharp", "Solar", "Spark", "Stellar", "Turbo", "Ultra",
            "Vector", "Vertex", "Vibrant", "Vision", "Volt", "Zen"
    };

    private static final String[] NOUNS = {
            "Architects", "Builders", "Catalysts", "Coders", "Commanders", "Creators", "Crew", "Defenders", "Dynamos",
            "Engineers", "Explorers", "Force", "Foundry", "Guardians", "Hackers", "Innovators", "Legends", "Makers",
            "Masters", "Minds", "Navigators", "Ninjas", "Pioneers", "Pilots", "Rangers", "Raiders", "Rockets",
            "Scholars", "Shapers", "Squad", "Stars", "Storm", "Strategists", "Strikers", "Synapse", "Techies",
            "Titans", "Trailblazers", "Troop", "Vanguards", "Warriors", "Wizards"
    };

    private static final char[] TOKEN_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int TOKEN_LENGTH = 5;

    private final SecureRandom random = new SecureRandom();

    public String generateUniqueName() {
        String adjective = ADJECTIVES[random.nextInt(ADJECTIVES.length)];
        String noun = NOUNS[random.nextInt(NOUNS.length)];
        return adjective + noun + randomToken();
    }

    private String randomToken() {
        StringBuilder builder = new StringBuilder(TOKEN_LENGTH);
        for (int i = 0; i < TOKEN_LENGTH; i++) {
            builder.append(TOKEN_CHARS[random.nextInt(TOKEN_CHARS.length)]);
        }
        return builder.toString();
    }
}
