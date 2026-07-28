package com.kickoffsim.controller;

import com.kickoffsim.dto.LeagueWizardForm;
import com.kickoffsim.dto.TeamDto;
import java.util.NoSuchElementException;
import com.kickoffsim.service.LeagueDraftService;
import com.kickoffsim.service.TeamService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeagueDraftControllerTest {

    @Mock
    private TeamService teamService;

    @Mock
    private LeagueDraftService leagueDraftService;

    @InjectMocks
    private LeagueDraftController controller;

    @Test
    void generate_invalidFormat_redirectsToWizardStart() {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String view = controller.generate(7, null, new ExtendedModelMap(), redirectAttributes);

        assertThat(view).isEqualTo("redirect:/leagues/wizard");
        assertThat(redirectAttributes.getFlashAttributes().get("warnMessage"))
                .isEqualTo("flash.league.invalidformat");
        verify(leagueDraftService, never()).draft(anyInt(), any());
    }

    @Test
    void generate_tooManyExistingTeams_redirectsToTeamsStep() {
        List<UUID> selected = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            selected.add(UUID.randomUUID());
        }
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String view = controller.generate(6, selected, new ExtendedModelMap(), redirectAttributes);

        assertThat(view).isEqualTo("redirect:/leagues/wizard/teams?format=6");
        assertThat(redirectAttributes.getFlashAttributes().get("warnMessage"))
                .isEqualTo("flash.league.toomanyteams");
    }

    @Test
    void generate_teamAlreadyInLeague_redirectsToTeamsStep() {
        UUID id = UUID.randomUUID();
        when(teamService.findById(id)).thenReturn(team(id, UUID.randomUUID(), 8));
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String view = controller.generate(8, List.of(id), new ExtendedModelMap(), redirectAttributes);

        assertThat(view).isEqualTo("redirect:/leagues/wizard/teams?format=8");
        assertThat(redirectAttributes.getFlashAttributes().get("warnMessage"))
                .isEqualTo("flash.league.teamsineligible");
    }

    @Test
    void generate_teamWithTooFewPlayers_redirectsToTeamsStep() {
        UUID id = UUID.randomUUID();
        when(teamService.findById(id)).thenReturn(team(id, null, 3));
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String view = controller.generate(8, List.of(id), new ExtendedModelMap(), redirectAttributes);

        assertThat(view).isEqualTo("redirect:/leagues/wizard/teams?format=8");
    }

    @Test
    void generate_nullExistingTeamIds_generatesForTheWholeFormat() {
        LeagueWizardForm drafted = new LeagueWizardForm();
        when(leagueDraftService.draft(anyInt(), any())).thenReturn(drafted);
        Model model = new ExtendedModelMap();

        String view = controller.generate(8, null, model, new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("leagues/wizard-new-teams");
        verify(leagueDraftService).draft(8, List.of());
    }

    @Test
    void generate_success_returnsWizardViewWithPrefilledForm() {
        LeagueWizardForm drafted = new LeagueWizardForm();
        drafted.setLeagueName("Banitsa Cup");
        when(leagueDraftService.draft(anyInt(), any())).thenReturn(drafted);
        Model model = new ExtendedModelMap();

        String view = controller.generate(8, List.of(), model, new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("leagues/wizard-new-teams");
        assertThat(model.getAttribute("leagueWizardForm")).isSameAs(drafted);
        assertThat(model.getAttribute("existingTeams")).isNotNull();
        assertThat(model.getAttribute("statusMessage")).isEqualTo("flash.leaguedraft.generated");
    }

    @Test
    void generate_withEligibleSelection_passesThemToTheService() {
        UUID id = UUID.randomUUID();
        TeamDto eligible = team(id, null, 8);
        when(teamService.findById(id)).thenReturn(eligible);
        when(leagueDraftService.draft(anyInt(), any())).thenReturn(new LeagueWizardForm());
        Model model = new ExtendedModelMap();

        controller.generate(8, List.of(id), model, new RedirectAttributesModelMap());

        verify(leagueDraftService).draft(8, List.of(eligible));
        assertThat(model.getAttribute("existingTeams")).isEqualTo(List.of(eligible));
    }

    @Test
    void generate_aiFailure_flashesAndRedirectsToBlankWizard() {
        when(leagueDraftService.draft(anyInt(), any()))
                .thenThrow(new NoSuchElementException("offline"));
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String view = controller.generate(8, null, new ExtendedModelMap(), redirectAttributes);

        assertThat(view).isEqualTo("redirect:/leagues/wizard/new-teams?format=8");
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage"))
                .isEqualTo("flash.leaguedraft.failed");
    }

    @Test
    void generate_aiFailureWithSelection_keepsExistingTeamIdsInRedirect() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(teamService.findById(first)).thenReturn(team(first, null, 8));
        when(teamService.findById(second)).thenReturn(team(second, null, 8));
        when(leagueDraftService.draft(anyInt(), any()))
                .thenThrow(new NoSuchElementException("offline"));

        String view = controller.generate(8, List.of(first, second), new ExtendedModelMap(),
                new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("redirect:/leagues/wizard/new-teams?format=8"
                + "&existingTeamIds=" + first + "&existingTeamIds=" + second);
    }

    private TeamDto team(UUID id, UUID leagueId, long playerCount) {
        TeamDto dto = new TeamDto();
        dto.setId(id);
        dto.setName("Team " + id);
        dto.setCity("Sofia");
        dto.setLeagueId(leagueId);
        dto.setPlayerCount(playerCount);
        return dto;
    }
}
