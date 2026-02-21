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
import com.mongodb.client.result.UpdateResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TeamService {

    private static final int MIN_TEAM_MEMBERS = 1;
    private static final int MAX_TEAM_MEMBERS = 4;
    private static final int TEAM_NAME_GENERATION_MAX_ATTEMPTS = 25;

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserService userService;
    private final EventService eventService;
    private final FormService formService;
    private final ProblemStatementService problemStatementService;
    private final TeamNameGenerator teamNameGenerator;
    private final MongoTemplate mongoTemplate;

    public TeamService(TeamRepository teamRepository,
                       TeamMemberRepository teamMemberRepository,
                       UserService userService,
                       EventService eventService,
                       FormService formService,
                       ProblemStatementService problemStatementService,
                       TeamNameGenerator teamNameGenerator,
                       MongoTemplate mongoTemplate) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.userService = userService;
        this.eventService = eventService;
        this.formService = formService;
        this.problemStatementService = problemStatementService;
        this.teamNameGenerator = teamNameGenerator;
        this.mongoTemplate = mongoTemplate;
    }

    public TeamResponse registerTeam(String userEmail, TeamRegistrationRequest request) {
        User user = userService.findByEmail(userEmail);
        HackathonEvent event = eventService.getEventEntity(request.eventId());
        eventService.validateRegistrationWindow(event);

        validateMembers(request.members());
        formService.validateFormResponse(request.eventId(), request.formResponses());

        Team team = new Team();
        team.setEventId(request.eventId());
        team.setUserId(user.getId());
        team.setTeamSize(request.members().size());
        team.setFormResponses(request.formResponses());

        Team savedTeam = saveTeamWithUniqueName(team);

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
        List<Team> teams = teamRepository.findByUserId(user.getId());
        return toTeamResponses(teams);
    }

    public List<TeamResponse> getTeamsByEvent(String eventId) {
        eventService.getEventEntity(eventId);
        List<Team> teams = teamRepository.findByEventId(eventId);
        return toTeamResponses(teams);
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

        Query updateAllowed = new Query(new Criteria().andOperator(
                Criteria.where("_id").is(team.getId()),
                new Criteria().orOperator(
                        Criteria.where("selectedProblemStatementId").exists(false),
                        Criteria.where("selectedProblemStatementId").is(null),
                        Criteria.where("selectedProblemStatementId").is("")
                )
        ));

        Update setProblem = new Update()
                .set("selectedProblemStatementId", statement.getId())
                .set("selectedProblemStatementTitle", statement.getTitle());

        UpdateResult result = mongoTemplate.updateFirst(updateAllowed, setProblem, Team.class);
        if (result.getModifiedCount() == 0) {
            throw new BadRequestException("Problem statement can be selected only once");
        }

        Team saved = getTeamEntity(team.getId());
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

    private List<TeamResponse> toTeamResponses(List<Team> teams) {
        if (teams == null || teams.isEmpty()) {
            return List.of();
        }

        List<String> teamIds = teams.stream().map(Team::getId).toList();
        Map<String, List<TeamMemberResponse>> membersByTeamId = new HashMap<>();

        for (TeamMember member : teamMemberRepository.findByTeamIdIn(teamIds)) {
            List<TeamMemberResponse> teamMembers = membersByTeamId.computeIfAbsent(member.getTeamId(), key -> new ArrayList<>());
            teamMembers.add(new TeamMemberResponse(
                    member.getId(),
                    member.getName(),
                    member.getEmail(),
                    member.getPhone(),
                    member.getCollege(),
                    member.isLeader()
            ));
        }

        return teams.stream()
                .map(team -> new TeamResponse(
                        team.getId(),
                        team.getEventId(),
                        team.getTeamName(),
                        team.getTeamSize(),
                        team.getPaymentStatus(),
                        team.getTotalScore(),
                        team.getRazorpayOrderId(),
                        team.getSelectedProblemStatementId(),
                        team.getSelectedProblemStatementTitle(),
                        membersByTeamId.getOrDefault(team.getId(), List.of()),
                        team.getFormResponses()
                ))
                .toList();
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

    private Team saveTeamWithUniqueName(Team team) {
        for (int attempt = 0; attempt < TEAM_NAME_GENERATION_MAX_ATTEMPTS; attempt++) {
            String candidate = teamNameGenerator.generateUniqueName();
            team.setTeamName(candidate);
            try {
                return teamRepository.save(team);
            } catch (DuplicateKeyException ex) {
                String normalizedMessage = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
                if (normalizedMessage.contains("teamname")) {
                    continue;
                }
                if (normalizedMessage.contains("userid") || normalizedMessage.contains("event_user_unique")) {
                    throw new BadRequestException("User can register only one team in the system");
                }
                throw ex;
            }
        }

        throw new BadRequestException("Unable to generate a unique team name. Please retry.");
    }
}
