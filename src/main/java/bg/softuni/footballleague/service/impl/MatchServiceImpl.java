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
        boolean scorerIsHome = scorerTeamId.equals(match.getHomeTeam().getId());
        boolean scorerIsAway = scorerTeamId.equals(match.getAwayTeam().getId());
        if (!scorerIsHome && !scorerIsAway) {
            throw new InvalidGoalException("Scorer %s %s does not play in this match."
                    .formatted(scorer.getFirstName(), scorer.getLastName()));
        }

        validateGoalCountLimit(match, scorerIsHome, null);

        Player assistant = null;
        if (dto.getAssistantId() != null) {
            assistant = getPlayerOrThrow(dto.getAssistantId());
            if (!assistant.getTeam().getId().equals(scorerTeamId)) {
                throw new InvalidGoalException("Assistant must be from the same team as the scorer.");
            }
        }

        Goal goal = new Goal();
        goal.setMatch(match);
        goal.setScorer(scorer);
        goal.setAssistant(assistant);
        applyHalfAndMinute(goal, dto.getMinute());
        goalRepository.save(goal);
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
        Match match = goal.getMatch();
        boolean scorerIsHome = scorerTeamId.equals(match.getHomeTeam().getId());
        boolean scorerIsAway = scorerTeamId.equals(match.getAwayTeam().getId());
        if (!scorerIsHome && !scorerIsAway) {
            throw new InvalidGoalException("Scorer %s %s does not play in this match."
                    .formatted(scorer.getFirstName(), scorer.getLastName()));
        }

        validateGoalCountLimit(match, scorerIsHome, goalId);

        Player assistant = null;
        if (dto.getAssistantId() != null) {
            assistant = getPlayerOrThrow(dto.getAssistantId());
            if (!assistant.getTeam().getId().equals(scorerTeamId)) {
                throw new InvalidGoalException("Assistant must be from the same team as the scorer.");
            }
        }

        goal.setScorer(scorer);
        goal.setAssistant(assistant);
        applyHalfAndMinute(goal, dto.getMinute());
        goalRepository.save(goal);
    }

    @Override
    public void deleteGoal(UUID goalId) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new EntityNotFoundException("Goal not found"));
        goalRepository.delete(goal);
    }

    private void validateGoalCountLimit(Match match, boolean scorerIsHome, UUID excludeGoalId) {
        UUID teamId = scorerIsHome ? match.getHomeTeam().getId() : match.getAwayTeam().getId();
        int declared = scorerIsHome ? match.getHomeScore() : match.getAwayScore();
        long current = goalRepository.countByMatchAndScorerTeamIdExcluding(match, teamId, excludeGoalId);
        if (current >= declared) {
            String side = scorerIsHome ? "home" : "away";
            throw new InvalidGoalException(
                    "Cannot add more " + side + " goals — declared " + side + " score is " + declared + ".");
        }
    }

    private void applyHalfAndMinute(Goal goal, Integer rawMinute) {
        goal.setHalf(rawMinute == null || rawMinute <= 20 ? Half.FIRST : Half.SECOND);
        // explicit boxing avoids NPE: ternary with mixed int/Integer unboxes the null branch
        goal.setMinute(rawMinute != null && rawMinute > 20 ? Integer.valueOf(rawMinute - 20) : rawMinute);
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
        match.setHomeScore(matchDto.getHomeScore() != null ? matchDto.getHomeScore() : 0);
        match.setAwayScore(matchDto.getAwayScore() != null ? matchDto.getAwayScore() : 0);
    }

    private MatchDto toDto(Match match) {
        MatchDto matchDto = new MatchDto();
        matchDto.setId(match.getId());
        matchDto.setHomeTeamId(match.getHomeTeam().getId());
        matchDto.setHomeTeamName(match.getHomeTeam().getName());
        matchDto.setAwayTeamId(match.getAwayTeam().getId());
        matchDto.setAwayTeamName(match.getAwayTeam().getName());
        matchDto.setHomeScore(match.getHomeScore());
        matchDto.setAwayScore(match.getAwayScore());
        matchDto.setPlayedAt(match.getPlayedAt());

        UUID homeTeamId = match.getHomeTeam().getId();
        int homeHalf = 0, awayHalf = 0;
        for (Goal g : match.getGoals()) {
            GoalDto dto = toGoalDto(g);
            boolean isHome = homeTeamId.equals(dto.getTeamId());
            boolean isFirst = g.getHalf() == Half.FIRST;

            if (isFirst) {
                if (isHome) { matchDto.getFirstHalfHomeGoals().add(dto); homeHalf++; }
                else        { matchDto.getFirstHalfAwayGoals().add(dto); awayHalf++; }
            } else {
                if (isHome) matchDto.getSecondHalfHomeGoals().add(dto);
                else        matchDto.getSecondHalfAwayGoals().add(dto);
            }
        }
        if (!match.getGoals().isEmpty()) {
            matchDto.setHomeHalfScore(homeHalf);
            matchDto.setAwayHalfScore(awayHalf);
        }

        return matchDto;
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
