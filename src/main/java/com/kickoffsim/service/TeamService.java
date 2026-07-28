package com.kickoffsim.service;

import com.kickoffsim.dto.TeamDto;
import org.springframework.data.domain.Sort;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TeamService {

    List<TeamDto> findAll();

    List<TeamDto> findAll(Sort sort);

    List<TeamDto> findAllByLeague(UUID leagueId);

    List<TeamDto> findAllFree();

    List<TeamDto> searchByName(String q);

    boolean existsByName(String name);

    boolean existsByNameAndCity(String name, String city);

    TeamDto findById(UUID id);

    List<TeamDto> findAllByIds(Collection<UUID> ids);

    TeamDto create(TeamDto teamDto);

    TeamDto update(UUID id, TeamDto teamDto);

    void delete(UUID id);
}
