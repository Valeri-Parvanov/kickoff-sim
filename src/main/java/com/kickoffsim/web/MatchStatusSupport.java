package com.kickoffsim.web;

import com.kickoffsim.dto.GoalDto;
import com.kickoffsim.dto.MatchDto;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public final class MatchStatusSupport {

    private MatchStatusSupport() {
    }

    public static List<MatchDto> sortByStatus(List<MatchDto> matches, LocalDateTime now, LocalDateTime liveThreshold) {
        return matches.stream()
                .sorted((a, b) -> {
                    int pa = statusPriority(a, now, liveThreshold);
                    int pb = statusPriority(b, now, liveThreshold);
                    if (pa != pb) return Integer.compare(pa, pb);
                    if (pa == 2) return b.getPlayedAt().compareTo(a.getPlayedAt());
                    return a.getPlayedAt().compareTo(b.getPlayedAt());
                })
                .toList();
    }

    private static int statusPriority(MatchDto m, LocalDateTime now, LocalDateTime liveThreshold) {
        if (m.getPlayedAt().isBefore(now) && m.getPlayedAt().isAfter(liveThreshold)) return 0;
        if (!m.getPlayedAt().isBefore(now)) return 1;
        return 2;
    }

    public static String liveStatusMessage(MatchDto match, LocalDateTime now) {
        String home = match.getHomeTeamName();
        String away = match.getAwayTeamName();
        long realMin = Duration.between(match.getPlayedAt(), now).toMinutes();
        String phase;
        int maxMin;
        if (realMin <= 20)      { phase = "FIRST";  maxMin = (int) realMin; }
        else if (realMin <= 25) { phase = "HT";     maxMin = 20; }
        else if (realMin <= 45) { phase = "SECOND"; maxMin = (int) (realMin - 25); }
        else                    { phase = "FT";     maxMin = 20; }

        int hs = 0, as = 0;
        for (GoalDto g : match.getGoalTimeline()) {
            int minute = g.getMinute() != null ? g.getMinute() : 0;
            int rh = g.getRunningHomeScore() != null ? g.getRunningHomeScore() : 0;
            int ra = g.getRunningAwayScore() != null ? g.getRunningAwayScore() : 0;
            boolean firstHalf = g.getHalf() != null && "FIRST".equals(g.getHalf().name());
            if (firstHalf) {
                if (!"FIRST".equals(phase) || minute <= maxMin) {
                    hs = rh;
                    as = ra;
                }
            } else {
                int secMax = "SECOND".equals(phase) ? maxMin : ("FT".equals(phase) ? 20 : -1);
                if (secMax >= 0 && minute <= secMax) {
                    hs = rh;
                    as = ra;
                }
            }
        }

        String display = switch (phase) {
            case "FIRST"  -> realMin + "'";
            case "HT"     -> "HT";
            case "SECOND" -> (20 + (realMin - 25)) + "'";
            default        -> "FT";
        };
        String prefix = "FT".equals(phase) ? "Full time: " : "LIVE: ";
        return prefix + home + " " + hs + ":" + as + " " + away + " · " + display;
    }
}
