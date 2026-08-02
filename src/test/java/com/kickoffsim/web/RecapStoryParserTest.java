package com.kickoffsim.web;

import com.kickoffsim.dto.RecapStory;
import com.kickoffsim.dto.RecapStoryKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecapStoryParserTest {

    @Test
    void parse_validLine_readsEveryField() {
        List<RecapStory> stories = RecapStoryParser.parse("COMEBACK|75|Alpha turn it around|They trailed by two.");

        assertThat(stories).hasSize(1);
        RecapStory story = stories.get(0);
        assertThat(story.kind()).isEqualTo(RecapStoryKind.COMEBACK);
        assertThat(story.weight()).isEqualTo(75);
        assertThat(story.headline()).isEqualTo("Alpha turn it around");
        assertThat(story.body()).isEqualTo("They trailed by two.");
    }

    @Test
    void parse_bodyWithSeparators_keepsThemInTheBody() {
        List<RecapStory> stories = RecapStoryParser.parse("STATS|1||3 matches|22 goals");

        assertThat(stories.get(0).body()).isEqualTo("3 matches|22 goals");
    }

    @Test
    void parse_multipleLines_keepsTheOrder() {
        List<RecapStory> stories = RecapStoryParser.parse("MVP|40|a|b\nRESULTS|5|c|d");

        assertThat(stories).extracting(RecapStory::kind)
                .containsExactly(RecapStoryKind.MVP, RecapStoryKind.RESULTS);
    }

    @Test
    void parse_blankLinesBetweenStories_areSkipped() {
        List<RecapStory> stories = RecapStoryParser.parse("MVP|40|a|b\n\nRESULTS|5|c|d");

        assertThat(stories).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "✨ Highlights:\n- Draws: 2",
            "MVP|40|missing body",
            "NOT_A_KIND|40|a|b",
            "MVP|heavy|a|b"})
    void parse_contentThatIsNotInTheCurrentFormat_yieldsNothing(String content) {
        assertThat(RecapStoryParser.parse(content)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void parse_blankContent_yieldsNothing(String content) {
        assertThat(RecapStoryParser.parse(content)).isEmpty();
    }

    @Test
    void parse_nullContent_yieldsNothing() {
        assertThat(RecapStoryParser.parse(null)).isEmpty();
    }

    @Test
    void serialize_thenParse_roundTripsEveryStory() {
        List<RecapStory> stories = List.of(
                new RecapStory(RecapStoryKind.TITLE_RACE, 68, "Two points apart", "Alpha lead Beta."),
                new RecapStory(RecapStoryKind.SQUAD, 10, "Team of the round", "Petar (Alpha);;Ivan (Beta)"));

        assertThat(RecapStoryParser.parse(RecapStoryParser.serialize(stories))).isEqualTo(stories);
    }

    @Test
    void serialize_noStories_yieldsEmptyContent() {
        assertThat(RecapStoryParser.serialize(List.of())).isEmpty();
    }

    @Test
    void items_splitsTheBodyAndDropsTheBlanks() {
        RecapStory story = new RecapStory(RecapStoryKind.SQUAD, 10, "head", "one;; two ;;;;three");

        assertThat(story.items()).containsExactly("one", "two", "three");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void items_blankBody_yieldsNothing(String body) {
        assertThat(new RecapStory(RecapStoryKind.STATS, 1, "head", body).items()).isEmpty();
    }

    @Test
    void items_nullBody_yieldsNothing() {
        assertThat(new RecapStory(RecapStoryKind.STATS, 1, "head", null).items()).isEmpty();
    }

    @Test
    void slug_replacesUnderscoresAndLowercasesTheName() {
        assertThat(RecapStoryKind.TITLE_DECIDED.getSlug()).isEqualTo("title-decided");
        assertThat(RecapStoryKind.MVP.getIcon()).isNotBlank();
    }
}
