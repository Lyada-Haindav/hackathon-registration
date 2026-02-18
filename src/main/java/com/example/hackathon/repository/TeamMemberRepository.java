package com.example.hackathon.repository;

import com.example.hackathon.model.TeamMember;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface TeamMemberRepository extends MongoRepository<TeamMember, String> {
    List<TeamMember> findByTeamId(String teamId);

    List<TeamMember> findByTeamIdIn(Collection<String> teamIds);

    void deleteByTeamId(String teamId);
}
