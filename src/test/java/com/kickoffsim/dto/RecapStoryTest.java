package com.kickoffsim.dto;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class RecapStoryTest {

    @Test
    void players_readNameTeamGoalsAndAssists() {
        RecapStory story = squad("Petar::Alpha::2::1;;Georgi::Beta::0::3");

        assertThat(story.players())
                .extracting(RecapPlayer::name, RecapPlayer::team, RecapPlayer::goals, RecapPlayer::assists)
                .containsExactly(
                        tuple("Petar", "Alpha", 2, 1),
                        tuple("Georgi", "Beta", 0, 3));
    }

    @Test
    void items_dropTheBlankOnes() {
        RecapStory story = squad("Petar::Alpha::2::1;; ;;Georgi::Beta::0::3");

        assertThat(story.players()).extracting(RecapPlayer::name)
                .containsExactly("Petar", "Georgi");
    }

    @Test
    void players_itemWithoutFields_keepsTheWholeTextAsTheName() {
        RecapStory story = squad("Petar (Alpha) — 1G 0A");

        assertThat(story.players()).containsExactly(
                new RecapPlayer("Petar (Alpha) — 1G 0A", "", 0, 0));
    }

    @Test
    void players_unreadableCounts_fallBackToZero() {
        RecapStory story = squad("Petar::Alpha::two::-");

        assertThat(story.players()).containsExactly(new RecapPlayer("Petar", "Alpha", 0, 0));
    }

    @Test
    void tiles_readValueAndLabel() {
        RecapStory story = new RecapStory(RecapStoryKind.STATS, 1, "", "5::matches;;36::goals");

        assertThat(story.tiles()).extracting(RecapStat::value, RecapStat::label)
                .containsExactly(tuple("5", "matches"), tuple("36", "goals"));
    }

    @Test
    void tiles_itemWithoutFields_becomesALabelOnly() {
        RecapStory story = new RecapStory(RecapStoryKind.STATS, 1, "", "5 matches");

        assertThat(story.tiles()).containsExactly(new RecapStat("", "5 matches"));
    }

    @Test
    void results_readTheTextAndTheMatchId() {
        RecapStory story = new RecapStory(RecapStoryKind.RESULTS, 5, "Results",
                "Alpha 2:1 Beta::match-1;;Gama 0:0 Delta::match-2");

        assertThat(story.results()).extracting(RecapLink::text, RecapLink::id)
                .containsExactly(
                        tuple("Alpha 2:1 Beta", "match-1"),
                        tuple("Gama 0:0 Delta", "match-2"));
    }

    @Test
    void results_itemWithoutAnId_staysPlainText() {
        RecapStory story = new RecapStory(RecapStoryKind.RESULTS, 5, "Results", "Alpha 2:1 Beta");

        assertThat(story.results()).containsExactly(new RecapLink("Alpha 2:1 Beta", null));
    }

    @Test
    void lineup_sixPlayers_areSpreadOverFourLines() {
        assertThat(sizes(squad(players(6)).lineup())).containsExactly(1, 2, 2, 1);
    }

    @Test
    void lineup_threePlayers_fillOnlyTheLinesThatAreNeeded() {
        assertThat(sizes(squad(players(3)).lineup())).containsExactly(1, 2);
    }

    @Test
    void lineup_incompleteLine_keepsWhatIsLeft() {
        assertThat(sizes(squad(players(4)).lineup())).containsExactly(1, 2, 1);
    }

    @Test
    void lineup_moreThanTheFormationHolds_getsAnExtraLine() {
        assertThat(sizes(squad(players(8)).lineup())).containsExactly(1, 2, 2, 1, 2);
    }

    @Test
    void lineup_noPlayers_isEmpty() {
        assertThat(squad("").lineup()).isEmpty();
    }

    @Test
    void lineup_startsWithTheTopContributor() {
        assertThat(squad(players(6)).lineup().get(0).get(0).name()).isEqualTo("Player1");
    }

    private RecapStory squad(String body) {
        return new RecapStory(RecapStoryKind.SQUAD, 10, "Team of the round", body);
    }

    private String players(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(index -> "Player" + index + "::Alpha::1::0")
                .collect(Collectors.joining(RecapStory.ITEM_SEPARATOR));
    }

    private List<Integer> sizes(List<List<RecapPlayer>> lineup) {
        return lineup.stream().map(List::size).toList();
    }
}
