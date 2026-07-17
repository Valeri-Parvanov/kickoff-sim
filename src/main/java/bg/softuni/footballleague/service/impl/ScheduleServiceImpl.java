package bg.softuni.footballleague.service.impl;

import bg.softuni.footballleague.client.BroadcastRequest;
import bg.softuni.footballleague.client.NotificationClient;
import bg.softuni.footballleague.exception.EntityNotFoundException;
import bg.softuni.footballleague.exception.InvalidLeagueOperationException;
import bg.softuni.footballleague.model.*;
import bg.softuni.footballleague.repository.GoalRepository;
import bg.softuni.footballleague.repository.LeagueRepository;
import bg.softuni.footballleague.repository.MatchRepository;
import bg.softuni.footballleague.repository.PlayerRepository;
import bg.softuni.footballleague.service.ScheduleService;
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

    private static final int[] TOTAL_GOAL_WEIGHTS = {2, 5, 9, 15, 20, 19, 15, 9, 4, 2};

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
        notifyFulltimes(now);
    }

    private void notifyKickoffs(LocalDateTime now) {
        for (Match match : matchRepository.findForKickoffNotification(now.minusMinutes(5), now)) {
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
        for (Match match : matchRepository.findForHalftimeNotification(now.minusMinutes(26), now.minusMinutes(21))) {
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

    private void notifyFulltimes(LocalDateTime now) {
        for (Match match : matchRepository.findForFulltimeNotification(now.minusMinutes(51), now.minusMinutes(46))) {
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
        LocalDateTime toastWindowStart = now.minusMinutes(5);

        for (Goal goal : goalRepository.findUnnotifiedForMatchesStartedBetween(now.minusMinutes(50), now)) {
            LocalDateTime goalTime = realGoalTime(goal);
            if (goalTime.isAfter(now) || goalTime.isBefore(toastWindowStart)) {
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
            String message = "GOAL for " + scoringTeam + "! " + scorerName + " " + displayMinute + "'"
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
        int minute = goal.getMinute() != null ? goal.getMinute() : 0;
        int offset = Half.SECOND.equals(goal.getHalf()) ? 25 + minute : minute;
        return goal.getMatch().getPlayedAt().plusMinutes(offset);
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
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int total = randomTotalGoals();
        int homeGoals = 0;
        for (int i = 0; i < total; i++) {
            if (rng.nextInt(100) < 55) homeGoals++;
        }
        int awayGoals = total - homeGoals;
        match.setHomeScore(homeGoals);
        match.setAwayScore(awayGoals);
        addGoals(match, homePlayers, awayPlayers, homeGoals);
        addGoals(match, awayPlayers, homePlayers, awayGoals);
    }

    private void addGoals(Match match, List<Player> scoringPlayers, List<Player> concedingPlayers, int count) {
        if (count == 0 || scoringPlayers.isEmpty()) return;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            boolean firstHalf = rng.nextBoolean();
            int minute = rng.nextInt(1, 21);

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
            goal.setHalf(firstHalf ? Half.FIRST : Half.SECOND);
            goal.setOwnGoal(isOwnGoal);
            goal.setPenalty(isPenalty);
            match.getGoals().add(goal);
        }
    }

    private int randomTotalGoals() {
        int r = ThreadLocalRandom.current().nextInt(100);
        int cumulative = 0;
        for (int i = 0; i < TOTAL_GOAL_WEIGHTS.length - 1; i++) {
            cumulative += TOTAL_GOAL_WEIGHTS[i];
            if (r < cumulative) return i;
        }
        return TOTAL_GOAL_WEIGHTS.length - 1;
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
