package com.kickoffsim.web;

import com.kickoffsim.dto.GoalDto;
import com.kickoffsim.dto.MatchDto;
import com.kickoffsim.model.Half;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LiveMatchJsSupportTest {

    private MatchDto match(List<GoalDto> goals) {
        MatchDto match = new MatchDto();
        match.setId(UUID.randomUUID());
        match.setHomeTeamId(UUID.randomUUID());
        match.setAwayTeamId(UUID.randomUUID());
        match.setPlayedAt(LocalDateTime.now().minusMinutes(10));
        match.getGoalTimeline().addAll(goals);
        return match;
    }

    @Test
    void toJsEntry_goalWithOffsetSeconds_includesRealValue() {
        GoalDto g = new GoalDto();
        g.setMinute(5);
        g.setOffsetSeconds(273);
        g.setHalf(Half.FIRST);
        g.setHomeGoal(true);
        g.setRunningHomeScore(1);
        g.setRunningAwayScore(0);

        Map<String, Object> entry = LiveMatchJsSupport.toJsEntry(match(List.of(g)), LocalDateTime.now());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> goals = (List<Map<String, Object>>) entry.get("goals");
        assertThat(goals.get(0).get("offsetSeconds")).isEqualTo(273);
        assertThat(goals.get(0).get("minute")).isEqualTo(5);
        assertThat(goals.get(0).get("half")).isEqualTo("FIRST");
    }

    @Test
    void toJsEntry_goalWithNullFields_appliesDefaults() {
        Map<String, Object> entry = LiveMatchJsSupport.toJsEntry(match(List.of(new GoalDto())), LocalDateTime.now());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> goals = (List<Map<String, Object>>) entry.get("goals");
        assertThat(goals.get(0).get("offsetSeconds")).isEqualTo(0);
        assertThat(goals.get(0).get("minute")).isEqualTo(0);
        assertThat(goals.get(0).get("half")).isEqualTo("FIRST");
        assertThat(goals.get(0).get("rh")).isEqualTo(0);
        assertThat(goals.get(0).get("ra")).isEqualTo(0);
    }

    @Test
    void toJsEntry_populatesMatchLevelFields() {
        MatchDto match = match(List.of());
        LocalDateTime now = match.getPlayedAt().plusMinutes(3);

        Map<String, Object> entry = LiveMatchJsSupport.toJsEntry(match, now);

        assertThat(entry.get("id")).isEqualTo(match.getId().toString());
        assertThat(entry.get("homeTeamId")).isEqualTo(match.getHomeTeamId().toString());
        assertThat(entry.get("awayTeamId")).isEqualTo(match.getAwayTeamId().toString());
        assertThat(entry.get("elapsedMin")).isEqualTo(3L);
        assertThat(entry.get("elapsedSec")).isEqualTo(180L);
    }

    @Test
    void toJs_mapsEveryMatch() {
        MatchDto a = match(List.of());
        MatchDto b = match(List.of());

        List<Map<String, Object>> result = LiveMatchJsSupport.toJs(List.of(a, b), LocalDateTime.now());

        assertThat(result).hasSize(2);
    }

    @Test
    void elapsedByMatchId_computesMinutesPerMatch() {
        MatchDto match = match(List.of());
        LocalDateTime now = match.getPlayedAt().plusMinutes(7);

        Map<UUID, Long> result = LiveMatchJsSupport.elapsedByMatchId(List.of(match), now);

        assertThat(result.get(match.getId())).isEqualTo(7L);
    }
}
