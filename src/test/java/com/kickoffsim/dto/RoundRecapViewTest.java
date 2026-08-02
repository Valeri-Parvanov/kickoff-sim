package com.kickoffsim.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoundRecapViewTest {

    @Test
    void groupsTheStoriesIntoLeadSecondaryStatsAndPanels() {
        RecapStory lead = story(RecapStoryKind.COMEBACK, 70);
        RecapStory second = story(RecapStoryKind.BIG_WIN, 40);
        RecapStory third = story(RecapStoryKind.AWAY_WIN, 28);
        RecapStory stats = story(RecapStoryKind.STATS, 1);
        RecapStory squad = story(RecapStoryKind.SQUAD, 10);

        RoundRecapView view = view(List.of(lead, second, third, stats, squad));

        assertThat(view.getLead()).isEqualTo(lead);
        assertThat(view.getSecondary()).containsExactly(second, third);
        assertThat(view.getStats()).isEqualTo(stats);
        assertThat(view.getLists()).containsExactly(squad);
    }

    @Test
    void panelsOnly_haveNoLeadNoSecondaryAndNoStats() {
        RecapStory results = story(RecapStoryKind.RESULTS, 5);

        RoundRecapView view = view(List.of(results));

        assertThat(view.getLead()).isNull();
        assertThat(view.getSecondary()).isEmpty();
        assertThat(view.getStats()).isNull();
        assertThat(view.getLists()).containsExactly(results);
    }

    @Test
    void noStoriesAtAll_yieldNothing() {
        RoundRecapView view = view(List.of());

        assertThat(view.getLead()).isNull();
        assertThat(view.getSecondary()).isEmpty();
        assertThat(view.getStats()).isNull();
        assertThat(view.getLists()).isEmpty();
    }

    @Test
    void nullStories_yieldNothing() {
        RoundRecapView view = view(null);

        assertThat(view.getLead()).isNull();
        assertThat(view.getSecondary()).isEmpty();
        assertThat(view.getStats()).isNull();
        assertThat(view.getLists()).isEmpty();
    }

    private RecapStory story(RecapStoryKind kind, int weight) {
        return new RecapStory(kind, weight, kind.name(), "body");
    }

    private RoundRecapView view(List<RecapStory> stories) {
        return new RoundRecapView("content", LocalDateTime.now(), "en", "a".repeat(64), stories);
    }
}
