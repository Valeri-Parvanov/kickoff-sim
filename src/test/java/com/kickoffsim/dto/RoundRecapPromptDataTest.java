package com.kickoffsim.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoundRecapPromptDataTest {

    @Test
    void legacyConstructor_leavesFactsAndMemoryNullSafe() {
        RoundRecapPromptData data = new RoundRecapPromptData("L", 1, "en", "English",
                List.of(), List.of(), 3, List.of(), List.of(), null);

        assertThat(data.matchFacts()).isEmpty();
        assertThat(data.memory()).isEqualTo(RecapMemory.empty());
        assertThat(data.context()).isNull();
    }

    @Test
    void fullConstructor_exposesFactsAndMemory() {
        MatchFact fact = new MatchFact("m", 1, "A", "B", 1, 0, List.of());
        RecapMemory memory = new RecapMemory(List.of("UPSET"), List.of(), true);

        RoundRecapPromptData data = new RoundRecapPromptData("L", 1, "en", "English",
                List.of(), List.of(), 3, List.of(), List.of(), null, List.of(fact), null, memory);

        assertThat(data.matchFacts()).containsExactly(fact);
        assertThat(data.memory()).isSameAs(memory);
    }
}
