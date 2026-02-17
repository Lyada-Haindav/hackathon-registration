package com.example.hackathon.repository;

import com.example.hackathon.model.ProblemStatement;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ProblemStatementRepository extends MongoRepository<ProblemStatement, String> {
    List<ProblemStatement> findByEventId(String eventId);

    List<ProblemStatement> findByEventIdAndReleasedTrue(String eventId);

    void deleteByEventId(String eventId);
}
