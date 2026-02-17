package com.example.hackathon.repository;

import com.example.hackathon.model.Criterion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CriterionRepository extends MongoRepository<Criterion, String> {
    List<Criterion> findByEventId(String eventId);

    void deleteByEventId(String eventId);
}
