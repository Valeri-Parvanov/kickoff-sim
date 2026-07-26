package com.kickoffsim.model;

import java.util.Arrays;
import java.util.Optional;

public enum LeagueFormat {

    SIX(6, 3, "leagues.format.triple"),
    EIGHT(8, 2, "leagues.format.double"),
    TEN(10, 2, "leagues.format.double"),
    SIXTEEN(16, 1, "leagues.format.single");

    private final int teamCount;
    private final int cycles;
    private final String cycleNameKey;

    LeagueFormat(int teamCount, int cycles, String cycleNameKey) {
        this.teamCount = teamCount;
        this.cycles = cycles;
        this.cycleNameKey = cycleNameKey;
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

    public String getCycleNameKey() {
        return cycleNameKey;
    }

    public static Optional<LeagueFormat> forTeamCount(int count) {
        return Arrays.stream(values())
                .filter(f -> f.teamCount == count)
                .findFirst();
    }
}
