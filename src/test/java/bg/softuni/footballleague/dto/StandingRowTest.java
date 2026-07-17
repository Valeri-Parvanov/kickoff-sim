package bg.softuni.footballleague.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StandingRowTest {

    @Test
    void points_andGoalDiff_areComputed() {
        StandingRow row = new StandingRow();
        row.setWins(2);
        row.setDraws(1);
        row.setLosses(1);
        row.setGoalsFor(5);
        row.setGoalsAgainst(3);

        assertThat(row.getPoints()).isEqualTo(7);
        assertThat(row.getGoalDiff()).isEqualTo(2);
    }
}
