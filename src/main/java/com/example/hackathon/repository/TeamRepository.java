package com.example.hackathon.repository;

import com.example.hackathon.model.Team;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends MongoRepository<Team, String> {
    Optional<Team> findByEventIdAndUserId(String eventId, String userId);

    List<Team> findByEventId(String eventId);

    List<Team> findByUserId(String userId);

    void deleteByEventId(String eventId);
}
