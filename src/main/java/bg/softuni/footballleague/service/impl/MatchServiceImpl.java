package bg.softuni.footballleague.service.impl;

import bg.softuni.footballleague.dto.GoalDto;
import bg.softuni.footballleague.dto.GoalEventDto;
import bg.softuni.footballleague.dto.MatchDto;
import bg.softuni.footballleague.exception.EntityNotFoundException;
import bg.softuni.footballleague.exception.InvalidGoalException;
import bg.softuni.footballleague.exception.InvalidMatchException;
import bg.softuni.footballleague.model.Goal;
import bg.softuni.footballleague.model.Half;
import bg.softuni.footballleague.model.Match;
import bg.softuni.footballleague.model.Player;
import bg.softuni.footballleague.model.Team;
import bg.softuni.footballleague.repository.GoalRepository;
import bg.softuni.footballleague.repository.MatchRepository;
import bg.softuni.footballleague.repository.PlayerRepository;
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
    private final GoalRepository goalRepository;
    private final PlayerRepository playerRepository;

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

    @Override
    public void addGoal(UUID matchId, GoalEventDto dto) {
        Match match = getMatchOrThrow(matchId);
        Player scorer = getPlayerOrThrow(dto.getScorerId());

        UUID scorerTeamId = scorer.getTeam().getId();
        boolean scorerBelongsToMatch = scorerTeamId.equals(match.getHomeTeam().getId())
                || scorerTeamId.equals(match.getAwayTeam().getId());
        if (!scorerBelongsToMatch) {
            throw new InvalidGoalException(
                    "Scorer " + scorer.getFirstName() + " " + scorer.getLastName()
                    + " does not play in this match.");
        }

        Goal goal = new Goal();
        Integer rawMinute = dto.getMinute();
        Half half = (rawMinute == null || rawMinute <= 20) ? Half.FIRST : Half.SECOND;
        Integer halfMinute = (rawMinute != null && rawMinute > 20) ? rawMinute - 20 : rawMinute;

        goal.setMatch(match);
        goal.setScorer(scorer);
        goal.setHalf(half);
        goal.setMinute(halfMinute);

        if (dto.getAssistantId() != null) {
            goal.setAssistant(getPlayerOrThrow(dto.getAssistantId()));
        }

        goalRepository.save(goal);
        recalculateAndSaveScores(match);
    }

    @Override
    public GoalDto findGoalById(UUID goalId) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new EntityNotFoundException("Goal not found"));
        return toGoalDto(goal);
    }

    @Override
    public void updateGoal(UUID goalId, GoalEventDto dto) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new EntityNotFoundException("Goal not found"));

        Player scorer = getPlayerOrThrow(dto.getScorerId());

        UUID scorerTeamId = scorer.getTeam().getId();
        boolean scorerBelongsToMatch = scorerTeamId.equals(goal.getMatch().getHomeTeam().getId())
                || scorerTeamId.equals(goal.getMatch().getAwayTeam().getId());
        if (!scorerBelongsToMatch) {
            throw new InvalidGoalException(
                    "Scorer " + scorer.getFirstName() + " " + scorer.getLastName()
                    + " does not play in this match.");
        }

        Integer rawMinute = dto.getMinute();
        Half half = (rawMinute == null || rawMinute <= 20) ? Half.FIRST : Half.SECOND;
        Integer halfMinute = (rawMinute != null && rawMinute > 20) ? rawMinute - 20 : rawMinute;

        goal.setScorer(scorer);
        goal.setHalf(half);
        goal.setMinute(halfMinute);
        goal.setAssistant(dto.getAssistantId() != null ? getPlayerOrThrow(dto.getAssistantId()) : null);

        goalRepository.save(goal);
        recalculateAndSaveScores(goal.getMatch());
    }

    @Override
    public void deleteGoal(UUID goalId) {
        goalRepository.findById(goalId).ifPresent(goal -> {
            Match match = goal.getMatch();
            goalRepository.delete(goal);
            recalculateAndSaveScores(match);
        });
    }

    private Player getPlayerOrThrow(UUID id) {
        return playerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Player with id %s not found".formatted(id)));
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
        match.setPlayedAt(matchDto.getPlayedAt());
    }

    private MatchDto toDto(Match match) {
        MatchDto matchDto = new MatchDto();
        matchDto.setId(match.getId());

        UUID homeTeamId = null;
        UUID awayTeamId = null;
        if (match.getHomeTeam() != null) {
            homeTeamId = match.getHomeTeam().getId();
            matchDto.setHomeTeamId(homeTeamId);
            matchDto.setHomeTeamName(match.getHomeTeam().getName());
        }
        if (match.getAwayTeam() != null) {
            awayTeamId = match.getAwayTeam().getId();
            matchDto.setAwayTeamId(awayTeamId);
            matchDto.setAwayTeamName(match.getAwayTeam().getName());
        }
        matchDto.setHomeScore(match.getHomeScore());
        matchDto.setAwayScore(match.getAwayScore());
        matchDto.setPlayedAt(match.getPlayedAt());

        List<Goal> goals = goalRepository.findAllByMatchOrderByHalfAscMinuteAsc(match);
        int homeTotal = 0, awayTotal = 0, homeHalf = 0, awayHalf = 0;
        for (Goal g : goals) {
            GoalDto dto = toGoalDto(g);
            boolean isHome = dto.getTeamId() != null && dto.getTeamId().equals(homeTeamId);
            boolean isFirst = g.getHalf() == Half.FIRST;

            if (isHome) homeTotal++; else awayTotal++;

            if (isFirst) {
                if (isHome) { matchDto.getFirstHalfHomeGoals().add(dto); homeHalf++; }
                else        { matchDto.getFirstHalfAwayGoals().add(dto); awayHalf++; }
            } else {
                if (isHome) matchDto.getSecondHalfHomeGoals().add(dto);
                else        matchDto.getSecondHalfAwayGoals().add(dto);
            }
        }
        matchDto.setHomeScore(homeTotal);
        matchDto.setAwayScore(awayTotal);
        if (!goals.isEmpty()) {
            matchDto.setHomeHalfScore(homeHalf);
            matchDto.setAwayHalfScore(awayHalf);
        }

        return matchDto;
    }

    private void recalculateAndSaveScores(Match match) {
        List<Goal> goals = goalRepository.findAllByMatchOrderByHalfAscMinuteAsc(match);
        UUID homeId = match.getHomeTeam().getId();
        int home = 0, away = 0;
        for (Goal g : goals) {
            if (g.getScorer() != null && g.getScorer().getTeam().getId().equals(homeId)) home++;
            else away++;
        }
        match.setHomeScore(home);
        match.setAwayScore(away);
        matchRepository.save(match);
    }

    private GoalDto toGoalDto(Goal goal) {
        GoalDto dto = new GoalDto();
        dto.setId(goal.getId());
        dto.setHalf(goal.getHalf());
        dto.setMinute(goal.getMinute());
        if (goal.getScorer() != null) {
            dto.setScorerId(goal.getScorer().getId());
            dto.setScorerName(goal.getScorer().getFirstName() + " " + goal.getScorer().getLastName());
            dto.setTeamId(goal.getScorer().getTeam().getId());
        }
        if (goal.getAssistant() != null) {
            dto.setAssistantId(goal.getAssistant().getId());
            dto.setAssistantName(goal.getAssistant().getFirstName() + " " + goal.getAssistant().getLastName());
        }
        return dto;
    }
}
