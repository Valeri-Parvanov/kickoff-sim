package com.kickoffsim.service.impl;

import com.kickoffsim.dto.LeagueContext;
import com.kickoffsim.dto.LeagueContext.SurvivalRace;
import com.kickoffsim.dto.LeagueContext.TeamForm;
import com.kickoffsim.dto.LeagueContext.TitleRace;
import com.kickoffsim.dto.MatchFact;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LeagueContextBuilder {

    private static final int WIN_POINTS = 3;

    private static final int DRAW_POINTS = 1;

    private static final int FORM_WINDOW = 5;

    private static final int TITLE_STAKE = 3;

    public LeagueContext build(List<MatchFact> completed, int roundNumber, int totalRounds) {
        List<MatchFact> upToRound = completed == null ? List.of() : completed.stream()
                .filter(match -> match.round() != null && match.round() <= roundNumber)
                .toList();

        List<Row> current = table(upToRound);
        Map<String, Integer> previous = positions(table(upToRound.stream()
                .filter(match -> match.round() < roundNumber)
                .toList()));

        List<TeamForm> forms = new ArrayList<>();
        for (int i = 0; i < current.size(); i++) {
            Row row = current.get(i);
            Trail trail = trailOf(upToRound, row.team());
            forms.add(new TeamForm(row.team(), i + 1, previous.getOrDefault(row.team(), 0),
                    row.points(), row.played(), trail.results(),
                    trail.winStreak(), trail.unbeatenStreak(), trail.winlessStreak()));
        }

        return new LeagueContext(roundNumber, totalRounds, forms,
                titleRace(current, roundNumber, totalRounds), survivalRace(current));
    }

    private TitleRace titleRace(List<Row> table, int roundNumber, int totalRounds) {
        if (table.size() < 2) {
            return null;
        }
        Row leader = table.get(0);
        Row second = table.get(1);
        int gap = leader.points() - second.points();
        int remaining = Math.max(0, totalRounds - roundNumber);
        boolean decided = gap > remaining * TITLE_STAKE;
        return new TitleRace(leader.team(), leader.points(), second.team(), second.points(),
                gap, remaining, decided);
    }

    private SurvivalRace survivalRace(List<Row> table) {
        if (table.size() < 2) {
            return null;
        }
        Row last = table.get(table.size() - 1);
        Row safe = table.get(table.size() - 2);
        return new SurvivalRace(last.team(), last.points(), safe.team(),
                safe.points() - last.points());
    }

    private Map<String, Integer> positions(List<Row> table) {
        Map<String, Integer> positions = new LinkedHashMap<>();
        for (int i = 0; i < table.size(); i++) {
            positions.put(table.get(i).team(), i + 1);
        }
        return positions;
    }

    private List<Row> table(List<MatchFact> matches) {
        Map<String, Tally> tallies = new LinkedHashMap<>();
        for (MatchFact match : matches) {
            Tally home = tallies.computeIfAbsent(match.homeTeam(), key -> new Tally());
            Tally away = tallies.computeIfAbsent(match.awayTeam(), key -> new Tally());
            home.add(match.homeScore(), match.awayScore());
            away.add(match.awayScore(), match.homeScore());
        }
        return tallies.entrySet().stream()
                .map(entry -> entry.getValue().toRow(entry.getKey()))
                .sorted(Comparator.comparingInt(Row::points).reversed()
                        .thenComparing(Comparator.comparingInt(Row::goalDifference).reversed())
                        .thenComparing(Comparator.comparingInt(Row::goalsFor).reversed())
                        .thenComparing(Row::team))
                .toList();
    }

    private Trail trailOf(List<MatchFact> matches, String team) {
        List<Character> results = new ArrayList<>();
        for (MatchFact match : matches) {
            if (match.homeTeam().equals(team)) {
                results.add(outcome(match.homeScore(), match.awayScore()));
            } else if (match.awayTeam().equals(team)) {
                results.add(outcome(match.awayScore(), match.homeScore()));
            }
        }
        int winStreak = 0;
        int unbeatenStreak = 0;
        int winlessStreak = 0;
        boolean winOpen = true;
        boolean unbeatenOpen = true;
        boolean winlessOpen = true;
        for (int i = results.size() - 1; i >= 0; i--) {
            char result = results.get(i);
            if (winOpen && result == 'W') {
                winStreak++;
            } else {
                winOpen = false;
            }
            if (unbeatenOpen && result != 'L') {
                unbeatenStreak++;
            } else {
                unbeatenOpen = false;
            }
            if (winlessOpen && result != 'W') {
                winlessStreak++;
            } else {
                winlessOpen = false;
            }
        }
        int from = Math.max(0, results.size() - FORM_WINDOW);
        StringBuilder recent = new StringBuilder();
        for (int i = from; i < results.size(); i++) {
            recent.append(results.get(i));
        }
        return new Trail(recent.toString(), winStreak, unbeatenStreak, winlessStreak);
    }

    private char outcome(int scored, int conceded) {
        if (scored > conceded) {
            return 'W';
        }
        return scored < conceded ? 'L' : 'D';
    }

    private static final class Tally {

        private int played;
        private int wins;
        private int draws;
        private int losses;
        private int goalsFor;
        private int goalsAgainst;

        private void add(int scored, int conceded) {
            played++;
            goalsFor += scored;
            goalsAgainst += conceded;
            if (scored > conceded) {
                wins++;
            } else if (scored < conceded) {
                losses++;
            } else {
                draws++;
            }
        }

        private Row toRow(String team) {
            return new Row(team, played, wins * WIN_POINTS + draws * DRAW_POINTS,
                    goalsFor, goalsAgainst);
        }
    }

    private record Row(String team, int played, int points, int goalsFor, int goalsAgainst) {

        private int goalDifference() {
            return goalsFor - goalsAgainst;
        }
    }

    private record Trail(String results, int winStreak, int unbeatenStreak, int winlessStreak) {
    }
}
