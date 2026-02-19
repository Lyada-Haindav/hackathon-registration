package com.example.hackathon;

import com.example.hackathon.util.TeamNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TeamNameGeneratorTest {

    @Test
    void generatesNonEmptyName() {
        TeamNameGenerator generator = new TeamNameGenerator();
        String name = generator.generateUniqueName();
        assertFalse(name.isBlank());
    }

    @Test
    void generatesManyDistinctNames() {
        TeamNameGenerator generator = new TeamNameGenerator();
        int sampleSize = 5000;
        Set<String> names = new HashSet<>();

        for (int i = 0; i < sampleSize; i++) {
            names.add(generator.generateUniqueName());
        }

        assertEquals(sampleSize, names.size());
    }
}
