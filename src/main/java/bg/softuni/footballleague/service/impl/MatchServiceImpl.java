package bg.softuni.footballleague.service.impl;

import bg.softuni.footballleague.dto.MatchDto;
import bg.softuni.footballleague.exception.EntityNotFoundException;
import bg.softuni.footballleague.exception.InvalidMatchException;
import bg.softuni.footballleague.model.Match;
import bg.softuni.footballleague.model.Team;
import bg.softuni.footballleague.repository.MatchRepository;
import bg.softuni.footballleague.repository.TeamRepository;
import bg.softuni.footballleague.service.MatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "playedAt");

    private final MatchRepository matchRepository;
    private final TeamRepository teamRepository;

    @Override
    public List<MatchDto> findAll() {
        return findAll(DEFAULT_SORT);
    }

    @Override
    public List<MatchDto> findAll(Sort sort) {
        return matchRepository.findAll(sort).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public List<MatchDto> findAllByTeam(UUID teamId) {
        Team team = getTeamOrThrow(teamId);
        return matchRepository.findAllByHomeTeamOrAwayTeam(team, team).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public MatchDto findById(UUID id) {
        return toDto(getMatchOrThrow(id));
    }

    @Override
    public MatchDto create(MatchDto matchDto) {
        Match match = new Match();
        mapToEntity(matchDto, match);
        return toDto(matchRepository.save(match));
    }

    @Override
    public MatchDto update(UUID id, MatchDto matchDto) {
        Match match = getMatchOrThrow(id);
        mapToEntity(matchDto, match);
        return toDto(matchRepository.save(match));
    }

    @Override
    public void delete(UUID id) {
        matchRepository.delete(getMatchOrThrow(id));
    }

    private Match getMatchOrThrow(UUID id) {
        return matchRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Match with id %s not found".formatted(id)));
    }

    private Team getTeamOrThrow(UUID id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Team with id %s not found".formatted(id)));
    }

    private void mapToEntity(MatchDto matchDto, Match match) {
        if (matchDto.getHomeTeamId().equals(matchDto.getAwayTeamId())) {
            throw new InvalidMatchException("Home team and away team must be different");
        }

        match.setHomeTeam(getTeamOrThrow(matchDto.getHomeTeamId()));
        match.setAwayTeam(getTeamOrThrow(matchDto.getAwayTeamId()));
        match.setHomeScore(matchDto.getHomeScore());
        match.setAwayScore(matchDto.getAwayScore());
        match.setPlayedAt(matchDto.getPlayedAt());
    }

    private MatchDto toDto(Match match) {
        MatchDto matchDto = new MatchDto();
        matchDto.setId(match.getId());
        if (match.getHomeTeam() != null) {
            matchDto.setHomeTeamId(match.getHomeTeam().getId());
            matchDto.setHomeTeamName(match.getHomeTeam().getName());
        }
        if (match.getAwayTeam() != null) {
            matchDto.setAwayTeamId(match.getAwayTeam().getId());
            matchDto.setAwayTeamName(match.getAwayTeam().getName());
        }
        matchDto.setHomeScore(match.getHomeScore());
        matchDto.setAwayScore(match.getAwayScore());
        matchDto.setPlayedAt(match.getPlayedAt());
        return matchDto;
    }
}
