package com.example.hackathon.service;

import com.example.hackathon.dto.CriterionRequest;
import com.example.hackathon.dto.CriterionResponse;
import com.example.hackathon.model.Criterion;
import com.example.hackathon.repository.CriterionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CriterionService {

    private final CriterionRepository criterionRepository;
    private final EventService eventService;

    public CriterionService(CriterionRepository criterionRepository, EventService eventService) {
        this.criterionRepository = criterionRepository;
        this.eventService = eventService;
    }

    public CriterionResponse createCriterion(CriterionRequest request, String facultyEmail) {
        eventService.getEventEntity(request.eventId());

        Criterion criterion = new Criterion();
        criterion.setEventId(request.eventId());
        criterion.setName(request.name());
        criterion.setMaxMarks(request.maxMarks());
        criterion.setCreatedBy(facultyEmail);

        return toResponse(criterionRepository.save(criterion));
    }

    public List<CriterionResponse> listCriteria(String eventId) {
        return criterionRepository.findByEventId(eventId).stream().map(this::toResponse).toList();
    }

    public Criterion getCriterionEntity(String criterionId) {
        return criterionRepository.findById(criterionId)
                .orElseThrow(() -> new com.example.hackathon.exception.ResourceNotFoundException("Criterion not found: " + criterionId));
    }

    private CriterionResponse toResponse(Criterion criterion) {
        return new CriterionResponse(
                criterion.getId(),
                criterion.getEventId(),
                criterion.getName(),
                criterion.getMaxMarks()
        );
    }
}
