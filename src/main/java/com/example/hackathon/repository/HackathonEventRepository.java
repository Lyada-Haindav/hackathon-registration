package com.example.hackathon.repository;

import com.example.hackathon.model.HackathonEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface HackathonEventRepository extends MongoRepository<HackathonEvent, String> {
    List<HackathonEvent> findByActiveTrue();
}
