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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
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
    @Transactional
    public MatchDto create(MatchDto matchDto) {
        Match match = new Match();
        mapToEntity(matchDto, match);
        MatchDto saved = toDto(matchRepository.save(match));
        log.info("Created match {} vs {}", saved.getHomeTeamName(), saved.getAwayTeamName());
        return saved;
    }

    @Override
    @Transactional
    public MatchDto update(UUID id, MatchDto matchDto) {
        Match match = getMatchOrThrow(id);
        mapToEntity(matchDto, match);
        MatchDto saved = toDto(matchRepository.save(match));
        log.info("Updated match {}", id);
        return saved;
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        matchRepository.delete(getMatchOrThrow(id));
        log.info("Deleted match {}", id);
    }

    @Override
    @Transactional
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

        boolean creditedToHome = dto.isOwnGoal() ? scorerIsAway : scorerIsHome;
        validateGoalCountLimit(match, creditedToHome, null);
        validateMinuteUnique(match, dto.getMinute(), null);

        if (dto.isOwnGoal() && dto.getAssistantId() != null) {
            throw new InvalidGoalException("Own goals cannot have an assist.");
        }
        Player assistant = dto.isOwnGoal() ? null : resolveAssistant(dto.getAssistantId(), scorer);

        Goal goal = new Goal();
        goal.setMatch(match);
        goal.setScorer(scorer);
        goal.setAssistant(assistant);
        goal.setOwnGoal(dto.isOwnGoal());
        goal.setPenalty(dto.isPenalty());
        applyHalfAndMinute(goal, dto.getMinute());
        goalRepository.save(goal);
        log.info("Recorded goal for match {} by player {}", matchId, dto.getScorerId());
    }

    @Override
    public GoalDto findGoalById(UUID goalId) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new EntityNotFoundException("Goal not found"));
        return toGoalDto(goal);
    }

    @Override
    @Transactional
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

        boolean creditedToHome = goal.isOwnGoal() ? !scorerIsHome : scorerIsHome;
        validateGoalCountLimit(match, creditedToHome, goalId);

        Player assistant = goal.isOwnGoal() ? null : resolveAssistant(dto.getAssistantId(), scorer);

        goal.setScorer(scorer);
        goal.setAssistant(assistant);
        goal.setPenalty(dto.isPenalty());
        goalRepository.save(goal);
        log.info("Updated goal {}", goalId);
    }

    @Override
    @Transactional
    public void deleteGoal(UUID goalId) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new EntityNotFoundException("Goal not found"));
        goalRepository.delete(goal);
        log.info("Deleted goal {}", goalId);
    }

    @Override
    public List<MatchDto> findByLeague(UUID leagueId) {
        return matchRepository.findByLeagueId(leagueId).stream()
                .map(this::toDto)
                .toList();
    }

    private static Half rawMinuteToHalf(int rawMinute) {
        return rawMinute <= 20 ? Half.FIRST : Half.SECOND;
    }

    private static int rawMinuteToStored(int rawMinute) {
        return rawMinute <= 20 ? rawMinute : rawMinute - 20;
    }

    private void validateMinuteUnique(Match match, Integer rawMinute, UUID excludeGoalId) {
        if (rawMinute == null) {
            return;
        }
        if (goalRepository.countByMatchAndHalfAndMinuteExcluding(
                match, rawMinuteToHalf(rawMinute), rawMinuteToStored(rawMinute), excludeGoalId) > 0) {
            throw new InvalidGoalException("A goal has already been recorded at minute " + rawMinute + ".");
        }
    }

    private Player resolveAssistant(UUID assistantId, Player scorer) {
        if (assistantId == null) {
            return null;
        }
        if (assistantId.equals(scorer.getId())) {
            throw new InvalidGoalException("A player cannot assist his own goal.");
        }
        Player assistant = getPlayerOrThrow(assistantId);
        if (!assistant.getTeam().getId().equals(scorer.getTeam().getId())) {
            throw new InvalidGoalException("Assistant must be from the same team as the scorer.");
        }
        return assistant;
    }

    private void validateGoalCountLimit(Match match, boolean creditedToHome, UUID excludeGoalId) {
        UUID benefitingTeamId = creditedToHome ? match.getHomeTeam().getId() : match.getAwayTeam().getId();
        int declared = creditedToHome ? match.getHomeScore() : match.getAwayScore();
        long current = goalRepository.countGoalsBenefitingTeam(match, benefitingTeamId, excludeGoalId);
        if (current >= declared) {
            String side = creditedToHome ? "home" : "away";
            throw new InvalidGoalException(
                    "Cannot add more " + side + " goals — declared " + side + " score is " + declared + ".");
        }
    }

    private void applyHalfAndMinute(Goal goal, Integer rawMinute) {
        goal.setHalf(rawMinute == null ? Half.FIRST : rawMinuteToHalf(rawMinute));
        goal.setMinute(rawMinute == null ? null : rawMinuteToStored(rawMinute));
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

    @Override
    public List<LocalDate> findAllMatchDates() {
        return matchRepository.findAllPlayedAtTimes().stream()
                .map(LocalDateTime::toLocalDate)
                .distinct()
                .sorted()
                .toList();
    }

    @Override
    public List<MatchDto> findByDate(LocalDate date) {
        return matchRepository.findByDateRange(
                date.atStartOfDay(),
                date.plusDays(1).atStartOfDay()
        ).stream().map(this::toDto).toList();
    }

    private MatchDto toDto(Match match) {
        MatchDto matchDto = new MatchDto();
        matchDto.setId(match.getId());
        matchDto.setHomeTeamId(match.getHomeTeam().getId());
        matchDto.setHomeTeamName(match.getHomeTeam().getName());
        matchDto.setHomeTeamCity(match.getHomeTeam().getCity());
        matchDto.setAwayTeamId(match.getAwayTeam().getId());
        matchDto.setAwayTeamName(match.getAwayTeam().getName());
        matchDto.setAwayTeamCity(match.getAwayTeam().getCity());
        matchDto.setHomeScore(match.getHomeScore());
        matchDto.setAwayScore(match.getAwayScore());
        matchDto.setPlayedAt(match.getPlayedAt());
        matchDto.setRoundNumber(match.getRoundNumber());
        if (match.getHomeTeam().getLeague() != null) {
            matchDto.setLeagueId(match.getHomeTeam().getLeague().getId());
            matchDto.setLeagueName(match.getHomeTeam().getLeague().getName());
        }

        UUID homeTeamId = match.getHomeTeam().getId();

        List<GoalDto> sorted = match.getGoals().stream()
                .map(this::toGoalDto)
                .sorted(Comparator.comparingInt(dto ->
                        dto.getMinute() == null ? Integer.MAX_VALUE
                                : dto.getHalf() == Half.FIRST ? dto.getMinute() : dto.getMinute() + 20))
                .toList();

        int runningHome = 0, runningAway = 0, homeHalf = 0, awayHalf = 0;
        Half currentHalf = null;
        for (GoalDto dto : sorted) {
            boolean scorerIsHome = homeTeamId.equals(dto.getTeamId());
            boolean isHome = dto.isOwnGoal() ? !scorerIsHome : scorerIsHome;
            dto.setHomeGoal(isHome);

            if (!dto.getHalf().equals(currentHalf)) {
                dto.setFirstInHalf(true);
                currentHalf = dto.getHalf();
            }

            if (isHome) runningHome++; else runningAway++;
            dto.setRunningHomeScore(runningHome);
            dto.setRunningAwayScore(runningAway);

            if (dto.getHalf() == Half.FIRST) {
                if (isHome) homeHalf++; else awayHalf++;
            }

            matchDto.getGoalTimeline().add(dto);
        }
        if (!sorted.isEmpty()) {
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
        dto.setOwnGoal(goal.isOwnGoal());
        dto.setPenalty(goal.isPenalty());
        return dto;
    }
}
