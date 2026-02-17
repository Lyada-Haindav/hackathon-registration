package com.example.hackathon.service;

import com.example.hackathon.dto.LeaderboardEntryResponse;
import com.example.hackathon.exception.BadRequestException;
import com.example.hackathon.model.Team;
import com.example.hackathon.repository.TeamRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class LeaderboardService {

    private final TeamRepository teamRepository;
    private final EventService eventService;

    public LeaderboardService(TeamRepository teamRepository, EventService eventService) {
        this.teamRepository = teamRepository;
        this.eventService = eventService;
    }

    public List<LeaderboardEntryResponse> getLeaderboard(String eventId, boolean facultyViewer) {
        if (!facultyViewer && !eventService.getEventEntity(eventId).isLeaderboardVisible()) {
            throw new BadRequestException("Leaderboard is not published by faculty for this event yet");
        }

        List<Team> sortedTeams = teamRepository.findByEventId(eventId).stream()
                .sorted(Comparator.comparingDouble(Team::getTotalScore).reversed()
                        .thenComparing(Team::getTeamName))
                .toList();

        List<LeaderboardEntryResponse> response = new ArrayList<>();
        for (int i = 0; i < sortedTeams.size(); i++) {
            Team team = sortedTeams.get(i);
            response.add(new LeaderboardEntryResponse(i + 1, team.getId(), team.getTeamName(), team.getTotalScore()));
        }
        return response;
    }
}
