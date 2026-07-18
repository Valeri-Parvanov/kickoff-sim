package com.kickoffsim.web;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LogoGeneratorTest {

    @Test
    void generate_shieldShape_coversAllPatternAndEmblemBranches() {
        for (long lo = 0; lo <= 5; lo++) {
            UUID id = new UUID(0L, lo);
            String svg = LogoGenerator.generate("Alpha", id);

            assertThat(svg).startsWith("<svg");
            assertThat(svg).contains("<path d=");
        }
    }

    @Test
    void generate_circleShape_usesCircleElements() {
        UUID id = new UUID(5L, 0L);

        String svg = LogoGenerator.generate("Alpha", id);

        assertThat(svg).contains("<circle cx=\"40\" cy=\"46\" r=\"37\"/>");
    }
}
