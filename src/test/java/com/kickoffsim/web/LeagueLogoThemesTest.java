package com.kickoffsim.web;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LeagueLogoThemesTest {

    private static List<String> vocabularyFoods() throws Exception {
        String js = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/resources/static/js/random-names.js"),
                java.nio.charset.StandardCharsets.UTF_8);
        String block = js.substring(js.indexOf("const LEAGUE_FOODS = ["));
        block = block.substring(0, block.indexOf("\n];"));

        return java.util.regex.Pattern.compile("food:\\s*\"([^\"]+)\"")
                .matcher(block)
                .results()
                .map(r -> r.group(1))
                .toList();
    }

    private static List<String> vocabularyNames() throws Exception {
        String js = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/resources/static/js/random-names.js"),
                java.nio.charset.StandardCharsets.UTF_8);
        String block = js.substring(js.indexOf("const LEAGUE_FOODS = ["));
        block = block.substring(0, block.indexOf("\n];"));

        return java.util.regex.Pattern.compile("food:\\s*\"([^\"]+)\",\\s*types:\\s*\\[([^]]+)]")
                .matcher(block)
                .results()
                .flatMap(r -> java.util.regex.Pattern.compile("\"([^\"]+)\"")
                        .matcher(r.group(2))
                        .results()
                        .map(t -> r.group(1) + " " + t.group(1)))
                .toList();
    }

    @Test
    void everyFoodInTheVocabulary_resolvesToATheme() throws Exception {
        List<String> foods = vocabularyFoods();

        assertThat(foods).isNotEmpty();
        assertThat(foods).allSatisfy(food -> assertThat(LeagueLogoThemes.match(food))
                .as("random-names.js offers '%s' but no theme matches it", food)
                .isPresent());
    }

    @Test
    void everyGeneratableName_resolvesToTheThemeOfItsFood() throws Exception {
        for (String name : vocabularyNames()) {
            String food = name.substring(0, name.lastIndexOf(' '));

            assertThat(LeagueLogoThemes.match(name))
                    .as("'%s' does not resolve to the same theme as '%s'", name, food)
                    .isEqualTo(LeagueLogoThemes.match(food));
        }
    }

    @Test
    void everyGeneratableName_fitsTheUiLengthBudget() throws Exception {
        assertThat(vocabularyNames()).allSatisfy(name -> assertThat(name.length())
                .as("'%s' exceeds the 22 character budget", name)
                .isLessThanOrEqualTo(22));
    }

    @Test
    void match_isCaseInsensitive() {
        assertThat(LeagueLogoThemes.match("DOMAT DERBY")).isPresent();
        assertThat(LeagueLogoThemes.match("domat derby")).isPresent();
    }

    @Test
    void match_customNameTypedByHand_returnsEmpty() {
        assertThat(LeagueLogoThemes.match("Premier Invitational")).isEmpty();
        assertThat(LeagueLogoThemes.match("My Office League")).isEmpty();
    }

    @Test
    void match_nullOrBlank_returnsEmpty() {
        assertThat(LeagueLogoThemes.match(null)).isEmpty();
        assertThat(LeagueLogoThemes.match("")).isEmpty();
        assertThat(LeagueLogoThemes.match("   ")).isEmpty();
    }

    @Test
    void topper_coversEveryVariantAndSubstitutesTheGradientId() {
        for (int i = 0; i < LeagueLogoThemes.topperCount(); i++) {
            String svg = LeagueLogoThemes.topper(i, "gid");

            assertThat(svg).contains("url(#gid)");
            assertThat(svg).doesNotContain("%1$s");
        }
    }

    @Test
    void topper_indexWraps_andHandlesNegativeValues() {
        assertThat(LeagueLogoThemes.topper(LeagueLogoThemes.topperCount(), "g"))
                .isEqualTo(LeagueLogoThemes.topper(0, "g"));
        assertThat(LeagueLogoThemes.topper(-1, "g"))
                .isEqualTo(LeagueLogoThemes.topper(LeagueLogoThemes.topperCount() - 1, "g"));
    }

    @Test
    void hasOverflow_isFalseWhenOverflowIsNull() {
        LeagueLogoThemes.Theme theme =
                new LeagueLogoThemes.Theme("#111111", "#222222", "<path d=\"M0 0\"/>", null, "nulloverflow");

        assertThat(theme.hasOverflow()).isFalse();
    }

    @Test
    void hasOverflow_isFalseWhenOverflowIsEmpty() {
        LeagueLogoThemes.Theme theme =
                new LeagueLogoThemes.Theme("#111111", "#222222", "<path d=\"M0 0\"/>", "", "emptyoverflow");

        assertThat(theme.hasOverflow()).isFalse();
    }

    @Test
    void hasOverflow_isTrueWhenOverflowIsPresent() {
        LeagueLogoThemes.Theme theme =
                new LeagueLogoThemes.Theme("#111111", "#222222", "<path d=\"M0 0\"/>", "<path d=\"M1 1\"/>", "hasoverflow");

        assertThat(theme.hasOverflow()).isTrue();
    }

    @Test
    void hasOverflow_isTrueOnlyForTheSpillableThemes() {
        assertThat(LeagueLogoThemes.match("Party Boza League").orElseThrow().hasOverflow()).isTrue();
        assertThat(LeagueLogoThemes.match("Ayrian Arena").orElseThrow().hasOverflow()).isTrue();
        assertThat(LeagueLogoThemes.match("Domat Derby").orElseThrow().hasOverflow()).isTrue();
        assertThat(LeagueLogoThemes.match("Lyutenitsa League").orElseThrow().hasOverflow()).isTrue();

        assertThat(LeagueLogoThemes.match("Sirene Series").orElseThrow().hasOverflow()).isFalse();
        assertThat(LeagueLogoThemes.match("Barcode United Cup").orElseThrow().hasOverflow()).isFalse();
    }

    @Test
    void themedLeague_rendersMotifAndNoMonogram() {
        String svg = LogoGenerator.generateLeagueLogo("Domat Derby", new UUID(0L, 0L));

        assertThat(svg).startsWith("<svg");
        assertThat(svg).doesNotContain("<text");
        assertThat(svg).contains("#3f7d34");
    }

    @Test
    void untitledLeague_keepsTheGenericMonogramDesign() {
        String svg = LogoGenerator.generateLeagueLogo("Premier Cup", new UUID(0L, 0L));

        assertThat(svg).contains(">PC</text>");
    }

    @Test
    void spillableTheme_producesBothTopperAndOverflowVariants() {
        boolean sawTopper = false;
        boolean sawOverflow = false;

        for (long hi = 0; hi < 4096 && !(sawTopper && sawOverflow); hi++) {
            String svg = LogoGenerator.generateLeagueLogo("Party Boza League", new UUID(hi, 0L));
            if (svg.contains("url(#lg")) {
                sawTopper = true;
            } else {
                sawOverflow = true;
            }
        }

        assertThat(sawTopper).as("no topper variant generated").isTrue();
        assertThat(sawOverflow).as("no overflow variant generated").isTrue();
    }

    @Test
    void themedLeague_variesTopperAcrossIds() {
        long distinct = java.util.stream.LongStream.range(0, 4096)
                .mapToObj(hi -> LogoGenerator.generateLeagueLogo("Sirene Series", new UUID(hi, 0L)))
                .distinct()
                .count();

        assertThat(distinct).isGreaterThan(4);
    }
}
