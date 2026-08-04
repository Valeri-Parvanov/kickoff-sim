package com.kickoffsim.dto;

import java.util.List;

public record MatchFact(
        String id,
        Integer round,
        String homeTeam,
        String awayTeam,
        int homeScore,
        int awayScore,
        List<GoalFact> goals) {

    public MatchFact {
        goals = goals == null ? List.of() : List.copyOf(goals);
    }

    public int totalGoals() {
        return homeScore + awayScore;
    }

    public int margin() {
        return Math.abs(homeScore - awayScore);
    }

    public boolean draw() {
        return homeScore == awayScore;
    }

    public boolean homeWon() {
        return homeScore > awayScore;
    }

    public boolean awayWon() {
        return awayScore > homeScore;
    }

    public String winner() {
        if (homeWon()) {
            return homeTeam;
        }
        return awayWon() ? awayTeam : null;
    }

    public String loser() {
        if (homeWon()) {
            return awayTeam;
        }
        return awayWon() ? homeTeam : null;
    }

    public int winnerScore() {
        return Math.max(homeScore, awayScore);
    }

    public int loserScore() {
        return Math.min(homeScore, awayScore);
    }

    public boolean hasTimeline() {
        long home = goals.stream().filter(GoalFact::homeGoal).count();
        return !goals.isEmpty() && home == homeScore && goals.size() - home == awayScore;
    }
}
