package com.kickoffsim.dto;

import java.util.List;

public record RoundRecapMatchData(
        String homeTeam,
        String awayTeam,
        int homeScore,
        int awayScore,
        List<String> goals) {
}
