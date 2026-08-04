package com.kickoffsim.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecapMemoryTest {

    @Test
    void empty_hasNoHistory() {
        RecapMemory memory = RecapMemory.empty();

        assertThat(memory.recentAngles()).isEmpty();
        assertThat(memory.recentHeadlines()).isEmpty();
        assertThat(memory.regeneration()).isFalse();
    }

    @Test
    void constructor_toleratesNullLists() {
        RecapMemory memory = new RecapMemory(null, null, true);

        assertThat(memory.recentAngles()).isEmpty();
        assertThat(memory.recentHeadlines()).isEmpty();
        assertThat(memory.regeneration()).isTrue();
    }

    @Test
    void angleRecency_isHigherForMoreRecentAngles() {
        RecapMemory memory = new RecapMemory(List.of("UPSET", "SURGE"), List.of(), false);

        assertThat(memory.angleRecency("UPSET")).isEqualTo(2);
        assertThat(memory.angleRecency("SURGE")).isEqualTo(1);
        assertThat(memory.angleRecency("MVP")).isEqualTo(-1);
    }

    @Test
    void hasHeadline_matchesStoredHeadlines() {
        RecapMemory memory = new RecapMemory(List.of(), List.of("Alpha stun Beta"), false);

        assertThat(memory.hasHeadline("Alpha stun Beta")).isTrue();
        assertThat(memory.hasHeadline("Something else")).isFalse();
    }
}
