package com.kickoffsim.web;

import com.kickoffsim.dto.MatchDto;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class LiveMatchJsSupport {

    private LiveMatchJsSupport() {
    }

    public static List<Map<String, Object>> toJs(List<MatchDto> matches, LocalDateTime now) {
        return matches.stream()
                .map(m -> toJsEntry(m, now))
                .toList();
    }

    public static Map<String, Object> toJsEntry(MatchDto match, LocalDateTime now) {
        List<Map<String, Object>> goals = match.getGoalTimeline().stream()
                .map(g -> Map.<String, Object>of(
                        "minute", g.getMinute() != null ? g.getMinute() : 0,
                        "offsetSeconds", g.getOffsetSeconds() != null ? g.getOffsetSeconds() : 0,
                        "half", g.getHalf() != null ? g.getHalf().name() : "FIRST",
                        "homeGoal", g.isHomeGoal(),
                        "rh", g.getRunningHomeScore() != null ? g.getRunningHomeScore() : 0,
                        "ra", g.getRunningAwayScore() != null ? g.getRunningAwayScore() : 0))
                .toList();
        return Map.<String, Object>of(
                "id", match.getId().toString(),
                "homeTeamId", match.getHomeTeamId().toString(),
                "awayTeamId", match.getAwayTeamId().toString(),
                "elapsedMin", Duration.between(match.getPlayedAt(), now).toMinutes(),
                "elapsedSec", Duration.between(match.getPlayedAt(), now).getSeconds(),
                "goals", goals);
    }

    public static Map<UUID, Long> elapsedByMatchId(List<MatchDto> matches, LocalDateTime now) {
        return matches.stream()
                .collect(Collectors.toMap(MatchDto::getId, m -> Duration.between(m.getPlayedAt(), now).toMinutes()));
    }
}
