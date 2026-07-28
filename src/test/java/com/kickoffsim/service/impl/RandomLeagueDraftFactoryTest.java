package com.kickoffsim.service.impl;

import com.kickoffsim.dto.LeagueSkeletonDraft;
import com.kickoffsim.dto.PlayerNameDraft;
import com.kickoffsim.dto.SquadDraft;
import com.kickoffsim.dto.TeamNameDraft;
import com.kickoffsim.web.LeagueDraftSanitizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class RandomLeagueDraftFactoryTest {

    private static final List<String> TEAM_POOL = List.of(LeagueDraftSanitizer.TEAM_NAMES);
    private static final List<String> CITY_POOL = List.of(LeagueDraftSanitizer.CITIES);
    private static final List<String> FIRST_POOL = List.of(LeagueDraftSanitizer.FIRST_NAMES);
    private static final List<String> LAST_POOL = List.of(LeagueDraftSanitizer.LAST_NAMES);
    private static final List<String> FOOD_POOL = List.of(LeagueDraftSanitizer.LEAGUE_FOODS);
    private static final List<String> TYPE_POOL = List.of(LeagueDraftSanitizer.LEAGUE_TYPES);

    private final RandomLeagueDraftFactory factory = new RandomLeagueDraftFactory(new Random(42));

    @Test
    void createSkeleton_leagueNameComesFromPools() {
        String[] parts = factory.createSkeleton(8).leagueName().split(" ");

        assertThat(parts).hasSize(2);
        assertThat(FOOD_POOL).contains(parts[0]);
        assertThat(TYPE_POOL).contains(parts[1]);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 6, 8, 10, 16})
    void createSkeleton_returnsRequestedCountWithDistinctPoolNames(int teamCount) {
        LeagueSkeletonDraft skeleton = factory.createSkeleton(teamCount);

        assertThat(skeleton.teams()).hasSize(teamCount);
        assertThat(skeleton.teams()).extracting(TeamNameDraft::name).doesNotHaveDuplicates();
        for (TeamNameDraft team : skeleton.teams()) {
            assertThat(TEAM_POOL).contains(team.name());
            assertThat(CITY_POOL).contains(team.city());
            assertThat(team.squadSize()).isBetween(6, 12);
        }
    }

    @Test
    void createSkeleton_zeroOrNegativeCount_returnsNoTeams() {
        assertThat(factory.createSkeleton(0).teams()).isEmpty();
        assertThat(factory.createSkeleton(-4).teams()).isEmpty();
    }

    @Test
    void createSkeleton_countBeyondPool_isCappedAtPoolSize() {
        LeagueSkeletonDraft skeleton = factory.createSkeleton(LeagueDraftSanitizer.TEAM_NAMES.length + 10);

        assertThat(skeleton.teams()).hasSize(LeagueDraftSanitizer.TEAM_NAMES.length);
        assertThat(skeleton.teams()).extracting(TeamNameDraft::name).doesNotHaveDuplicates();
    }

    @ParameterizedTest
    @ValueSource(ints = {6, 9, 12})
    void createSquad_returnsRequestedSizeWithDistinctPoolNames(int size) {
        SquadDraft squad = factory.createSquad(size);

        assertThat(squad.players()).hasSize(size);
        assertThat(squad.players()).extracting(p -> p.firstName() + " " + p.lastName()).doesNotHaveDuplicates();
        for (PlayerNameDraft player : squad.players()) {
            assertThat(FIRST_POOL).contains(player.firstName());
            assertThat(LAST_POOL).contains(player.lastName());
        }
    }

    @Test
    void createSquad_sizeIsClamped() {
        assertThat(factory.createSquad(2).players()).hasSize(6);
        assertThat(factory.createSquad(40).players()).hasSize(12);
    }

    @Test
    void sameSeed_producesSameDraft() {
        RandomLeagueDraftFactory first = new RandomLeagueDraftFactory(new Random(7));
        RandomLeagueDraftFactory second = new RandomLeagueDraftFactory(new Random(7));

        assertThat(first.createSkeleton(8)).isEqualTo(second.createSkeleton(8));
        assertThat(first.createSquad(9)).isEqualTo(second.createSquad(9));
    }

    @Test
    void differentDraws_varyBetweenCalls() {
        LeagueSkeletonDraft first = factory.createSkeleton(8);
        LeagueSkeletonDraft second = factory.createSkeleton(8);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void createSquad_redrawsWhenTheSameFullNameComesUpTwice() {
        int[] scripted = {0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6};
        Random repeating = new Random() {
            private int index;

            @Override
            public int nextInt(int bound) {
                return scripted[index++ % scripted.length] % bound;
            }
        };

        SquadDraft squad = new RandomLeagueDraftFactory(repeating).createSquad(6);

        assertThat(squad.players()).hasSize(6);
        assertThat(squad.players()).extracting(p -> p.firstName() + " " + p.lastName())
                .doesNotHaveDuplicates();
    }

    @Test
    void defaultConstructor_producesUsableDraft() {
        LeagueSkeletonDraft skeleton = new RandomLeagueDraftFactory().createSkeleton(6);

        assertThat(skeleton.teams()).hasSize(6);
        assertThat(skeleton.teams()).extracting(TeamNameDraft::name).doesNotHaveDuplicates();
    }
}
