package com.kickoffsim.service.impl;

import com.kickoffsim.client.BroadcastRequest;
import com.kickoffsim.client.NotificationClient;
import com.kickoffsim.client.SubscriptionDto;
import com.kickoffsim.client.SubscriptionRequest;
import com.kickoffsim.exception.EntityNotFoundException;
import com.kickoffsim.exception.InvalidLeagueOperationException;
import com.kickoffsim.model.*;
import com.kickoffsim.repository.GoalRepository;
import com.kickoffsim.repository.LeagueRepository;
import com.kickoffsim.repository.MatchRepository;
import com.kickoffsim.repository.PlayerRepository;
import com.kickoffsim.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ScheduleServiceImpl implements ScheduleService {

    private static final LocalTime EARLIEST = LocalTime.of(8, 0);
    private static final LocalTime LATEST = LocalTime.of(23, 30);
    private static final LocalTime DEFAULT_START_TIME = LocalTime.of(11, 0);

    private static final double BASE_LAMBDA = 3.6;
    private static final double STRENGTH_SCALE = 0.06;
    private static final double HOME_ADVANTAGE = 0.6;
    private static final double MIN_LAMBDA = 1.0;
    private static final int MAX_GOALS_PER_TEAM = 12;

    private final LeagueRepository leagueRepository;
    private final MatchRepository matchRepository;
    private final PlayerRepository playerRepository;
    private final GoalRepository goalRepository;
    private final NotificationClient notificationClient;

    @Override
    @Transactional(noRollbackFor = InvalidLeagueOperationException.class)
    public void generate(UUID leagueId, LocalDate startDate, LocalTime startTime) {
        League league = leagueRepository.findByIdWithTeams(leagueId)
                .orElseThrow(() -> new EntityNotFoundException("League not found"));

        int n = league.getTeams().size();
        LeagueFormat format = LeagueFormat.forTeamCount(n)
                .orElseThrow(() -> new InvalidLeagueOperationException(
                        "Cannot generate schedule: league must have 6, 8, 10, or 16 teams (currently " + n + ")."));

        validateStartTime(startTime, format.getMatchesPerRound());

        if (matchRepository.existsByLeagueId(leagueId)) {
            throw new InvalidLeagueOperationException(
                    "A schedule already exists for this league. Delete existing matches first.");
        }

        buildAndSave(league, format, startDate, startTime);
    }

    @Override
    public void tryAutoGenerate(UUID leagueId) {
        League league = leagueRepository.findById(leagueId).orElse(null);
        if (league == null || league.getScheduleStartDate() == null) return;

        LeagueFormat format = LeagueFormat.forTeamCount(league.getTeams().size()).orElse(null);
        if (format == null) return;

        if (matchRepository.existsByLeagueId(leagueId)) return;

        LocalTime time = league.getScheduleStartTime() != null
                ? league.getScheduleStartTime()
                : DEFAULT_START_TIME;

        try {
            validateStartTime(time, format.getMatchesPerRound());
        } catch (InvalidLeagueOperationException e) {
            log.warn("Auto-generate skipped for league '{}': {}", league.getName(), e.getMessage());
            return;
        }

        log.info("Auto-generating schedule for league '{}' ({} teams)", league.getName(), league.getTeams().size());
        buildAndSave(league, format, league.getScheduleStartDate(), time);
    }

    private void buildAndSave(League league, LeagueFormat format, LocalDate startDate, LocalTime startTime) {
        List<Team> teams = league.getTeams().stream()
                .sorted(Comparator.comparing(Team::getName))
                .collect(Collectors.toList());

        int n = teams.size();

        Map<UUID, List<Player>> playersByTeam = teams.stream()
                .collect(Collectors.toMap(Team::getId, playerRepository::findAllByTeam));

        List<Match> allMatches = new ArrayList<>();
        LocalDate currentDate = startDate;

        for (int cycle = 0; cycle < format.getCycles(); cycle++) {
            boolean cycleSwap = (cycle % 2 == 1);
            int[] positions = IntStream.range(0, n).toArray();

            for (int round = 0; round < n - 1; round++) {
                int roundNumber = cycle * (n - 1) + round + 1;
                boolean swapHomeAway = cycleSwap ^ (round % 2 == 1);

                for (int i = 0; i < n / 2; i++) {
                    int aIdx = positions[i];
                    int bIdx = positions[n - 1 - i];
                    Team home = swapHomeAway ? teams.get(bIdx) : teams.get(aIdx);
                    Team away = swapHomeAway ? teams.get(aIdx) : teams.get(bIdx);

                    Match match = new Match();
                    match.setHomeTeam(home);
                    match.setAwayTeam(away);
                    match.setPlayedAt(currentDate.atTime(startTime).plusHours(i));
                    match.setHomeScore(0);
                    match.setAwayScore(0);
                    match.setRoundNumber(roundNumber);

                    simulateResult(match,
                            playersByTeam.getOrDefault(home.getId(), List.of()),
                            playersByTeam.getOrDefault(away.getId(), List.of()));

                    allMatches.add(match);
                }

                currentDate = currentDate.plusDays(1);
                rotatePositions(positions);
            }
        }

        matchRepository.saveAll(allMatches);
        List<Goal> allGoals = allMatches.stream()
                .flatMap(m -> m.getGoals().stream())
                .toList();
        if (!allGoals.isEmpty()) {
            goalRepository.saveAll(allGoals);
        }
        log.info("Generated {} matches ({} rounds) for league '{}'",
                allMatches.size(), format.getTotalRounds(), league.getName());

        backfillMatchSubscriptions(league, teams, allMatches);
    }

    private void backfillMatchSubscriptions(League league, List<Team> teams, List<Match> allMatches) {
        try {
            List<UUID> entityIds = new ArrayList<>();
            entityIds.add(league.getId());
            teams.forEach(t -> entityIds.add(t.getId()));

            List<SubscriptionDto> followers = notificationClient.getSubscriptionsForEntities(entityIds);

            Set<UUID> leagueFollowerUserIds = followers.stream()
                    .filter(s -> "LEAGUE".equals(s.getEntityType()) && league.getId().equals(s.getEntityId()))
                    .map(SubscriptionDto::getUserId)
                    .collect(Collectors.toSet());

            Map<UUID, Set<UUID>> teamFollowerUserIdsByTeam = followers.stream()
                    .filter(s -> "TEAM".equals(s.getEntityType()))
                    .collect(Collectors.groupingBy(SubscriptionDto::getEntityId,
                            Collectors.mapping(SubscriptionDto::getUserId, Collectors.toSet())));

            for (Match match : allMatches) {
                Set<UUID> eligibleUserIds = new LinkedHashSet<>(leagueFollowerUserIds);
                eligibleUserIds.addAll(teamFollowerUserIdsByTeam.getOrDefault(match.getHomeTeam().getId(), Set.of()));
                eligibleUserIds.addAll(teamFollowerUserIdsByTeam.getOrDefault(match.getAwayTeam().getId(), Set.of()));

                for (UUID userId : eligibleUserIds) {
                    try {
                        notificationClient.subscribe(new SubscriptionRequest(userId, "MATCH", match.getId()));
                    } catch (Exception e) {
                        log.warn("Could not auto-subscribe user {} to generated match {}: {}",
                                userId, match.getId(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not look up followers to auto-subscribe for league '{}': {}", league.getName(), e.getMessage());
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = "leagues", allEntries = true)
    public void simulatePastMatches() {
        LocalDateTime to = LocalDateTime.now().minusMinutes(50);
        List<Match> candidates = matchRepository.findGoallessBefore(to);
        int count = 0;
        List<Goal> allNewGoals = new ArrayList<>();
        for (Match match : candidates) {
            List<Player> homePlayers = playerRepository.findAllByTeam(match.getHomeTeam());
            List<Player> awayPlayers = playerRepository.findAllByTeam(match.getAwayTeam());
            simulateResult(match, homePlayers, awayPlayers);
            matchRepository.save(match);
            allNewGoals.addAll(match.getGoals());
            count++;
            try {
                UUID leagueId = match.getHomeTeam().getLeague() != null
                        ? match.getHomeTeam().getLeague().getId() : null;
                String message = match.getHomeTeam().getName() + " vs " + match.getAwayTeam().getName()
                        + ": " + match.getHomeScore() + "-" + match.getAwayScore();
                notificationClient.broadcast(new BroadcastRequest(
                        match.getId(),
                        match.getHomeTeam().getId(),
                        match.getAwayTeam().getId(),
                        leagueId,
                        message,
                        "MATCH_RESULT"
                ));
            } catch (Exception e) {
                log.warn("Failed to broadcast notification for match {}: {}", match.getId(), e.getMessage());
            }
        }
        if (!allNewGoals.isEmpty()) {
            goalRepository.saveAll(allNewGoals);
        }
        if (count > 0) {
            log.info("Auto-simulated {} match result(s)", count);
        }
    }

    @Override
    @Transactional
    public void notifyMatchEvents() {
        LocalDateTime now = LocalDateTime.now();
        notifyKickoffs(now);
        notifyHalftimes(now);
        notifySecondHalfStarts(now);
        notifyFulltimes(now);
    }

    private void notifyKickoffs(LocalDateTime now) {
        for (Match match : matchRepository.findForKickoffNotification(now.minusDays(1), now)) {
            try {
                broadcast(match,
                        teamLabel(match.getHomeTeam()) + " vs " + teamLabel(match.getAwayTeam()) + " — KICK OFF!",
                        "MATCH_KICKOFF");
                match.setKickoffNotified(true);
                matchRepository.save(match);
            } catch (Exception e) {
                log.warn("Failed to send kick-off notification for match {}: {}", match.getId(), e.getMessage());
            }
        }
    }

    private void notifyHalftimes(LocalDateTime now) {
        for (Match match : matchRepository.findForHalftimeNotification(now.minusDays(1), now.minusMinutes(20))) {
            try {
                int homeHalf = 0;
                int awayHalf = 0;
                for (Goal g : match.getGoals()) {
                    if (!Half.FIRST.equals(g.getHalf())) continue;
                    if (benefitsHome(g, match)) homeHalf++;
                    else awayHalf++;
                }
                broadcast(match,
                        teamLabel(match.getHomeTeam()) + " vs " + teamLabel(match.getAwayTeam())
                                + " — HALF TIME " + homeHalf + "-" + awayHalf,
                        "MATCH_HALFTIME");
                match.setHalftimeNotified(true);
                matchRepository.save(match);
            } catch (Exception e) {
                log.warn("Failed to send half-time notification for match {}: {}", match.getId(), e.getMessage());
            }
        }
    }

    private void notifySecondHalfStarts(LocalDateTime now) {
        for (Match match : matchRepository.findForSecondHalfNotification(now.minusDays(1), now.minusMinutes(25))) {
            try {
                int homeHalf = 0;
                int awayHalf = 0;
                for (Goal g : match.getGoals()) {
                    if (!Half.FIRST.equals(g.getHalf())) continue;
                    if (benefitsHome(g, match)) homeHalf++;
                    else awayHalf++;
                }
                broadcast(match,
                        teamLabel(match.getHomeTeam()) + " vs " + teamLabel(match.getAwayTeam())
                                + " — SECOND HALF " + homeHalf + "-" + awayHalf,
                        "MATCH_SECONDHALF");
                match.setSecondHalfNotified(true);
                matchRepository.save(match);
            } catch (Exception e) {
                log.warn("Failed to send second-half notification for match {}: {}", match.getId(), e.getMessage());
            }
        }
    }

    private void notifyFulltimes(LocalDateTime now) {
        for (Match match : matchRepository.findForFulltimeNotification(now.minusDays(1), now.minusMinutes(45))) {
            try {
                broadcast(match,
                        teamLabel(match.getHomeTeam()) + " vs " + teamLabel(match.getAwayTeam())
                                + " — FULL TIME " + match.getHomeScore() + "-" + match.getAwayScore(),
                        "MATCH_FULLTIME");
                match.setFulltimeNotified(true);
                matchRepository.save(match);
            } catch (Exception e) {
                log.warn("Failed to send full-time notification for match {}: {}", match.getId(), e.getMessage());
            }
        }
    }

    private String teamLabel(Team team) {
        return team.getCity() != null ? team.getName() + " (" + team.getCity() + ")" : team.getName();
    }

    @Override
    @Transactional
    public void notifyGoals() {
        LocalDateTime now = LocalDateTime.now();

        for (Goal goal : goalRepository.findUnnotifiedForMatchesStartedBetween(now.minusDays(1), now)) {
            LocalDateTime goalTime = realGoalTime(goal);
            if (goalTime.isAfter(now)) {
                continue;
            }

            Match match = goal.getMatch();
            int homeScore = 0;
            int awayScore = 0;
            for (Goal other : match.getGoals()) {
                if (realGoalTime(other).isAfter(goalTime)) continue;
                if (benefitsHome(other, match)) homeScore++;
                else awayScore++;
            }

            int minute = goal.getMinute() != null ? goal.getMinute() : 0;
            int displayMinute = Half.SECOND.equals(goal.getHalf()) ? minute + 20 : minute;
            String scorerName = goal.getScorer().getFirstName() + " " + goal.getScorer().getLastName();
            String scoringTeam = benefitsHome(goal, match)
                    ? teamLabel(match.getHomeTeam())
                    : teamLabel(match.getAwayTeam());
            String message = "GOAL for " + scoringTeam + "! " + scorerName
                    + (goal.getAssistant() != null ? " (assist: " + goal.getAssistant().getFirstName() + " " + goal.getAssistant().getLastName() + ")" : "")
                    + " " + displayMinute + "'"
                    + (goal.isOwnGoal() ? " (own goal)" : goal.isPenalty() ? " (penalty)" : "")
                    + " — " + teamLabel(match.getHomeTeam()) + " " + homeScore + ":" + awayScore
                    + " " + teamLabel(match.getAwayTeam());

            try {
                broadcast(match, message, "GOAL");
                goal.setNotified(true);
                goalRepository.save(goal);
            } catch (Exception e) {
                log.warn("Failed to send goal notification for goal {}: {}", goal.getId(), e.getMessage());
            }
        }
    }

    private LocalDateTime realGoalTime(Goal goal) {
        int offsetSeconds = goal.getOffsetSeconds() != null
                ? goal.getOffsetSeconds()
                : (goal.getMinute() != null ? (goal.getMinute() - 1) * 60 : 0);
        int totalSeconds = Half.SECOND.equals(goal.getHalf()) ? (25 * 60) + offsetSeconds : offsetSeconds;
        return goal.getMatch().getPlayedAt().plusSeconds(totalSeconds);
    }

    private boolean benefitsHome(Goal goal, Match match) {
        boolean scorerIsHome = goal.getScorer().getTeam().getId().equals(match.getHomeTeam().getId());
        return goal.isOwnGoal() ? !scorerIsHome : scorerIsHome;
    }

    private void broadcast(Match match, String message, String type) {
        UUID leagueId = match.getHomeTeam().getLeague() != null
                ? match.getHomeTeam().getLeague().getId() : null;
        notificationClient.broadcast(new BroadcastRequest(
                match.getId(),
                match.getHomeTeam().getId(),
                match.getAwayTeam().getId(),
                leagueId,
                message,
                type
        ));
    }

    private void simulateResult(Match match, List<Player> homePlayers, List<Player> awayPlayers) {
        int diff = match.getHomeTeam().getStrength() - match.getAwayTeam().getStrength();
        double lambdaHome = Math.max(MIN_LAMBDA, BASE_LAMBDA + diff * STRENGTH_SCALE + HOME_ADVANTAGE);
        double lambdaAway = Math.max(MIN_LAMBDA, BASE_LAMBDA - diff * STRENGTH_SCALE - HOME_ADVANTAGE / 2);

        int homeGoals = Math.min(samplePoisson(lambdaHome), MAX_GOALS_PER_TEAM);
        int awayGoals = Math.min(samplePoisson(lambdaAway), MAX_GOALS_PER_TEAM);

        match.setHomeScore(homeGoals);
        match.setAwayScore(awayGoals);
        addGoals(match, homePlayers, awayPlayers, homeGoals);
        addGoals(match, awayPlayers, homePlayers, awayGoals);
        enforceMinimumGoalSpacing(match);
    }

    private int samplePoisson(double lambda) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double l = Math.exp(-lambda);
        int k = 0;
        double p = 1.0;
        do {
            k++;
            p *= rng.nextDouble();
        } while (p > l);
        return k - 1;
    }

    private static final int MIN_GOAL_GAP_SECONDS = 28;
    private static final int GOAL_GAP_JITTER_SECONDS = 22;

    private void enforceMinimumGoalSpacing(Match match) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (Half half : Half.values()) {
            List<Goal> inHalf = match.getGoals().stream()
                    .filter(g -> half.equals(g.getHalf()))
                    .sorted(Comparator.comparing(Goal::getOffsetSeconds))
                    .toList();
            int earliestAllowed = -MIN_GOAL_GAP_SECONDS;
            for (Goal g : inHalf) {
                int offset = Math.max(g.getOffsetSeconds(), earliestAllowed);
                offset = Math.min(offset, 1199);
                g.setOffsetSeconds(offset);
                g.setMinute(offset / 60 + 1);
                earliestAllowed = offset + MIN_GOAL_GAP_SECONDS + rng.nextInt(GOAL_GAP_JITTER_SECONDS + 1);
            }
        }
    }

    private void addGoals(Match match, List<Player> scoringPlayers, List<Player> concedingPlayers, int count) {
        if (count == 0 || scoringPlayers.isEmpty()) return;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            boolean firstHalf = rng.nextBoolean();
            int offsetSeconds = rng.nextInt(0, 1200);
            int minute = offsetSeconds / 60 + 1;

            boolean isOwnGoal = !concedingPlayers.isEmpty() && rng.nextInt(100) < 8;
            boolean isPenalty = !isOwnGoal && rng.nextInt(100) < 12;

            Player scorer = isOwnGoal
                    ? concedingPlayers.get(rng.nextInt(concedingPlayers.size()))
                    : scoringPlayers.get(rng.nextInt(scoringPlayers.size()));

            Player assistant = null;
            if (!isOwnGoal && !isPenalty && scoringPlayers.size() > 1 && rng.nextInt(10) < 6) {
                do {
                    assistant = scoringPlayers.get(rng.nextInt(scoringPlayers.size()));
                } while (assistant.getId().equals(scorer.getId()));
            }

            Goal goal = new Goal();
            goal.setMatch(match);
            goal.setScorer(scorer);
            goal.setAssistant(assistant);
            goal.setMinute(minute);
            goal.setOffsetSeconds(offsetSeconds);
            goal.setHalf(firstHalf ? Half.FIRST : Half.SECOND);
            goal.setOwnGoal(isOwnGoal);
            goal.setPenalty(isPenalty);
            match.getGoals().add(goal);
        }
    }

    private void validateStartTime(LocalTime startTime, int matchesPerRound) {
        if (startTime.getMinute() % 15 != 0) {
            throw new InvalidLeagueOperationException(
                    "Start time must be on a 15-minute mark (e.g. 11:00, 11:15, 11:30, 11:45).");
        }
        if (startTime.isBefore(EARLIEST)) {
            throw new InvalidLeagueOperationException("Start time cannot be before 08:00.");
        }
        int startMinutes = startTime.getHour() * 60 + startTime.getMinute();
        int latestMinutes = LATEST.getHour() * 60 + LATEST.getMinute();
        int lastStartMinutes = startMinutes + (matchesPerRound - 1) * 60;
        if (lastStartMinutes > latestMinutes) {
            int latestStartMinutes = latestMinutes - (matchesPerRound - 1) * 60;
            LocalTime latestStart = LocalTime.of(latestStartMinutes / 60, latestStartMinutes % 60);
            throw new InvalidLeagueOperationException(
                    "Start time too late: the last match would kick off after " + LATEST
                    + ". Choose " + latestStart + " or earlier.");
        }
    }

    private void rotatePositions(int[] positions) {
        int first = positions[1];
        for (int i = 1; i < positions.length - 1; i++) {
            positions[i] = positions[i + 1];
        }
        positions[positions.length - 1] = first;
    }
}
