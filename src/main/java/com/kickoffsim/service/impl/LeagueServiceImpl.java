package com.kickoffsim.service.impl;

import com.kickoffsim.dto.*;
import com.kickoffsim.exception.EntityNotFoundException;
import com.kickoffsim.exception.InvalidLeagueOperationException;
import com.kickoffsim.model.*;
import com.kickoffsim.repository.LeagueRepository;
import com.kickoffsim.repository.MatchRepository;
import com.kickoffsim.repository.PlayerRepository;
import com.kickoffsim.repository.TeamRepository;
import com.kickoffsim.service.LeagueService;
import com.kickoffsim.service.MatchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(readOnly = true)
public class LeagueServiceImpl implements LeagueService {

    private static final Sort DEFAULT_SORT = Sort.by("name");

    private static final int MIN_PLAYERS_PER_TEAM = 6;

    private final LeagueRepository leagueRepository;
    private final MatchRepository matchRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final MatchService matchService;

    public LeagueServiceImpl(LeagueRepository leagueRepository,
                              MatchRepository matchRepository,
                              TeamRepository teamRepository,
                              PlayerRepository playerRepository,
                              @Lazy MatchService matchService) {
        this.leagueRepository = leagueRepository;
        this.matchRepository = matchRepository;
        this.teamRepository = teamRepository;
        this.playerRepository = playerRepository;
        this.matchService = matchService;
    }

    @Override
    @Cacheable("leagues")
    public List<LeagueDto> findAll() {
        return findAll(DEFAULT_SORT);
    }

    @Override
    public List<LeagueDto> findAll(Sort sort) {
        return leagueRepository.findAll(sort).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public LeagueDto findById(UUID id) {
        return toDto(getLeagueOrThrow(id));
    }

    @Override
    @Transactional
    @CacheEvict(value = "leagues", allEntries = true)
    public LeagueDto create(LeagueDto leagueDto) {
        int teamCount = leagueDto.getTeamIds() == null ? 0 : leagueDto.getTeamIds().size();
        if (teamCount == 0) {
            throw new InvalidLeagueOperationException("A league must include at least one team.");
        }
        if (LeagueFormat.forTeamCount(teamCount).isEmpty()) {
            throw new InvalidLeagueOperationException(
                    "Team count must be exactly 6, 8, 10, or 16 (selected: " + teamCount + ").");
        }
        League league = new League();
        mapToEntity(leagueDto, league);
        League saved = leagueRepository.save(league);

        List<Team> teams = teamRepository.findAllById(leagueDto.getTeamIds());
        for (Team team : teams) {
            if (team.getLeague() != null) {
                throw new InvalidLeagueOperationException(
                        "Team '" + team.getName() + "' is already assigned to another league.");
            }
            long count = playerRepository.countByTeam(team);
            if (count < MIN_PLAYERS_PER_TEAM) {
                throw new InvalidLeagueOperationException(
                        "Team '" + team.getName() + "' must have at least " + MIN_PLAYERS_PER_TEAM
                                + " players (has " + count + ").");
            }
            team.setLeague(saved);
            teamRepository.save(team);
            saved.getTeams().add(team);
        }

        log.info("Created league '{}' with {} team(s)", saved.getName(), teams.size());
        return toDto(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = "leagues", allEntries = true)
    public LeagueDto update(UUID id, LeagueDto leagueDto) {
        League league = getLeagueOrThrow(id);
        mapToEntity(leagueDto, league);
        LeagueDto saved = toDto(leagueRepository.save(league));
        log.info("Updated league {}", id);
        return saved;
    }

    @Override
    @Transactional
    @CacheEvict(value = "leagues", allEntries = true)
    public void delete(UUID id) {
        League league = getLeagueOrThrow(id);
        if (hasLeagueStarted(id)) {
            throw new InvalidLeagueOperationException(
                    "Cannot delete '%s' — it already has played or live matches.".formatted(league.getName()));
        }
        deleteInternal(league);
    }

    @Override
    @Transactional
    @CacheEvict(value = "leagues", allEntries = true)
    public int deleteFinishedOlderThan(int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        List<League> finished = leagueRepository.findFinishedBefore(cutoff);
        for (League league : finished) {
            deleteInternal(league);
        }
        if (!finished.isEmpty()) {
            log.info("Auto-deleted {} finished league(s) with no match in the last {} days", finished.size(), days);
        }
        return finished.size();
    }

    private void deleteInternal(League league) {
        List<Team> teams = new ArrayList<>(league.getTeams());
        for (Team team : teams) {
            List<Match> matches = matchRepository.findAllByHomeTeamOrAwayTeam(team, team);
            matchRepository.deleteAll(matches);
            team.setLeague(null);
            teamRepository.save(team);
        }
        leagueRepository.delete(league);
        log.info("Deleted league {} — {} team(s) detached", league.getId(), teams.size());
    }

    @Override
    public LeagueDetailView findDetail(UUID id) {
        League league = getLeagueOrThrow(id);
        List<MatchDto> allMatches = matchService.findByLeague(id);

        Set<UUID> teamIds = league.getTeams().stream()
                .map(Team::getId)
                .collect(Collectors.toSet());

        List<MatchDto> intraLeagueMatches = allMatches.stream()
                .filter(m -> teamIds.contains(m.getHomeTeamId()) && teamIds.contains(m.getAwayTeamId()))
                .toList();

        Map<UUID, StandingRow> rowMap = new LinkedHashMap<>();
        Map<UUID, String> teamNameById = new HashMap<>();
        Map<UUID, String> teamCityById = new HashMap<>();
        league.getTeams().stream()
                .sorted(Comparator.comparing(Team::getName))
                .forEach(t -> {
                    StandingRow row = new StandingRow();
                    row.setTeamId(t.getId());
                    row.setTeamName(t.getName());
                    row.setTeamCity(t.getCity());
                    rowMap.put(t.getId(), row);
                    teamNameById.put(t.getId(), t.getName());
                    teamCityById.put(t.getId(), t.getCity());
                });

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime liveThreshold = now.minusMinutes(46);
        Map<UUID, PlayerStatRow> scorerMap = new LinkedHashMap<>();
        Map<UUID, PlayerStatRow> assistMap = new LinkedHashMap<>();
        for (MatchDto m : intraLeagueMatches) {
            if (m.getPlayedAt() == null || m.getPlayedAt().isAfter(now)) continue;

            boolean isLive = m.getPlayedAt().isAfter(liveThreshold);
            int homeGoals, awayGoals;
            if (isLive) {
                int[] live = computeLiveScore(m, now);
                homeGoals = live[0];
                awayGoals = live[1];
            } else {
                if (m.getHomeScore() == null || m.getAwayScore() == null) continue;
                homeGoals = m.getHomeScore();
                awayGoals = m.getAwayScore();
            }

            StandingRow home = rowMap.get(m.getHomeTeamId());
            StandingRow away = rowMap.get(m.getAwayTeamId());

            home.setPlayed(home.getPlayed() + 1);
            away.setPlayed(away.getPlayed() + 1);
            home.setGoalsFor(home.getGoalsFor() + homeGoals);
            home.setGoalsAgainst(home.getGoalsAgainst() + awayGoals);
            away.setGoalsFor(away.getGoalsFor() + awayGoals);
            away.setGoalsAgainst(away.getGoalsAgainst() + homeGoals);

            if (homeGoals > awayGoals) {
                home.setWins(home.getWins() + 1);
                away.setLosses(away.getLosses() + 1);
            } else if (homeGoals < awayGoals) {
                away.setWins(away.getWins() + 1);
                home.setLosses(home.getLosses() + 1);
            } else {
                home.setDraws(home.getDraws() + 1);
                away.setDraws(away.getDraws() + 1);
            }

            long realSec = isLive ? Duration.between(m.getPlayedAt(), now).getSeconds() : -1;
            for (GoalDto g : m.getGoalTimeline()) {
                if (isLive && !goalRevealed(g, realSec)) continue;
                if (!g.isOwnGoal() && g.getScorerId() != null) {
                    tally(scorerMap, g.getScorerId(), g.getScorerName(), g.getTeamId(),
                            teamNameById.get(g.getTeamId()), teamCityById.get(g.getTeamId()));
                }
                if (g.getAssistantId() != null) {
                    tally(assistMap, g.getAssistantId(), g.getAssistantName(), g.getTeamId(),
                            teamNameById.get(g.getTeamId()), teamCityById.get(g.getTeamId()));
                }
            }
        }

        List<StandingRow> standings = sortWithTiebreakers(new ArrayList<>(rowMap.values()), intraLeagueMatches);
        markChampion(standings, LeagueFormat.forTeamCount(league.getTeams().size()).orElse(null));

        List<TeamDto> teamDtos = league.getTeams().stream()
                .sorted(Comparator.comparing(Team::getName))
                .map(t -> {
                    TeamDto dto = new TeamDto();
                    dto.setId(t.getId());
                    dto.setName(t.getName());
                    dto.setCity(t.getCity());
                    return dto;
                })
                .toList();

        LeagueDetailView view = new LeagueDetailView();
        view.setId(league.getId());
        view.setName(league.getName());
        view.setTeams(teamDtos);
        view.setStandings(standings);
        view.setTopScorers(topN(scorerMap, 5));
        view.setTopAssists(topN(assistMap, 5));
        view.setMatches(allMatches.stream()
                .sorted(Comparator.comparingInt((MatchDto m) -> m.getRoundNumber() == null ? Integer.MAX_VALUE : m.getRoundNumber())
                        .thenComparing(MatchDto::getPlayedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList());
        view.setFormat(LeagueFormat.forTeamCount(league.getTeams().size()).orElse(null));
        view.setScheduleStartDate(league.getScheduleStartDate());
        view.setScheduleStartTime(league.getScheduleStartTime());
        return view;
    }

    void markChampion(List<StandingRow> standings, LeagueFormat format) {
        if (format == null || standings.size() < 2) return;

        StandingRow leader = standings.get(0);
        if (leader.getPlayed() == 0) return;

        int matchesPerTeam = format.getTotalRounds();
        for (int i = 1; i < standings.size(); i++) {
            StandingRow rival = standings.get(i);
            int rivalRemaining = Math.max(0, matchesPerTeam - rival.getPlayed());
            int rivalMaxPoints = rival.getPoints() + 3 * rivalRemaining;
            if (leader.getPoints() <= rivalMaxPoints) return;
        }
        leader.setChampion(true);
    }

    private List<StandingRow> sortWithTiebreakers(List<StandingRow> rows, List<MatchDto> intraLeagueMatches) {
        rows.sort(Comparator.<StandingRow>comparingInt(r -> -r.getPoints())
                .thenComparingInt(r -> -r.getGoalDiff())
                .thenComparingInt(r -> -r.getGoalsFor())
                .thenComparing(StandingRow::getTeamName));

        int i = 0;
        while (i < rows.size()) {
            int j = i + 1;
            while (j < rows.size() && samePrimaryKey(rows.get(i), rows.get(j))) {
                j++;
            }
            if (j - i > 1) {
                List<StandingRow> sorted = sortByH2H(rows.subList(i, j), intraLeagueMatches);
                for (int k = 0; k < sorted.size(); k++) {
                    rows.set(i + k, sorted.get(k));
                }
            }
            i = j;
        }
        return rows;
    }

    private boolean samePrimaryKey(StandingRow a, StandingRow b) {
        return a.getPoints() == b.getPoints()
                && a.getGoalDiff() == b.getGoalDiff()
                && a.getGoalsFor() == b.getGoalsFor();
    }

    private List<StandingRow> sortByH2H(List<StandingRow> group, List<MatchDto> allIntraLeagueMatches) {
        Set<UUID> groupIds = group.stream().map(StandingRow::getTeamId).collect(Collectors.toSet());

        Map<UUID, int[]> h2h = new HashMap<>();
        for (StandingRow r : group) {
            h2h.put(r.getTeamId(), new int[]{0, 0, 0});
        }

        allIntraLeagueMatches.stream()
                .filter(m -> groupIds.contains(m.getHomeTeamId()) && groupIds.contains(m.getAwayTeamId()))
                .filter(m -> m.getHomeScore() != null && m.getAwayScore() != null)
                .forEach(m -> {
                    int[] home = h2h.get(m.getHomeTeamId());
                    int[] away = h2h.get(m.getAwayTeamId());
                    home[1] += m.getHomeScore() - m.getAwayScore();
                    home[2] += m.getHomeScore();
                    away[1] += m.getAwayScore() - m.getHomeScore();
                    away[2] += m.getAwayScore();
                    if (m.getHomeScore() > m.getAwayScore()) {
                        home[0] += 3;
                    } else if (m.getHomeScore() < m.getAwayScore()) {
                        away[0] += 3;
                    } else {
                        home[0] += 1;
                        away[0] += 1;
                    }
                });

        return group.stream()
                .sorted(Comparator.<StandingRow>comparingInt(r -> -h2h.get(r.getTeamId())[0])
                        .thenComparingInt(r -> -h2h.get(r.getTeamId())[1])
                        .thenComparingInt(r -> -h2h.get(r.getTeamId())[2])
                        .thenComparing(StandingRow::getTeamName))
                .collect(Collectors.toList());
    }

    @Override
    public boolean hasLeagueStarted(UUID leagueId) {
        return matchRepository.hasPlayedMatchesForLeague(leagueId, LocalDateTime.now());
    }

    private int[] computeLiveScore(MatchDto m, LocalDateTime now) {
        long realSec = Duration.between(m.getPlayedAt(), now).getSeconds();
        int hs = 0, as = 0;
        for (GoalDto g : m.getGoalTimeline()) {
            if (goalRevealed(g, realSec)) {
                if (g.isHomeGoal()) hs++; else as++;
            }
        }
        return new int[]{hs, as};
    }

    private boolean goalRevealed(GoalDto g, long realSec) {
        if (g.getHalf() == null) return false;
        if (g.getOffsetSeconds() == null && g.getMinute() == null) return false;
        int offset = g.getOffsetSeconds() != null ? g.getOffsetSeconds() : (g.getMinute() - 1) * 60;
        long revealSec = g.getHalf() == Half.FIRST ? offset : 1500 + offset;
        return revealSec <= realSec;
    }

    private void tally(Map<UUID, PlayerStatRow> map, UUID playerId, String playerName, UUID teamId, String teamName, String teamCity) {
        PlayerStatRow row = map.get(playerId);
        if (row == null) {
            row = new PlayerStatRow();
            row.setPlayerId(playerId);
            row.setPlayerName(playerName);
            row.setTeamId(teamId);
            row.setTeamName(teamName);
            row.setTeamCity(teamCity);
            map.put(playerId, row);
        }
        row.setCount(row.getCount() + 1);
    }

    private List<PlayerStatRow> topN(Map<UUID, PlayerStatRow> map, int n) {
        return map.values().stream()
                .sorted(Comparator.<PlayerStatRow>comparingInt(r -> -r.getCount())
                        .thenComparing(PlayerStatRow::getPlayerName))
                .limit(n)
                .toList();
    }

    private League getLeagueOrThrow(UUID id) {
        return leagueRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("League with id %s not found".formatted(id)));
    }

    private void mapToEntity(LeagueDto leagueDto, League league) {
        league.setName(leagueDto.getName());
        league.setScheduleStartDate(leagueDto.getScheduleStartDate());
        league.setScheduleStartTime(leagueDto.getScheduleStartTime());
    }

    private LeagueDto toDto(League league) {
        LeagueDto leagueDto = new LeagueDto();
        leagueDto.setId(league.getId());
        leagueDto.setName(league.getName());
        leagueDto.setScheduleStartDate(league.getScheduleStartDate());
        leagueDto.setScheduleStartTime(league.getScheduleStartTime());
        leagueDto.setTeamCount(league.getTeams().size());
        leagueDto.setTotalMatches(matchRepository.countByLeagueId(league.getId()));
        leagueDto.setPlayedMatches(matchRepository.countPlayedByLeagueId(league.getId(), LocalDateTime.now()));
        return leagueDto;
    }
}
