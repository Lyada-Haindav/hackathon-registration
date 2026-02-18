package com.example.hackathon.controller;

import com.example.hackathon.dto.*;
import com.example.hackathon.model.RegistrationForm;
import com.example.hackathon.service.*;
import com.example.hackathon.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/faculty")
public class FacultyController {

    private final EventService eventService;
    private final FormService formService;
    private final TeamService teamService;
    private final ProblemStatementService problemStatementService;
    private final CriterionService criterionService;
    private final EvaluationService evaluationService;
    private final DeploymentReadinessService deploymentReadinessService;

    public FacultyController(EventService eventService,
                             FormService formService,
                             TeamService teamService,
                             ProblemStatementService problemStatementService,
                             CriterionService criterionService,
                             EvaluationService evaluationService,
                             DeploymentReadinessService deploymentReadinessService) {
        this.eventService = eventService;
        this.formService = formService;
        this.teamService = teamService;
        this.problemStatementService = problemStatementService;
        this.criterionService = criterionService;
        this.evaluationService = evaluationService;
        this.deploymentReadinessService = deploymentReadinessService;
    }

    @PostMapping("/events")
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request) {
        EventResponse response = eventService.createEvent(request, SecurityUtil.currentUsername());
        formService.ensureDefaultFormForEvent(response.id(), SecurityUtil.currentUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/events/{eventId}")
    public ResponseEntity<EventResponse> updateEvent(@PathVariable String eventId,
                                                     @Valid @RequestBody CreateEventRequest request) {
        return ResponseEntity.ok(eventService.updateEvent(eventId, request));
    }

    @GetMapping("/events")
    public ResponseEntity<List<EventResponse>> listEvents() {
        return ResponseEntity.ok(eventService.getAllEvents());
    }

    @PutMapping("/events/{eventId}/leaderboard-visibility")
    public ResponseEntity<EventResponse> setLeaderboardVisibility(@PathVariable String eventId,
                                                                  @RequestBody LeaderboardVisibilityRequest request) {
        return ResponseEntity.ok(eventService.setLeaderboardVisibility(eventId, request.visible()));
    }

    @PutMapping("/events/{eventId}/hold")
    public ResponseEntity<EventResponse> holdEvent(@PathVariable String eventId) {
        return ResponseEntity.ok(eventService.holdEvent(eventId));
    }

    @PutMapping("/events/{eventId}/resume")
    public ResponseEntity<EventResponse> resumeEvent(@PathVariable String eventId) {
        return ResponseEntity.ok(eventService.resumeEvent(eventId));
    }

    @PutMapping("/events/{eventId}/open-registration")
    public ResponseEntity<EventResponse> openRegistrationNow(@PathVariable String eventId) {
        return ResponseEntity.ok(eventService.openRegistrationNow(eventId));
    }

    @PutMapping("/events/{eventId}/close-registration")
    public ResponseEntity<EventResponse> closeRegistrationNow(@PathVariable String eventId) {
        return ResponseEntity.ok(eventService.closeRegistrationNow(eventId));
    }

    @DeleteMapping("/events/{eventId}")
    public ResponseEntity<MessageResponse> deleteEvent(@PathVariable String eventId) {
        eventService.deleteEvent(eventId);
        return ResponseEntity.ok(new MessageResponse("Event deleted successfully"));
    }

    @PostMapping("/forms")
    public ResponseEntity<RegistrationForm> createOrUpdateForm(@Valid @RequestBody CreateFormRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(formService.createOrUpdateForm(request, SecurityUtil.currentUsername()));
    }

    @GetMapping("/forms/{eventId}")
    public ResponseEntity<RegistrationForm> getForm(@PathVariable String eventId) {
        return ResponseEntity.ok(formService.getFormByEventId(eventId));
    }

    @GetMapping("/events/{eventId}/teams")
    public ResponseEntity<List<TeamResponse>> getTeams(@PathVariable String eventId) {
        return ResponseEntity.ok(teamService.getTeamsByEvent(eventId));
    }

    @PostMapping("/problem-statements")
    public ResponseEntity<ProblemStatementResponse> createProblem(@Valid @RequestBody ProblemStatementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(problemStatementService.create(request, SecurityUtil.currentUsername()));
    }

    @PutMapping("/problem-statements/{problemId}/release")
    public ResponseEntity<ProblemStatementResponse> releaseProblem(@PathVariable String problemId) {
        return ResponseEntity.ok(problemStatementService.release(problemId));
    }

    @GetMapping("/problem-statements/{eventId}")
    public ResponseEntity<List<ProblemStatementResponse>> listProblems(@PathVariable String eventId) {
        return ResponseEntity.ok(problemStatementService.listForFaculty(eventId));
    }

    @PostMapping("/criteria")
    public ResponseEntity<CriterionResponse> createCriterion(@Valid @RequestBody CriterionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(criterionService.createCriterion(request, SecurityUtil.currentUsername()));
    }

    @GetMapping("/criteria/{eventId}")
    public ResponseEntity<List<CriterionResponse>> listCriteria(@PathVariable String eventId) {
        return ResponseEntity.ok(criterionService.listCriteria(eventId));
    }

    @PostMapping("/evaluations")
    public ResponseEntity<List<EvaluationRecordResponse>> evaluateTeam(@Valid @RequestBody EvaluateTeamRequest request) {
        return ResponseEntity.ok(evaluationService.evaluateTeam(request, SecurityUtil.currentUsername()));
    }

    @GetMapping("/evaluations/{eventId}/{teamId}")
    public ResponseEntity<List<EvaluationRecordResponse>> getTeamEvaluations(@PathVariable String eventId,
                                                                              @PathVariable String teamId) {
        return ResponseEntity.ok(evaluationService.getTeamEvaluations(eventId, teamId));
    }

    @GetMapping("/evaluations/{eventId}")
    public ResponseEntity<List<EvaluationRecordResponse>> getEventEvaluations(@PathVariable String eventId) {
        return ResponseEntity.ok(evaluationService.getEventEvaluations(eventId));
    }

    @GetMapping("/deployment/readiness")
    public ResponseEntity<DeploymentReadinessResponse> getDeploymentReadiness() {
        return ResponseEntity.ok(deploymentReadinessService.checkReadiness());
    }
}
