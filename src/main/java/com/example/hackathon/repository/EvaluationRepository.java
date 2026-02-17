package com.example.hackathon.repository;

import com.example.hackathon.model.Evaluation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface EvaluationRepository extends MongoRepository<Evaluation, String> {
    Optional<Evaluation> findByEventIdAndTeamIdAndCriterionId(String eventId, String teamId, String criterionId);

    List<Evaluation> findByEventIdAndTeamId(String eventId, String teamId);

    List<Evaluation> findByEventId(String eventId);

    void deleteByEventId(String eventId);
}
