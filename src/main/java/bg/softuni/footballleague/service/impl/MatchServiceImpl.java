package bg.softuni.footballleague.service.impl;

import bg.softuni.footballleague.dto.MatchDto;
import bg.softuni.footballleague.exception.EntityNotFoundException;
import bg.softuni.footballleague.model.Match;
import bg.softuni.footballleague.model.Team;
import bg.softuni.footballleague.repository.MatchRepository;
import bg.softuni.footballleague.repository.TeamRepository;
import bg.softuni.footballleague.service.MatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService {

    private final MatchRepository matchRepository;
    private final TeamRepository teamRepository;

    @Override
    public List<MatchDto> findAll() {
        return matchRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public List<MatchDto> findAllByTeam(Long teamId) {
        Team team = getTeamOrThrow(teamId);
        return matchRepository.findAllByHomeTeamOrAwayTeam(team, team).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public MatchDto findById(Long id) {
        return toDto(getMatchOrThrow(id));
    }

    @Override
    public MatchDto create(MatchDto matchDto) {
        Match match = new Match();
        mapToEntity(matchDto, match);
        return toDto(matchRepository.save(match));
    }

    @Override
    public MatchDto update(Long id, MatchDto matchDto) {
        Match match = getMatchOrThrow(id);
        mapToEntity(matchDto, match);
        return toDto(matchRepository.save(match));
    }

    @Override
    public void delete(Long id) {
        matchRepository.delete(getMatchOrThrow(id));
    }

    private Match getMatchOrThrow(Long id) {
        return matchRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Match with id %d not found".formatted(id)));
    }

    private Team getTeamOrThrow(Long id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Team with id %d not found".formatted(id)));
    }

    private void mapToEntity(MatchDto matchDto, Match match) {
        match.setHomeTeam(getTeamOrThrow(matchDto.getHomeTeamId()));
        match.setAwayTeam(getTeamOrThrow(matchDto.getAwayTeamId()));
        match.setHomeScore(matchDto.getHomeScore());
        match.setAwayScore(matchDto.getAwayScore());
        match.setPlayedAt(matchDto.getPlayedAt());
    }

    private MatchDto toDto(Match match) {
        MatchDto matchDto = new MatchDto();
        matchDto.setId(match.getId());
        matchDto.setHomeTeamId(match.getHomeTeam() != null ? match.getHomeTeam().getId() : null);
        matchDto.setAwayTeamId(match.getAwayTeam() != null ? match.getAwayTeam().getId() : null);
        matchDto.setHomeScore(match.getHomeScore());
        matchDto.setAwayScore(match.getAwayScore());
        matchDto.setPlayedAt(match.getPlayedAt());
        return matchDto;
    }
}
