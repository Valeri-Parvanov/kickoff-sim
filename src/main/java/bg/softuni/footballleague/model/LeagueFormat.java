package bg.softuni.footballleague.model;

import java.util.Arrays;
import java.util.Optional;

public enum LeagueFormat {

    SIX(6, 3),
    EIGHT(8, 2),
    TEN(10, 2),
    SIXTEEN(16, 1);

    private final int teamCount;
    private final int cycles;

    LeagueFormat(int teamCount, int cycles) {
        this.teamCount = teamCount;
        this.cycles = cycles;
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
        return switch (cycles) {
            case 1 -> "Single round-robin (each opponent once)";
            case 2 -> "Double round-robin (each opponent twice)";
            case 3 -> "Triple round-robin (each opponent three times)";
            default -> cycles + "× round-robin";
        };
    }

    public static Optional<LeagueFormat> forTeamCount(int count) {
        return Arrays.stream(values())
                .filter(f -> f.teamCount == count)
                .findFirst();
    }
}
