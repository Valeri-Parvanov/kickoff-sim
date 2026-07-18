package com.kickoffsim.model;

import java.util.Arrays;
import java.util.Optional;

public enum LeagueFormat {

    SIX(6, 3, "Triple round-robin (each opponent three times)"),
    EIGHT(8, 2, "Double round-robin (each opponent twice)"),
    TEN(10, 2, "Double round-robin (each opponent twice)"),
    SIXTEEN(16, 1, "Single round-robin (each opponent once)");

    private final int teamCount;
    private final int cycles;
    private final String cycleName;

    LeagueFormat(int teamCount, int cycles, String cycleName) {
        this.teamCount = teamCount;
        this.cycles = cycles;
        this.cycleName = cycleName;
    }

    public int getTeamCount() {
        return teamCount;
    }

    public int getCycles() {
        return cycles;
    }

    public int getRoundsPerCycle() {
        return teamCount - 1;
    }

    public int getTotalRounds() {
        return (teamCount - 1) * cycles;
    }

    public int getMatchesPerRound() {
        return teamCount / 2;
    }

    public int getTotalMatches() {
        return (teamCount * (teamCount - 1) / 2) * cycles;
    }

    public String getCycleName() {
        return cycleName;
    }

    public static Optional<LeagueFormat> forTeamCount(int count) {
        return Arrays.stream(values())
                .filter(f -> f.teamCount == count)
                .findFirst();
    }
}
