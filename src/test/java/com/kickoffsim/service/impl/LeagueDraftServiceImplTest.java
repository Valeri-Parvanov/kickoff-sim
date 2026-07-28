package com.kickoffsim.service.impl;

import com.kickoffsim.dto.LeagueSkeletonDraft;
import com.kickoffsim.dto.LeagueWizardForm;
import com.kickoffsim.dto.PlayerNameDraft;
import com.kickoffsim.dto.PlayerRowDto;
import com.kickoffsim.dto.SquadDraft;
import com.kickoffsim.dto.TeamCreateForm;
import com.kickoffsim.dto.TeamDto;
import com.kickoffsim.dto.TeamNameDraft;
import com.kickoffsim.service.LeagueDraftFactory;
import com.kickoffsim.service.TeamService;
import com.kickoffsim.web.LeagueDraftSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeagueDraftServiceImplTest {

    private static final List<String> TEAM_POOL = List.of(LeagueDraftSanitizer.TEAM_NAMES);

    @Mock
    private LeagueDraftFactory draftFactory;

    @Mock
    private TeamService teamService;

    private LeagueDraftServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LeagueDraftServiceImpl(draftFactory, teamService);
        when(draftFactory.createSkeleton(anyInt())).thenAnswer(i -> skeleton(i.getArgument(0)));
        when(draftFactory.createSquad(anyInt())).thenAnswer(i -> squad(i.getArgument(0)));
        when(teamService.existsByNameAndCity(anyString(), anyString())).thenReturn(false);
    }

    @Test
    void draft_noExistingTeams_buildsEveryTeamAndSquad() {
        LeagueWizardForm form = service.draft(8, List.of());

        assertThat(form.getFormat()).isEqualTo(8);
        assertThat(form.getLeagueName()).isEqualTo("Banitsa Cup");
        assertThat(form.getNewTeams()).hasSize(8);
        assertThat(form.getExistingTeamIds()).isEmpty();
        assertThat(form.getNewTeams()).allSatisfy(t -> assertThat(t.getPlayers()).hasSize(12));
        verify(draftFactory).createSkeleton(8);
        verify(draftFactory, times(8)).createSquad(anyInt());
    }

    @Test
    void draft_nullExistingTeams_isTolerated() {
        LeagueWizardForm form = service.draft(8, null);

        assertThat(form.getNewTeams()).hasSize(8);
        assertThat(form.getExistingTeamIds()).isEmpty();
    }

    @Test
    void draft_withExistingTeams_buildsOnlyTheShortfall() {
        List<TeamDto> existing = List.of(team("Alpha", "Sofia"), team("Beta", "Varna"), team("Gama", "Ruse"));

        LeagueWizardForm form = service.draft(8, existing);

        assertThat(form.getNewTeams()).hasSize(5);
        assertThat(form.getExistingTeamIds()).hasSize(3);
        verify(draftFactory).createSkeleton(5);
        verify(draftFactory, times(5)).createSquad(anyInt());
    }

    @Test
    void draft_squadFull_buildsNothing() {
        List<TeamDto> existing = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            existing.add(team("Team" + i, "Sofia"));
        }

        LeagueWizardForm form = service.draft(8, existing);

        assertThat(form.getNewTeams()).isEmpty();
        verify(draftFactory, never()).createSquad(anyInt());
    }

    @Test
    void draft_moreExistingTeamsThanFormat_requestsZeroTeams() {
        List<TeamDto> existing = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            existing.add(team("Team" + i, "Sofia"));
        }

        service.draft(8, existing);

        verify(draftFactory).createSkeleton(0);
    }

    @Test
    void draft_existingTeamNames_areNotReused() {
        when(draftFactory.createSkeleton(anyInt())).thenReturn(new LeagueSkeletonDraft("Banitsa Cup",
                List.of(new TeamNameDraft("Domati", "Plovdiv", 12))));
        List<TeamDto> existing = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            existing.add(team(i == 0 ? "Domati" : "Other" + i, "Sofia"));
        }

        LeagueWizardForm form = service.draft(8, existing);

        assertThat(form.getNewTeams().get(0).getName()).isNotEqualToIgnoringCase("Domati");
        assertThat(TEAM_POOL).contains(form.getNewTeams().get(0).getName());
    }

    @Test
    void draft_nameAlreadyTakenInDatabase_picksAnotherPoolName() {
        when(draftFactory.createSkeleton(anyInt())).thenReturn(new LeagueSkeletonDraft("Banitsa Cup",
                List.of(new TeamNameDraft("Domati", "Sofia", 12))));
        when(teamService.existsByNameAndCity("Domati", "Sofia")).thenReturn(true);

        LeagueWizardForm form = service.draft(1, List.of());

        assertThat(form.getNewTeams().get(0).getName()).isNotEqualTo("Domati");
        assertThat(TEAM_POOL).contains(form.getNewTeams().get(0).getName());
    }

    @Test
    void draft_perTeamSquadSizes_areHonoured() {
        when(draftFactory.createSkeleton(anyInt())).thenReturn(new LeagueSkeletonDraft("Banitsa Cup", List.of(
                new TeamNameDraft("Domati", "Sofia", 6),
                new TeamNameDraft("Kyufteta", "Varna", 9))));

        LeagueWizardForm form = service.draft(2, List.of());

        assertThat(filled(form.getNewTeams().get(0))).hasSize(6);
        assertThat(filled(form.getNewTeams().get(1))).hasSize(9);
        assertThat(form.getNewTeams()).allSatisfy(t -> assertThat(t.getPlayers()).hasSize(12));

        ArgumentCaptor<Integer> sizes = ArgumentCaptor.forClass(Integer.class);
        verify(draftFactory, times(2)).createSquad(sizes.capture());
        assertThat(sizes.getAllValues()).containsExactly(6, 9);
    }

    @Test
    void draft_nullSkeleton_stillProducesValidForm() {
        when(draftFactory.createSkeleton(anyInt())).thenReturn(null);

        LeagueWizardForm form = service.draft(2, List.of());

        assertThat(form.getLeagueName().split(" ")).hasSize(2);
        assertThat(List.of(LeagueDraftSanitizer.LEAGUE_FOODS))
                .contains(form.getLeagueName().split(" ")[0]);
        assertThat(form.getNewTeams()).hasSize(2);
        assertThat(form.getNewTeams()).extracting(TeamCreateForm::getName)
                .allSatisfy(n -> assertThat(TEAM_POOL).contains(n));
    }

    @Test
    void draft_scheduleFieldsAreLeftForTheUser() {
        LeagueWizardForm form = service.draft(8, List.of());

        assertThat(form.getScheduleStartDate()).isNull();
        assertThat(form.getScheduleStartTime()).isNull();
    }

    private List<PlayerRowDto> filled(TeamCreateForm team) {
        return team.getPlayers().stream().filter(row -> !row.isEmpty()).toList();
    }

    private LeagueSkeletonDraft skeleton(int teamCount) {
        List<TeamNameDraft> teams = new ArrayList<>();
        for (int i = 0; i < teamCount; i++) {
            teams.add(new TeamNameDraft(LeagueDraftSanitizer.TEAM_NAMES[i % LeagueDraftSanitizer.TEAM_NAMES.length],
                    LeagueDraftSanitizer.CITIES[i % LeagueDraftSanitizer.CITIES.length], 12));
        }
        return new LeagueSkeletonDraft("Banitsa Cup", teams);
    }

    private SquadDraft squad(int size) {
        List<PlayerNameDraft> players = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            players.add(new PlayerNameDraft(
                    LeagueDraftSanitizer.FIRST_NAMES[i % LeagueDraftSanitizer.FIRST_NAMES.length],
                    LeagueDraftSanitizer.LAST_NAMES[i % LeagueDraftSanitizer.LAST_NAMES.length]));
        }
        return new SquadDraft(players);
    }

    private TeamDto team(String name, String city) {
        TeamDto dto = new TeamDto();
        dto.setId(UUID.randomUUID());
        dto.setName(name);
        dto.setCity(city);
        dto.setPlayerCount(8);
        return dto;
    }
}
