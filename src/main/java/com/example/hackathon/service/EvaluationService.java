package com.example.hackathon.service;

import com.example.hackathon.dto.CriterionScoreInput;
import com.example.hackathon.dto.EvaluateTeamRequest;
import com.example.hackathon.dto.EvaluationRecordResponse;
import com.example.hackathon.exception.BadRequestException;
import com.example.hackathon.model.Criterion;
import com.example.hackathon.model.Evaluation;
import com.example.hackathon.model.Team;
import com.example.hackathon.repository.EvaluationRepository;
import com.example.hackathon.repository.TeamRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class EvaluationService {

    private final EvaluationRepository evaluationRepository;
    private final TeamService teamService;
    private final TeamRepository teamRepository;
    private final CriterionService criterionService;

    public EvaluationService(EvaluationRepository evaluationRepository,
                             TeamService teamService,
                             TeamRepository teamRepository,
                             CriterionService criterionService) {
        this.evaluationRepository = evaluationRepository;
        this.teamService = teamService;
        this.teamRepository = teamRepository;
        this.criterionService = criterionService;
    }

    public List<EvaluationRecordResponse> evaluateTeam(EvaluateTeamRequest request, String facultyEmail) {
        Team team = teamService.getTeamEntity(request.teamId());
        if (!team.getEventId().equals(request.eventId())) {
            throw new BadRequestException("Team does not belong to requested event");
        }

        validateNoDuplicateCriteriaInRequest(request.scores());

        for (CriterionScoreInput scoreInput : request.scores()) {
            createEvaluationRecordOnce(
                    request.eventId(),
                    request.teamId(),
                    request.description(),
                    scoreInput,
                    facultyEmail
            );
        }

        List<Evaluation> teamEvaluations = evaluationRepository.findByEventIdAndTeamId(request.eventId(), request.teamId());
        double totalScore = teamEvaluations.stream().mapToDouble(Evaluation::getMarksGiven).sum();

        team.setTotalScore(totalScore);
        teamRepository.save(team);

        teamEvaluations.forEach(evaluation -> evaluation.setTotalScore(totalScore));
        evaluationRepository.saveAll(teamEvaluations);

        return teamEvaluations.stream().map(this::toResponse).toList();
    }

    public List<EvaluationRecordResponse> getTeamEvaluations(String eventId, String teamId) {
        return evaluationRepository.findByEventIdAndTeamId(eventId, teamId).stream().map(this::toResponse).toList();
    }

    public List<EvaluationRecordResponse> getEventEvaluations(String eventId) {
        return evaluationRepository.findByEventId(eventId).stream().map(this::toResponse).toList();
    }

    private void createEvaluationRecordOnce(String eventId,
                                            String teamId,
                                            String description,
                                            CriterionScoreInput scoreInput,
                                            String facultyEmail) {
        Criterion criterion = criterionService.getCriterionEntity(scoreInput.criterionId());
        if (!criterion.getEventId().equals(eventId)) {
            throw new BadRequestException("Criterion does not belong to event");
        }
        if (scoreInput.marksGiven() > criterion.getMaxMarks()) {
            throw new BadRequestException("Marks cannot exceed max marks for criterion: " + criterion.getName());
        }
        if (evaluationRepository.findByEventIdAndTeamIdAndCriterionId(eventId, teamId, criterion.getId()).isPresent()) {
            throw new BadRequestException("Marks for criterion '" + criterion.getName() + "' are already submitted and cannot be edited");
        }

        Evaluation evaluation = new Evaluation();
        evaluation.setEventId(eventId);
        evaluation.setTeamId(teamId);
        evaluation.setCriterionId(criterion.getId());
        evaluation.setCriterionName(criterion.getName());
        evaluation.setMarksGiven(scoreInput.marksGiven());
        evaluation.setMaxMarks(criterion.getMaxMarks());
        evaluation.setEvaluatedBy(facultyEmail);
        evaluation.setDescription(description);
        evaluation.setEvaluatedAt(Instant.now());

        evaluationRepository.save(evaluation);
    }

    private void validateNoDuplicateCriteriaInRequest(List<CriterionScoreInput> scores) {
        Set<String> criteriaIds = new HashSet<>();
        for (CriterionScoreInput score : scores) {
            if (!criteriaIds.add(score.criterionId())) {
                throw new BadRequestException("Each criterion can be scored only once per submission");
            }
        }
    }

    private EvaluationRecordResponse toResponse(Evaluation evaluation) {
        return new EvaluationRecordResponse(
                evaluation.getId(),
                evaluation.getTeamId(),
                evaluation.getCriterionId(),
                evaluation.getCriterionName(),
                evaluation.getMarksGiven(),
                evaluation.getMaxMarks(),
                evaluation.getEvaluatedBy(),
                evaluation.getDescription(),
                evaluation.getTotalScore(),
                evaluation.getEvaluatedAt()
        );
    }
}
