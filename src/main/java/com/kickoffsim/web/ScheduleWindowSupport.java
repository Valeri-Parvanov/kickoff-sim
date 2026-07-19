package com.kickoffsim.web;

import java.time.LocalTime;
import java.util.Optional;

public final class ScheduleWindowSupport {

    private static final LocalTime LATEST = LocalTime.of(23, 30);

    private ScheduleWindowSupport() {
    }

    public static Optional<String> checkLastMatchTooLate(int teamCount, LocalTime startTime) {
        int matchesPerRound = teamCount / 2;
        if (matchesPerRound <= 1) {
            return Optional.empty();
        }
        LocalTime lastStart = startTime.plusMinutes((matchesPerRound - 1) * 60L);
        if (!lastStart.isAfter(LATEST)) {
            return Optional.empty();
        }
        LocalTime maxStart = LATEST.minusMinutes((matchesPerRound - 1) * 60L);
        return Optional.of("With " + teamCount + " teams, last match starts at " + lastStart
                + ". Use " + maxStart + " or earlier, or move the schedule to the next day.");
    }
}
