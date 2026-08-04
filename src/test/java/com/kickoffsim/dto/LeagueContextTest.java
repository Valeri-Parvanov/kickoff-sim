package com.kickoffsim.dto;

import com.kickoffsim.dto.LeagueContext.TeamForm;
import com.kickoffsim.dto.LeagueContext.TitleRace;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LeagueContextTest {

    @Test
    void nullForms_becomeAnEmptyTable() {
        LeagueContext context = new LeagueContext(1, 3, null, null, null);

        assertThat(context.forms()).isEmpty();
        assertThat(context.fieldSize()).isZero();
        assertThat(context.formOf("A")).isNull();
        assertThat(context.positionOf("A")).isZero();
        assertThat(context.isTopTier("A")).isFalse();
        assertThat(context.isBottomTier("A")).isFalse();
    }

    @Test
    void teamForm_withoutAPreviousPosition_hasNoClimb() {
        TeamForm form = new TeamForm("A", 1, 0, 9, 3, "WWW", 3, 3, 0);

        assertThat(form.climb()).isZero();
        assertThat(form.rising()).isFalse();
        assertThat(form.falling()).isFalse();
    }

    @Test
    void titleRace_isTightOnlyWhenLiveAndClose() {
        assertThat(new TitleRace("A", 9, "B", 8, 1, 3, false).tight()).isTrue();
        assertThat(new TitleRace("A", 20, "B", 10, 10, 3, false).tight()).isFalse();
        assertThat(new TitleRace("A", 20, "B", 5, 15, 1, true).tight()).isFalse();
    }
}
