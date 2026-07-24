package com.kickoffsim.service.impl;

import com.kickoffsim.dto.TeamDto;
import com.kickoffsim.exception.EntityNotFoundException;
import com.kickoffsim.exception.InvalidLeagueOperationException;
import com.kickoffsim.model.League;
import com.kickoffsim.model.Match;
import com.kickoffsim.model.Team;
import com.kickoffsim.repository.LeagueRepository;
import com.kickoffsim.repository.MatchRepository;
import com.kickoffsim.repository.PlayerRepository;
import com.kickoffsim.repository.TeamRepository;
import com.kickoffsim.scheduling.TeamCreatedEvent;
import com.kickoffsim.service.TeamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamServiceImpl implements TeamService {

    private static final Sort DEFAULT_SORT = Sort.by("league.name").and(Sort.by("name"));

    private static final int STRENGTH_MEAN = 60;
    private static final int STRENGTH_STDDEV = 20;
    private static final int STRENGTH_MIN = 20;
    private static final int STRENGTH_MAX = 95;

    private final TeamRepository teamRepository;
    private final LeagueRepository leagueRepository;
    private final MatchRepository matchRepository;
    private final PlayerRepository playerRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public List<TeamDto> findAll() {
        return findAll(DEFAULT_SORT);
    }

    @Override
    public List<TeamDto> findAll(Sort sort) {
        return teamRepository.findAll(sort).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public List<TeamDto> findAllFree() {
        return teamRepository.findAllByLeagueIsNull().stream()
                .map(t -> {
                    TeamDto dto = toDto(t);
                    dto.setPlayerCount(playerRepository.countByTeam(t));
                    return dto;
                })
                .toList();
    }

    @Override
    public List<TeamDto> findAllByLeague(UUID leagueId) {
        League league = getLeagueOrThrow(leagueId);
        return teamRepository.findAllByLeague(league).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public boolean existsByName(String name) {
        return teamRepository.existsByName(name);
    }

    @Override
    public boolean existsByNameAndCity(String name, String city) {
        return teamRepository.existsByNameAndCity(name, city);
    }

    @Override
    public TeamDto findById(UUID id) {
        return toDto(getTeamOrThrow(id));
    }

    @Override
    @Transactional
    public TeamDto create(TeamDto teamDto) {
        Team team = new Team();
        mapToEntity(teamDto, team);
        team.setStrength(rollStrength());
        Team saved = teamRepository.save(team);
        if (saved.getLeague() != null) {
            eventPublisher.publishEvent(new TeamCreatedEvent(saved.getLeague().getId()));
        }
        log.info("Created team '{}'", saved.getName());
        return toDto(saved);
    }

    @Override
    @Transactional
    public TeamDto update(UUID id, TeamDto teamDto) {
        Team team = getTeamOrThrow(id);
        teamDto.setName(team.getName());
        mapToEntity(teamDto, team);
        TeamDto saved = toDto(teamRepository.save(team));
        log.info("Updated team {}", id);
        return saved;
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Team team = getTeamOrThrow(id);
        if (team.getLeague() != null && matchRepository.existsByLeagueId(team.getLeague().getId())) {
            throw new InvalidLeagueOperationException(
                    "Cannot delete '%s' — it belongs to league '%s', which already has a generated schedule. Delete the league instead if you need to remove it."
                            .formatted(team.getName(), team.getLeague().getName()));
        }
        List<Match> matches = matchRepository.findAllByHomeTeamOrAwayTeam(team, team);
        matchRepository.deleteAll(matches);
        teamRepository.delete(team);
        log.info("Deleted team {} and {} related match(es)", id, matches.size());
    }

    private Team getTeamOrThrow(UUID id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Team with id %s not found".formatted(id)));
    }

    private League getLeagueOrThrow(UUID id) {
        return leagueRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("League with id %s not found".formatted(id)));
    }

    private void mapToEntity(TeamDto teamDto, Team team) {
        team.setName(teamDto.getName());
        team.setCity(teamDto.getCity());
        team.setLeague(teamDto.getLeagueId() != null ? getLeagueOrThrow(teamDto.getLeagueId()) : null);
    }

    private int rollStrength() {
        int value = (int) Math.round(STRENGTH_MEAN + ThreadLocalRandom.current().nextGaussian() * STRENGTH_STDDEV);
        return Math.max(STRENGTH_MIN, Math.min(STRENGTH_MAX, value));
    }

    private TeamDto toDto(Team team) {
        TeamDto teamDto = new TeamDto();
        teamDto.setId(team.getId());
        teamDto.setName(team.getName());
        teamDto.setCity(team.getCity());
        teamDto.setLeagueId(team.getLeague() != null ? team.getLeague().getId() : null);
        teamDto.setLeagueName(team.getLeague() != null ? team.getLeague().getName() : null);
        teamDto.setPlayerCount(playerRepository.countByTeam(team));
        teamDto.setStrength(team.getStrength());
        return teamDto;
    }
}
