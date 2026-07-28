package com.kickoffsim.dto;

public record RoundRecapStandingData(
        int position,
        String team,
        int played,
        int wins,
        int draws,
        int losses,
        int goalsFor,
        int goalsAgainst,
        int goalDifference,
        int points,
        boolean champion) {
}
