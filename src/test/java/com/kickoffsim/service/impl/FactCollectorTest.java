package com.kickoffsim.service.impl;

import com.kickoffsim.dto.GoalDto;
import com.kickoffsim.dto.GoalFact;
import com.kickoffsim.dto.MatchDto;
import com.kickoffsim.dto.MatchFact;
import com.kickoffsim.model.Half;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FactCollectorTest {

    private final FactCollector collector = new FactCollector();

    @Test
    void collect_nullMatches_returnsEmpty() {
        assertThat(collector.collect(null, true)).isEmpty();
    }

    @Test
    void collect_ordersByPlayedAtThenNullsLast() {
        MatchDto early = match("Alpha", null, "Beta", null, 1, 0, LocalDateTime.of(2026, 1, 1, 12, 0));
        MatchDto late = match("Gama", null, "Delta", null, 2, 0, LocalDateTime.of(2026, 1, 2, 12, 0));
        MatchDto undated = match("Epsilon", null, "Zeta", null, 0, 0, null);

        List<MatchFact> facts = collector.collect(List.of(late, undated, early), false);

        assertThat(facts).extracting(MatchFact::homeTeam)
                .containsExactly("Alpha", "Gama", "Epsilon");
    }

    @Test
    void toMatchFact_withoutGoals_leavesGoalsEmpty() {
        MatchDto match = match("Alpha", "Sofia", "Beta", "Varna", 3, 1,
                LocalDateTime.of(2026, 1, 1, 12, 0));
        match.getGoalTimeline().add(goal(true, "Petar", "Ivan", 10, Half.FIRST, false, false));

        MatchFact fact = collector.toMatchFact(match, false);

        assertThat(fact.goals()).isEmpty();
        assertThat(fact.homeTeam()).isEqualTo("Alpha (Sofia)");
        assertThat(fact.awayTeam()).isEqualTo("Beta (Varna)");
    }

    @Test
    void toMatchFact_withGoals_mapsEveryField() {
        MatchDto match = match("Alpha", "Sofia", "Beta", null, 2, 1,
                LocalDateTime.of(2026, 1, 1, 12, 0));
        match.setRoundNumber(4);
        match.getGoalTimeline().add(goal(true, "Petar", "Ivan", 12, Half.FIRST, true, false));
        match.getGoalTimeline().add(goal(false, "Georgi", null, 25, Half.SECOND, false, true));

        MatchFact fact = collector.toMatchFact(match, true);

        assertThat(fact.round()).isEqualTo(4);
        assertThat(fact.homeTeam()).isEqualTo("Alpha (Sofia)");
        assertThat(fact.awayTeam()).isEqualTo("Beta");
        GoalFact home = fact.goals().get(0);
        assertThat(home.homeGoal()).isTrue();
        assertThat(home.team()).isEqualTo("Alpha (Sofia)");
        assertThat(home.scorer()).isEqualTo("Petar");
        assertThat(home.hasAssist()).isTrue();
        assertThat(home.penalty()).isTrue();
        assertThat(home.secondHalf()).isFalse();
        GoalFact away = fact.goals().get(1);
        assertThat(away.homeGoal()).isFalse();
        assertThat(away.team()).isEqualTo("Beta");
        assertThat(away.hasAssist()).isFalse();
        assertThat(away.ownGoal()).isTrue();
        assertThat(away.secondHalf()).isTrue();
    }

    @Test
    void toMatchFact_nullScores_becomeZeroAndNullId() {
        MatchDto match = new MatchDto();
        match.setHomeTeamName("Alpha");
        match.setAwayTeamName("Beta");

        MatchFact fact = collector.toMatchFact(match, false);

        assertThat(fact.homeScore()).isZero();
        assertThat(fact.awayScore()).isZero();
        assertThat(fact.id()).isNull();
    }

    @Test
    void toMatchFact_blankCity_dropsSuffix() {
        MatchDto match = match("Alpha", "  ", "Beta", "Ruse", 0, 0, LocalDateTime.now());

        MatchFact fact = collector.toMatchFact(match, false);

        assertThat(fact.homeTeam()).isEqualTo("Alpha");
        assertThat(fact.awayTeam()).isEqualTo("Beta (Ruse)");
    }

    @Test
    void matchFact_homeWin_exposesWinnerAndScores() {
        MatchFact fact = new MatchFact("1", 1, "Alpha", "Beta", 4, 1, null);

        assertThat(fact.homeWon()).isTrue();
        assertThat(fact.winner()).isEqualTo("Alpha");
        assertThat(fact.loser()).isEqualTo("Beta");
        assertThat(fact.winnerScore()).isEqualTo(4);
        assertThat(fact.loserScore()).isEqualTo(1);
        assertThat(fact.margin()).isEqualTo(3);
        assertThat(fact.totalGoals()).isEqualTo(5);
        assertThat(fact.draw()).isFalse();
    }

    @Test
    void matchFact_awayWin_exposesWinnerAndLoser() {
        MatchFact fact = new MatchFact("1", 1, "Alpha", "Beta", 0, 2, List.of());

        assertThat(fact.awayWon()).isTrue();
        assertThat(fact.winner()).isEqualTo("Beta");
        assertThat(fact.loser()).isEqualTo("Alpha");
    }

    @Test
    void matchFact_draw_hasNoWinnerOrLoser() {
        MatchFact fact = new MatchFact("1", 1, "Alpha", "Beta", 2, 2, List.of());

        assertThat(fact.draw()).isTrue();
        assertThat(fact.winner()).isNull();
        assertThat(fact.loser()).isNull();
    }

    @Test
    void matchFact_hasTimeline_matchesGoalCounts() {
        MatchFact complete = new MatchFact("1", 1, "Alpha", "Beta", 1, 1, List.of(
                new GoalFact(true, "Alpha", "Petar", null, 10, Half.FIRST, false, false),
                new GoalFact(false, "Beta", "Ivan", null, 20, Half.SECOND, false, false)));
        MatchFact mismatched = new MatchFact("2", 1, "Alpha", "Beta", 3, 0, List.of(
                new GoalFact(true, "Alpha", "Petar", null, 10, Half.FIRST, false, false)));
        MatchFact empty = new MatchFact("3", 1, "Alpha", "Beta", 0, 0, List.of());

        assertThat(complete.hasTimeline()).isTrue();
        assertThat(mismatched.hasTimeline()).isFalse();
        assertThat(empty.hasTimeline()).isFalse();
    }

    @Test
    void matchFact_timelineMatchingHomeButNotAway_isIncomplete() {
        MatchFact fact = new MatchFact("1", 1, "Alpha", "Beta", 1, 1, List.of(
                new GoalFact(true, "Alpha", "Petar", null, 10, Half.FIRST, false, false)));

        assertThat(fact.hasTimeline()).isFalse();
    }

    @Test
    void goalFact_blankAssistIsNoAssist() {
        GoalFact fact = new GoalFact(true, "Alpha", "Petar", "  ", 10, Half.FIRST, false, false);

        assertThat(fact.hasAssist()).isFalse();
    }

    private MatchDto match(String homeName, String homeCity, String awayName, String awayCity,
                           int homeScore, int awayScore, LocalDateTime playedAt) {
        MatchDto match = new MatchDto();
        match.setId(UUID.randomUUID());
        match.setHomeTeamName(homeName);
        match.setHomeTeamCity(homeCity);
        match.setAwayTeamName(awayName);
        match.setAwayTeamCity(awayCity);
        match.setHomeScore(homeScore);
        match.setAwayScore(awayScore);
        match.setPlayedAt(playedAt);
        return match;
    }

    private GoalDto goal(boolean homeGoal, String scorer, String assistant, Integer minute,
                         Half half, boolean penalty, boolean ownGoal) {
        GoalDto goal = new GoalDto();
        goal.setHomeGoal(homeGoal);
        goal.setScorerName(scorer);
        goal.setAssistantName(assistant);
        goal.setMinute(minute);
        goal.setHalf(half);
        goal.setPenalty(penalty);
        goal.setOwnGoal(ownGoal);
        return goal;
    }
}
