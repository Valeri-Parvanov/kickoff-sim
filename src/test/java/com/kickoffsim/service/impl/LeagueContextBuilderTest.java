package com.kickoffsim.service.impl;

import com.kickoffsim.dto.LeagueContext;
import com.kickoffsim.dto.LeagueContext.TeamForm;
import com.kickoffsim.dto.MatchFact;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LeagueContextBuilderTest {

    private final LeagueContextBuilder builder = new LeagueContextBuilder();

    @Test
    void build_noMatches_yieldsEmptyContext() {
        LeagueContext context = builder.build(List.of(), 1, 3);

        assertThat(context.forms()).isEmpty();
        assertThat(context.titleRace()).isNull();
        assertThat(context.survivalRace()).isNull();
        assertThat(context.fieldSize()).isZero();
        assertThat(context.positionOf("A")).isZero();
        assertThat(context.formOf("A")).isNull();
    }

    @Test
    void build_nullMatches_yieldsEmptyContext() {
        assertThat(builder.build(null, 1, 3).forms()).isEmpty();
    }

    @Test
    void build_ranksTeamsAndTracksPositionChanges() {
        LeagueContext context = builder.build(twoRounds(), 2, 3);

        assertThat(context.forms()).extracting(TeamForm::team)
                .containsExactly("A", "B", "C", "D");
        TeamForm a = context.formOf("A");
        assertThat(a.position()).isEqualTo(1);
        assertThat(a.points()).isEqualTo(6);
        assertThat(a.recentResults()).isEqualTo("WW");
        assertThat(a.winStreak()).isEqualTo(2);
        assertThat(a.climb()).isZero();

        TeamForm b = context.formOf("B");
        assertThat(b.position()).isEqualTo(2);
        assertThat(b.previousPosition()).isEqualTo(4);
        assertThat(b.rising()).isTrue();
        assertThat(b.recentResults()).isEqualTo("LW");

        TeamForm c = context.formOf("C");
        assertThat(c.falling()).isTrue();
        assertThat(c.winlessStreak()).isEqualTo(1);

        TeamForm d = context.formOf("D");
        assertThat(d.winlessStreak()).isEqualTo(2);
        assertThat(d.unbeatenStreak()).isZero();
    }

    @Test
    void build_openTitleRace_isTightButNotDecided() {
        LeagueContext.TitleRace race = builder.build(twoRounds(), 2, 3).titleRace();

        assertThat(race.leader()).isEqualTo("A");
        assertThat(race.second()).isEqualTo("B");
        assertThat(race.gap()).isEqualTo(3);
        assertThat(race.remaining()).isEqualTo(1);
        assertThat(race.decided()).isFalse();
        assertThat(race.tight()).isTrue();
    }

    @Test
    void build_lastRound_titleRaceIsDecided() {
        LeagueContext.TitleRace race = builder.build(twoRounds(), 2, 2).titleRace();

        assertThat(race.decided()).isTrue();
        assertThat(race.tight()).isFalse();
    }

    @Test
    void build_reportsSurvivalRace() {
        LeagueContext.SurvivalRace survival = builder.build(twoRounds(), 2, 3).survivalRace();

        assertThat(survival.last()).isEqualTo("D");
        assertThat(survival.safe()).isEqualTo("C");
        assertThat(survival.cushion()).isEqualTo(3);
    }

    @Test
    void context_classifiesTopAndBottomTiers() {
        LeagueContext context = builder.build(twoRounds(), 2, 3);

        assertThat(context.isTopTier("A")).isTrue();
        assertThat(context.isTopTier("B")).isFalse();
        assertThat(context.isBottomTier("D")).isTrue();
        assertThat(context.isBottomTier("A")).isFalse();
        assertThat(context.isTopTier("Unknown")).isFalse();
        assertThat(context.isBottomTier("Unknown")).isFalse();
    }

    @Test
    void build_ignoresMatchesFromLaterRounds() {
        List<MatchFact> facts = List.of(
                mf(1, "A", "B", 2, 0),
                mf(1, "C", "D", 1, 0),
                mf(2, "A", "C", 1, 0),
                mf(2, "B", "D", 3, 0),
                mf(3, "A", "E", 4, 0));

        LeagueContext context = builder.build(facts, 2, 3);

        assertThat(context.formOf("E")).isNull();
    }

    @Test
    void build_countsDrawsAndSkipsMatchesWithoutARound() {
        MatchFact draw = mf(1, "A", "B", 1, 1);
        MatchFact decisive = mf(1, "C", "D", 2, 0);
        MatchFact roundless = new MatchFact("x", null, "E", "F", 3, 0, List.of());

        LeagueContext context = builder.build(List.of(draw, decisive, roundless), 1, 3);

        assertThat(context.formOf("A").points()).isEqualTo(1);
        assertThat(context.formOf("A").recentResults()).isEqualTo("D");
        assertThat(context.formOf("E")).isNull();
    }

    private List<MatchFact> twoRounds() {
        return List.of(
                mf(1, "A", "B", 2, 0),
                mf(1, "C", "D", 1, 0),
                mf(2, "A", "C", 1, 0),
                mf(2, "B", "D", 3, 0));
    }

    private MatchFact mf(int round, String home, String away, int homeScore, int awayScore) {
        return new MatchFact(home + away + round, round, home, away, homeScore, awayScore, List.of());
    }
}
