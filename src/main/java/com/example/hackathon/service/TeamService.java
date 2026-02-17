package com.example.hackathon.service;

import com.example.hackathon.dto.TeamMemberRequest;
import com.example.hackathon.dto.TeamMemberResponse;
import com.example.hackathon.dto.TeamRegistrationRequest;
import com.example.hackathon.dto.TeamResponse;
import com.example.hackathon.exception.BadRequestException;
import com.example.hackathon.exception.ResourceNotFoundException;
import com.example.hackathon.model.HackathonEvent;
import com.example.hackathon.model.ProblemStatement;
import com.example.hackathon.model.Team;
import com.example.hackathon.model.TeamMember;
import com.example.hackathon.model.User;
import com.example.hackathon.repository.TeamMemberRepository;
import com.example.hackathon.repository.TeamRepository;
import com.example.hackathon.util.TeamNameGenerator;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TeamService {

    private static final int MIN_TEAM_MEMBERS = 1;
    private static final int MAX_TEAM_MEMBERS = 4;

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserService userService;
    private final EventService eventService;
    private final FormService formService;
    private final ProblemStatementService problemStatementService;
    private final TeamNameGenerator teamNameGenerator;

    public TeamService(TeamRepository teamRepository,
                       TeamMemberRepository teamMemberRepository,
                       UserService userService,
                       EventService eventService,
                       FormService formService,
                       ProblemStatementService problemStatementService,
                       TeamNameGenerator teamNameGenerator) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.userService = userService;
        this.eventService = eventService;
        this.formService = formService;
        this.problemStatementService = problemStatementService;
        this.teamNameGenerator = teamNameGenerator;
    }

    public TeamResponse registerTeam(String userEmail, TeamRegistrationRequest request) {
        User user = userService.findByEmail(userEmail);
        HackathonEvent event = eventService.getEventEntity(request.eventId());
        eventService.validateRegistrationWindow(event);

        if (!teamRepository.findByUserId(user.getId()).isEmpty()) {
            throw new BadRequestException("User can register only one team in the system");
        }

        teamRepository.findByEventIdAndUserId(request.eventId(), user.getId())
                .ifPresent(team -> {
                    throw new BadRequestException("Team already registered for this event");
                });

        validateMembers(request.members());
        formService.validateFormResponse(request.eventId(), request.formResponses());

        Team team = new Team();
        team.setEventId(request.eventId());
        team.setUserId(user.getId());
        team.setTeamName(teamNameGenerator.generateUniqueName());
        team.setTeamSize(request.members().size());
        team.setFormResponses(request.formResponses());

        Team savedTeam = teamRepository.save(team);

        List<TeamMember> members = request.members().stream().map(member -> toEntity(member, savedTeam.getId())).toList();
        teamMemberRepository.saveAll(members);

        return toTeamResponse(savedTeam);
    }

    public TeamResponse getUserTeamByEvent(String userEmail, String eventId) {
        User user = userService.findByEmail(userEmail);
        Team team = teamRepository.findByEventIdAndUserId(eventId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Team not found for event: " + eventId));
        return toTeamResponse(team);
    }

    public List<TeamResponse> getUserTeams(String userEmail) {
        User user = userService.findByEmail(userEmail);
        return teamRepository.findByUserId(user.getId()).stream().map(this::toTeamResponse).toList();
    }

    public List<TeamResponse> getTeamsByEvent(String eventId) {
        eventService.getEventEntity(eventId);
        return teamRepository.findByEventId(eventId).stream().map(this::toTeamResponse).toList();
    }

    public Team getTeamEntity(String teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
    }

    public Team getTeamEntityForUser(String teamId, String userEmail) {
        User user = userService.findByEmail(userEmail);
        Team team = getTeamEntity(teamId);
        if (!team.getUserId().equals(user.getId())) {
            throw new BadRequestException("Team does not belong to current user");
        }
        return team;
    }

    public TeamResponse selectProblemStatement(String userEmail, String teamId, String problemStatementId) {
        Team team = getTeamEntityForUser(teamId, userEmail);
        ProblemStatement statement = problemStatementService.getReleasedProblemStatement(problemStatementId);

        if (!team.getEventId().equals(statement.getEventId())) {
            throw new BadRequestException("Selected problem statement does not belong to the team's event");
        }
        if (team.getSelectedProblemStatementId() != null && !team.getSelectedProblemStatementId().isBlank()) {
            throw new BadRequestException("Problem statement can be selected only once");
        }

        team.setSelectedProblemStatementId(statement.getId());
        team.setSelectedProblemStatementTitle(statement.getTitle());
        Team saved = teamRepository.save(team);
        return toTeamResponse(saved);
    }

    public TeamResponse toTeamResponse(Team team) {
        List<TeamMemberResponse> members = teamMemberRepository.findByTeamId(team.getId()).stream()
                .map(member -> new TeamMemberResponse(
                        member.getId(),
                        member.getName(),
                        member.getEmail(),
                        member.getPhone(),
                        member.getCollege(),
                        member.isLeader()
                ))
                .toList();

        return new TeamResponse(
                team.getId(),
                team.getEventId(),
                team.getTeamName(),
                team.getTeamSize(),
                team.getPaymentStatus(),
                team.getTotalScore(),
                team.getRazorpayOrderId(),
                team.getSelectedProblemStatementId(),
                team.getSelectedProblemStatementTitle(),
                members,
                team.getFormResponses()
        );
    }

    private TeamMember toEntity(TeamMemberRequest memberRequest, String teamId) {
        TeamMember member = new TeamMember();
        member.setTeamId(teamId);
        member.setName(memberRequest.name());
        member.setEmail(memberRequest.email());
        member.setPhone(memberRequest.phone());
        member.setCollege(memberRequest.college());
        member.setLeader(memberRequest.leader());
        return member;
    }

    private void validateMembers(List<TeamMemberRequest> members) {
        int memberCount = members == null ? 0 : members.size();
        if (memberCount < MIN_TEAM_MEMBERS || memberCount > MAX_TEAM_MEMBERS) {
            throw new BadRequestException("Team size must be between 1 and 4 members");
        }

        long leaders = members.stream().filter(TeamMemberRequest::leader).count();
        if (leaders != 1) {
            throw new BadRequestException("Exactly one team member must be marked as leader");
        }
    }
}
