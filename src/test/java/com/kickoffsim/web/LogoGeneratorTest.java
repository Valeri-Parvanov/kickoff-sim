package com.kickoffsim.web;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LogoGeneratorTest {

    @Test
    void generate_coversAllShapeRimAndEmblemBranches() {
        for (long hi = 0; hi <= 8; hi++) {
            UUID id = new UUID(hi, 0L);
            String svg = LogoGenerator.generate("Sofia FC", id);

            assertThat(svg).startsWith("<svg");
            assertThat(svg).contains("<defs>");
        }
    }

    @Test
    void generate_circleShape_usesCircleElements() {
        UUID id = new UUID(8L, 0L);

        String svg = LogoGenerator.generate("Sofia FC", id);

        assertThat(svg).contains("<circle cx=\"40\" cy=\"46\" r=\"37\"");
        assertThat(svg).contains("<circle cx=\"40\" cy=\"46\" r=\"32\"");
    }

    @Test
    void generate_appliesDropShadowFilterAndEmblemGradient() {
        UUID id = new UUID(7L, 0L);

        String svg = LogoGenerator.generate("Sofia FC", id);

        assertThat(svg).contains("<feDropShadow");
        assertThat(svg).contains("<g filter=\"url(#s");
        assertThat(svg).contains("fill=\"url(#e");
    }

    @Test
    void generate_shieldShape_usesPathElements() {
        UUID id = new UUID(0L, 0L);

        String svg = LogoGenerator.generate("Sofia FC", id);

        assertThat(svg).contains("<path d=");
    }

    @Test
    void generate_coversSplitDirectionBranches() {
        String splitRight = LogoGenerator.generate("Sofia FC", new UUID(0L, 0L));
        String splitLeft = LogoGenerator.generate("Sofia FC", new UUID(0L, 4L));

        assertThat(splitRight).contains("0,0 80,0 80,50 0,20");
        assertThat(splitLeft).contains("0,0 80,0 80,20 0,50");
    }

    @Test
    void generate_monogramEmblem_multiWordName_usesFirstLetterOfEachWord() {
        UUID id = new UUID(7L, 0L);

        String svg = LogoGenerator.generate("Sofia FC", id);

        assertThat(svg).contains(">SF</text>");
    }

    @Test
    void generate_monogramEmblem_singleWordName_usesFirstTwoLetters() {
        UUID id = new UUID(7L, 0L);

        String svg = LogoGenerator.generate("Titan", id);

        assertThat(svg).contains(">TI</text>");
    }

    @Test
    void generate_monogramEmblem_singleCharWordName_doesNotAppendSecondLetter() {
        UUID id = new UUID(7L, 0L);

        String svg = LogoGenerator.generate("X", id);

        assertThat(svg).contains(">X</text>");
    }

    @Test
    void generate_monogramEmblem_escapesXmlSpecialCharacters() {
        UUID id = new UUID(7L, 0L);

        String svg = LogoGenerator.generate("A & B", id);

        assertThat(svg).contains(">A&amp;</text>");
    }

    @Test
    void generateLeagueLogo_monogramBranch_containsInitials() {
        String svg = LogoGenerator.generateLeagueLogo("Premier Cup", new UUID(0L, 0L));

        assertThat(svg).startsWith("<svg");
        assertThat(svg).contains(">PC</text>");
    }

    @Test
    void generateLeagueLogo_starBranch_containsPolygon() {
        String svg = LogoGenerator.generateLeagueLogo("Premier Cup", new UUID(32L, 0L));

        assertThat(svg).contains("<polygon");
        assertThat(svg).doesNotContain("<text");
    }

    @Test
    void generateLeagueLogo_coversRimColorBranches() {
        String goldRim = LogoGenerator.generateLeagueLogo("Cup", new UUID(0L, 0L));
        String silverRim = LogoGenerator.generateLeagueLogo("Cup", new UUID(8L, 0L));

        assertThat(goldRim).contains("#f4d27a");
        assertThat(silverRim).contains("#e8e8ea");
    }

    @Test
    void generateLeagueLogo_coversAllShapeBranches() {
        for (long hi = 0; hi <= 4; hi++) {
            UUID id = new UUID(hi, 0L);
            String svg = LogoGenerator.generateLeagueLogo("Rooftop Cup", id);

            assertThat(svg).startsWith("<svg");
        }
    }

    @Test
    void generateLeagueLogo_nonCircleShape_usesPathElements() {
        UUID id = new UUID(0L, 0L);

        String svg = LogoGenerator.generateLeagueLogo("Rooftop Cup", id);

        assertThat(svg).contains("<path d=");
    }

    @Test
    void generateLeagueLogo_circleShape_usesCircleElements() {
        UUID id = new UUID(4L, 0L);

        String svg = LogoGenerator.generateLeagueLogo("Rooftop Cup", id);

        assertThat(svg).contains("<circle cx=\"40\" cy=\"46\" r=\"37\"");
        assertThat(svg).contains("<circle cx=\"40\" cy=\"46\" r=\"31\"");
    }

    @Test
    void generateLeagueLogo_appliesDropShadowFilterAndEmblemGradient() {
        UUID id = new UUID(0L, 0L);

        String svg = LogoGenerator.generateLeagueLogo("Rooftop Cup", id);

        assertThat(svg).contains("<feDropShadow");
        assertThat(svg).contains("<g filter=\"url(#ls");
        assertThat(svg).contains("fill=\"url(#le");
    }
}
