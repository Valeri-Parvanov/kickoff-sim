package com.kickoffsim.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecapPlayerTest {

    @Test
    void firstAndLastName_giveTwoInitials() {
        assertThat(player("Kostadin Spasov").getInitials()).isEqualTo("KS");
    }

    @Test
    void singleName_givesOneInitial() {
        assertThat(player("Petar").getInitials()).isEqualTo("P");
    }

    @Test
    void threeNames_stopAtTwoInitials() {
        assertThat(player("Georgi Ivanov Petrov").getInitials()).isEqualTo("GI");
    }

    @Test
    void extraWhitespace_isIgnored() {
        assertThat(player("  ilian   penchev  ").getInitials()).isEqualTo("IP");
    }

    @Test
    void blankName_fallsBackToQuestionMark() {
        assertThat(player("   ").getInitials()).isEqualTo("?");
    }

    @Test
    void nullName_fallsBackToQuestionMark() {
        assertThat(player(null).getInitials()).isEqualTo("?");
    }

    private RecapPlayer player(String name) {
        return new RecapPlayer(name, "Alpha", 1, 0);
    }
}
