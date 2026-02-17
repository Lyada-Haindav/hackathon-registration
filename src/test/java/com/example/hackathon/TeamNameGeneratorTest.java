package com.example.hackathon;

import com.example.hackathon.util.TeamNameGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class TeamNameGeneratorTest {

    @Test
    void generatesNonEmptyName() {
        TeamNameGenerator generator = new TeamNameGenerator();
        String name = generator.generateUniqueName();
        assertFalse(name.isBlank());
    }
}
