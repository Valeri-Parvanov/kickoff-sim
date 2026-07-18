package bg.softuni.footballleague.service.impl;

import bg.softuni.footballleague.dto.TeamDto;
import bg.softuni.footballleague.exception.EntityNotFoundException;
import bg.softuni.footballleague.model.League;
import bg.softuni.footballleague.model.Match;
import bg.softuni.footballleague.model.Team;
import bg.softuni.footballleague.repository.LeagueRepository;
import bg.softuni.footballleague.repository.MatchRepository;
import bg.softuni.footballleague.repository.PlayerRepository;
import bg.softuni.footballleague.repository.TeamRepository;
import bg.softuni.footballleague.scheduling.TeamCreatedEvent;
import bg.softuni.footballleague.service.TeamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamServiceImpl implements TeamService {

    private static final Sort DEFAULT_SORT = Sort.by("league.name").and(Sort.by("name"));

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
        mapToEntity(teamDto, team);
        TeamDto saved = toDto(teamRepository.save(team));
        log.info("Updated team {}", id);
        return saved;
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Team team = getTeamOrThrow(id);
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

    private TeamDto toDto(Team team) {
        TeamDto teamDto = new TeamDto();
        teamDto.setId(team.getId());
        teamDto.setName(team.getName());
        teamDto.setCity(team.getCity());
        teamDto.setLeagueId(team.getLeague() != null ? team.getLeague().getId() : null);
        teamDto.setLeagueName(team.getLeague() != null ? team.getLeague().getName() : null);
        teamDto.setPlayerCount(playerRepository.countByTeam(team));
        return teamDto;
    }
}
