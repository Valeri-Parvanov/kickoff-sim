package com.kickoffsim.dto;

import java.util.List;

public record RoundRecapMatchData(
        String homeTeam,
        String awayTeam,
        int homeScore,
        int awayScore,
        List<String> goals,
        String id) {

    public RoundRecapMatchData(String homeTeam, String awayTeam, int homeScore, int awayScore, List<String> goals) {
        this(homeTeam, awayTeam, homeScore, awayScore, goals, null);
    }
}
