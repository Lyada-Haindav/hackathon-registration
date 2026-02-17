package com.example.hackathon.service;

import com.example.hackathon.dto.ProblemStatementRequest;
import com.example.hackathon.dto.ProblemStatementResponse;
import com.example.hackathon.exception.ResourceNotFoundException;
import com.example.hackathon.model.ProblemStatement;
import com.example.hackathon.repository.ProblemStatementRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class ProblemStatementService {

    private final ProblemStatementRepository problemStatementRepository;
    private final EventService eventService;

    public ProblemStatementService(ProblemStatementRepository problemStatementRepository, EventService eventService) {
        this.problemStatementRepository = problemStatementRepository;
        this.eventService = eventService;
    }

    public ProblemStatementResponse create(ProblemStatementRequest request, String facultyEmail) {
        eventService.getEventEntity(request.eventId());

        ProblemStatement statement = new ProblemStatement();
        statement.setEventId(request.eventId());
        statement.setTitle(request.title());
        statement.setDescription(request.description());
        statement.setCreatedBy(facultyEmail);
        statement.setReleased(false);

        return toResponse(problemStatementRepository.save(statement));
    }

    public ProblemStatementResponse release(String problemId) {
        ProblemStatement statement = problemStatementRepository.findById(problemId)
                .orElseThrow(() -> new ResourceNotFoundException("Problem statement not found: " + problemId));

        statement.setReleased(true);
        statement.setReleasedAt(Instant.now());

        return toResponse(problemStatementRepository.save(statement));
    }

    public List<ProblemStatementResponse> listForFaculty(String eventId) {
        return problemStatementRepository.findByEventId(eventId).stream().map(this::toResponse).toList();
    }

    public List<ProblemStatementResponse> listReleasedForUsers(String eventId) {
        return problemStatementRepository.findByEventIdAndReleasedTrue(eventId).stream().map(this::toResponse).toList();
    }

    public ProblemStatement getReleasedProblemStatement(String problemId) {
        ProblemStatement statement = problemStatementRepository.findById(problemId)
                .orElseThrow(() -> new ResourceNotFoundException("Problem statement not found: " + problemId));

        if (!statement.isReleased()) {
            throw new ResourceNotFoundException("Problem statement is not released yet: " + problemId);
        }
        return statement;
    }

    private ProblemStatementResponse toResponse(ProblemStatement statement) {
        return new ProblemStatementResponse(
                statement.getId(),
                statement.getEventId(),
                statement.getTitle(),
                statement.getDescription(),
                statement.isReleased(),
                statement.getReleasedAt()
        );
    }
}
