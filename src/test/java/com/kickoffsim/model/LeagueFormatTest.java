package com.kickoffsim.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LeagueFormatTest {

    @Test
    void six_hasExpectedDerivedValues() {
        assertThat(LeagueFormat.SIX.getTeamCount()).isEqualTo(6);
        assertThat(LeagueFormat.SIX.getCycles()).isEqualTo(3);
        assertThat(LeagueFormat.SIX.getRoundsPerCycle()).isEqualTo(5);
        assertThat(LeagueFormat.SIX.getTotalRounds()).isEqualTo(15);
        assertThat(LeagueFormat.SIX.getMatchesPerRound()).isEqualTo(3);
        assertThat(LeagueFormat.SIX.getTotalMatches()).isEqualTo(45);
        assertThat(LeagueFormat.SIX.getCycleNameKey()).isEqualTo("leagues.format.triple");
    }

    @Test
    void eight_hasExpectedDerivedValues() {
        assertThat(LeagueFormat.EIGHT.getTeamCount()).isEqualTo(8);
        assertThat(LeagueFormat.EIGHT.getCycles()).isEqualTo(2);
        assertThat(LeagueFormat.EIGHT.getCycleNameKey()).isEqualTo("leagues.format.double");
    }

    @Test
    void ten_hasExpectedDerivedValues() {
        assertThat(LeagueFormat.TEN.getTeamCount()).isEqualTo(10);
        assertThat(LeagueFormat.TEN.getCycles()).isEqualTo(2);
        assertThat(LeagueFormat.TEN.getCycleNameKey()).isEqualTo("leagues.format.double");
    }

    @Test
    void sixteen_hasExpectedDerivedValues() {
        assertThat(LeagueFormat.SIXTEEN.getTeamCount()).isEqualTo(16);
        assertThat(LeagueFormat.SIXTEEN.getCycles()).isEqualTo(1);
        assertThat(LeagueFormat.SIXTEEN.getRoundsPerCycle()).isEqualTo(15);
        assertThat(LeagueFormat.SIXTEEN.getTotalRounds()).isEqualTo(15);
        assertThat(LeagueFormat.SIXTEEN.getMatchesPerRound()).isEqualTo(8);
        assertThat(LeagueFormat.SIXTEEN.getTotalMatches()).isEqualTo(120);
        assertThat(LeagueFormat.SIXTEEN.getCycleNameKey()).isEqualTo("leagues.format.single");
    }

    @Test
    void forTeamCount_knownCount_returnsMatchingFormat() {
        assertThat(LeagueFormat.forTeamCount(8)).contains(LeagueFormat.EIGHT);
    }

    @Test
    void forTeamCount_unknownCount_returnsEmpty() {
        assertThat(LeagueFormat.forTeamCount(4)).isEmpty();
    }
}
