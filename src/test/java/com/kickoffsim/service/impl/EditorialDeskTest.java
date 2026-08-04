package com.kickoffsim.service.impl;

import com.kickoffsim.dto.RecapMemory;
import com.kickoffsim.dto.RecapStory;
import com.kickoffsim.dto.RecapStoryKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EditorialDeskTest {

    private final EditorialDesk desk = new EditorialDesk();

    @Test
    void arrange_ordersNarrativeByWeightThenStatsThenLists() {
        List<RecapStory> arranged = desk.arrange(List.of(
                story(RecapStoryKind.SQUAD, 10, "Squad"),
                story(RecapStoryKind.RESULTS, 5, "Results"),
                story(RecapStoryKind.STATS, 1, ""),
                story(RecapStoryKind.MVP, 40, "Mvp"),
                story(RecapStoryKind.UPSET, 70, "Upset")), null);

        assertThat(arranged).extracting(RecapStory::kind).containsExactly(
                RecapStoryKind.UPSET, RecapStoryKind.MVP,
                RecapStoryKind.STATS, RecapStoryKind.SQUAD, RecapStoryKind.RESULTS);
    }

    @Test
    void arrange_equalWeight_isBrokenByKindName() {
        List<RecapStory> arranged = desk.arrange(List.of(
                story(RecapStoryKind.SURGE, 50, "Surge"),
                story(RecapStoryKind.COMEBACK, 50, "Comeback")), RecapMemory.empty());

        assertThat(arranged).extracting(RecapStory::kind)
                .containsExactly(RecapStoryKind.COMEBACK, RecapStoryKind.SURGE);
    }

    @Test
    void arrange_penalisesRecentlyUsedAngle() {
        RecapMemory memory = new RecapMemory(List.of(RecapStoryKind.UPSET.name()), List.of(), false);

        List<RecapStory> arranged = desk.arrange(List.of(
                story(RecapStoryKind.UPSET, 52, "Upset"),
                story(RecapStoryKind.SURGE, 50, "Surge")), memory);

        assertThat(arranged.get(0).kind()).isEqualTo(RecapStoryKind.SURGE);
    }

    @Test
    void arrange_pushesDownARepeatedHeadline() {
        RecapMemory memory = new RecapMemory(List.of(), List.of("Same headline"), false);

        List<RecapStory> arranged = desk.arrange(List.of(
                story(RecapStoryKind.UPSET, 60, "Same headline"),
                story(RecapStoryKind.SURGE, 40, "Fresh headline")), memory);

        assertThat(arranged.get(0).kind()).isEqualTo(RecapStoryKind.SURGE);
    }

    @Test
    void arrange_keepsAtMostSixNarrativeStories() {
        List<RecapStory> narrative = desk.arrange(List.of(
                story(RecapStoryKind.UPSET, 70, "a"),
                story(RecapStoryKind.SURGE, 65, "b"),
                story(RecapStoryKind.COLLAPSE, 60, "c"),
                story(RecapStoryKind.COMEBACK, 55, "d"),
                story(RecapStoryKind.LATE_DRAMA, 50, "e"),
                story(RecapStoryKind.MVP, 45, "f"),
                story(RecapStoryKind.STREAK, 40, "g")), RecapMemory.empty());

        assertThat(narrative).hasSize(6)
                .noneMatch(story -> story.kind() == RecapStoryKind.STREAK);
    }

    private RecapStory story(RecapStoryKind kind, int weight, String headline) {
        return new RecapStory(kind, weight, headline, "body");
    }
}
