package com.kickoffsim.controller;

import com.kickoffsim.dto.LeagueBundlePayload;
import com.kickoffsim.dto.LeagueDto;
import com.kickoffsim.dto.LeagueWizardForm;
import com.kickoffsim.dto.PlayerDto;
import com.kickoffsim.dto.PlayerRowDto;
import com.kickoffsim.dto.TeamCreateForm;
import com.kickoffsim.dto.TeamDto;
import com.kickoffsim.dto.TeamSquadPayload;
import com.kickoffsim.model.LeagueFormat;
import com.kickoffsim.service.ChangeRequestService;
import com.kickoffsim.service.LeagueService;
import com.kickoffsim.service.TeamService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeagueWizardControllerTest {

    @Mock private TeamService teamService;
    @Mock private LeagueService leagueService;
    @Mock private ChangeRequestService changeRequestService;

    @InjectMocks private LeagueWizardController controller;

    private final Authentication auth = mock(Authentication.class);

    private List<PlayerRowDto> squadOf(int count) {
        List<PlayerRowDto> rows = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            PlayerRowDto row = new PlayerRowDto();
            row.setShirtNumber(i);
            row.setFirstName("First" + i);
            row.setLastName("Last" + i);
            rows.add(row);
        }
        return rows;
    }

    private TeamCreateForm validTeamForm(String name, String city) {
        TeamCreateForm form = new TeamCreateForm();
        form.setName(name);
        form.setCity(city);
        form.setPlayers(squadOf(6));
        return form;
    }

    private LeagueWizardForm validForm(int numNewTeams) {
        LeagueWizardForm form = new LeagueWizardForm();
        form.setLeagueName("Test League");
        for (int i = 0; i < numNewTeams; i++) {
            form.getNewTeams().add(validTeamForm("Team" + i, "City" + i));
        }
        return form;
    }

    private TeamDto team(UUID id, String name, UUID leagueId, long playerCount) {
        TeamDto t = new TeamDto();
        t.setId(id);
        t.setName(name);
        t.setLeagueId(leagueId);
        t.setPlayerCount(playerCount);
        return t;
    }

    @Test
    void start_populatesModelAndReturnsView() {
        Model model = new ExtendedModelMap();

        assertThat(controller.start(model)).isEqualTo("leagues/wizard-format");
        assertThat((LeagueFormat[]) model.getAttribute("formats")).containsExactly(LeagueFormat.values());
    }

    @Test
    void chooseTeams_invalidFormat_redirectsToWizardStart() {
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.chooseTeams(7, model, ra);

        assertThat(view).isEqualTo("redirect:/leagues/wizard");
        assertThat(ra.getFlashAttributes().get("warnMessage")).isEqualTo("Invalid league format.");
    }

    @Test
    void chooseTeams_noEligibleFreeTeams_redirectsStraightToNewTeams() {
        when(teamService.findAllFree()).thenReturn(List.of(team(UUID.randomUUID(), "TooSmall", null, 3)));
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.chooseTeams(6, model, ra);

        assertThat(view).isEqualTo("redirect:/leagues/wizard/new-teams?format=6");
    }

    @Test
    void chooseTeams_hasEligibleFreeTeams_returnsTeamsView() {
        TeamDto eligible = team(UUID.randomUUID(), "Eligible", null, 8);
        when(teamService.findAllFree()).thenReturn(List.of(eligible));
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.chooseTeams(6, model, ra);

        assertThat(view).isEqualTo("leagues/wizard-teams");
        assertThat(model.getAttribute("format")).isEqualTo(6);
        assertThat((List<TeamDto>) model.getAttribute("availableTeams")).containsExactly(eligible);
    }

    @Test
    void newTeams_invalidFormat_redirectsToWizardStart() {
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.newTeams(7, null, model, ra);

        assertThat(view).isEqualTo("redirect:/leagues/wizard");
        assertThat(ra.getFlashAttributes().get("warnMessage")).isEqualTo("Invalid league format.");
    }

    @Test
    void newTeams_tooManySelected_redirectsToTeamsStepWithWarning() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 7; i++) ids.add(UUID.randomUUID());
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.newTeams(6, ids, model, ra);

        assertThat(view).isEqualTo("redirect:/leagues/wizard/teams?format=6");
        assertThat(ra.getFlashAttributes().get("warnMessage")).asString().contains("more teams");
    }

    @Test
    void newTeams_ineligibleTeamSelected_redirectsToTeamsStepWithWarning() {
        UUID id1 = UUID.randomUUID();
        when(teamService.findById(id1)).thenReturn(team(id1, "Existing", UUID.randomUUID(), 8));
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.newTeams(6, List.of(id1), model, ra);

        assertThat(view).isEqualTo("redirect:/leagues/wizard/teams?format=6");
        assertThat(ra.getFlashAttributes().get("warnMessage")).asString().contains("no longer eligible");
    }

    @Test
    void newTeams_teamWithoutLeagueButTooFewPlayers_redirectsToTeamsStepWithWarning() {
        UUID id1 = UUID.randomUUID();
        when(teamService.findById(id1)).thenReturn(team(id1, "TooSmall", null, 3));
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.newTeams(6, List.of(id1), model, ra);

        assertThat(view).isEqualTo("redirect:/leagues/wizard/teams?format=6");
        assertThat(ra.getFlashAttributes().get("warnMessage")).asString().contains("no longer eligible");
    }

    @Test
    void newTeams_nullExistingTeamIds_createsShortfallForAllTeams() {
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.newTeams(6, null, model, ra);

        assertThat(view).isEqualTo("leagues/wizard-new-teams");
        LeagueWizardForm form = (LeagueWizardForm) model.getAttribute("leagueWizardForm");
        assertThat(form.getNewTeams()).hasSize(6);
        assertThat(form.getExistingTeamIds()).isEmpty();
    }

    @Test
    void newTeams_validSelectionWithShortfall_returnsNewTeamsView() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(teamService.findById(id1)).thenReturn(team(id1, "Alpha", null, 8));
        when(teamService.findById(id2)).thenReturn(team(id2, "Beta", null, 6));
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.newTeams(8, List.of(id1, id2), model, ra);

        assertThat(view).isEqualTo("leagues/wizard-new-teams");
        LeagueWizardForm form = (LeagueWizardForm) model.getAttribute("leagueWizardForm");
        assertThat(form.getNewTeams()).hasSize(6);
        assertThat(form.getExistingTeamIds()).containsExactly(id1, id2);
        assertThat((List<?>) model.getAttribute("existingTeams")).hasSize(2);
    }

    @Test
    void newTeams_exactMatchNoShortfall_createsNoNewTeamBlocks() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            when(teamService.findById(id)).thenReturn(team(id, "Team" + i, null, 6));
        }
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.newTeams(6, ids, model, ra);

        assertThat(view).isEqualTo("leagues/wizard-new-teams");
        LeagueWizardForm form = (LeagueWizardForm) model.getAttribute("leagueWizardForm");
        assertThat(form.getNewTeams()).isEmpty();
    }

    @Test
    void submit_invalidFormat_rejectsAndReturnsView() {
        LeagueWizardForm form = validForm(6);
        form.setFormat(7);
        BindingResult br = new BeanPropertyBindingResult(form, "leagueWizardForm");
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.submit(form, br, null, model, auth, ra);

        assertThat(view).isEqualTo("leagues/wizard-new-teams");
        assertThat(br.hasErrors()).isTrue();
    }

    @Test
    void submit_existingTeamIneligibleDueToLeague_rejects() {
        UUID id1 = UUID.randomUUID();
        when(teamService.findById(id1)).thenReturn(team(id1, "AlreadyAssigned", UUID.randomUUID(), 8));
        LeagueWizardForm form = validForm(5);
        form.setFormat(6);
        form.setExistingTeamIds(List.of(id1));
        BindingResult br = new BeanPropertyBindingResult(form, "leagueWizardForm");
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.submit(form, br, null, model, auth, ra);

        assertThat(view).isEqualTo("leagues/wizard-new-teams");
        assertThat(br.hasErrors()).isTrue();
        assertThat((List<?>) model.getAttribute("existingTeams")).hasSize(1);
    }

    @Test
    void submit_existingTeamBelowMinPlayers_rejects() {
        UUID id1 = UUID.randomUUID();
        when(teamService.findById(id1)).thenReturn(team(id1, "TooSmall", null, 3));
        LeagueWizardForm form = validForm(5);
        form.setFormat(6);
        form.setExistingTeamIds(List.of(id1));
        BindingResult br = new BeanPropertyBindingResult(form, "leagueWizardForm");
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.submit(form, br, null, model, auth, ra);

        assertThat(view).isEqualTo("leagues/wizard-new-teams");
        assertThat(br.hasErrors()).isTrue();
    }

    @Test
    void submit_countMismatch_rejects() {
        LeagueWizardForm form = validForm(5);
        form.setFormat(6);
        BindingResult br = new BeanPropertyBindingResult(form, "leagueWizardForm");
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.submit(form, br, null, model, auth, ra);

        assertThat(view).isEqualTo("leagues/wizard-new-teams");
        assertThat(br.hasErrors()).isTrue();
    }

    @Test
    void submit_newTeamNameNullOrBlank_rejectsButLeavesOthersValid() {
        LeagueWizardForm form = validForm(6);
        form.setFormat(6);
        form.getNewTeams().get(0).setName(null);
        form.getNewTeams().get(1).setName("   ");
        BindingResult br = new BeanPropertyBindingResult(form, "leagueWizardForm");
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.submit(form, br, null, model, auth, ra);

        assertThat(view).isEqualTo("leagues/wizard-new-teams");
        assertThat(br.hasFieldErrors("newTeams[0].name")).isTrue();
        assertThat(br.hasFieldErrors("newTeams[1].name")).isTrue();
    }

    @Test
    void submit_newTeamCityNullOrBlank_rejects() {
        LeagueWizardForm form = validForm(6);
        form.setFormat(6);
        form.getNewTeams().get(0).setCity(null);
        form.getNewTeams().get(1).setCity("   ");
        BindingResult br = new BeanPropertyBindingResult(form, "leagueWizardForm");
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.submit(form, br, null, model, auth, ra);

        assertThat(view).isEqualTo("leagues/wizard-new-teams");
        assertThat(br.hasFieldErrors("newTeams[0].city")).isTrue();
        assertThat(br.hasFieldErrors("newTeams[1].city")).isTrue();
    }

    @Test
    void submit_newTeamSquadBelowMinimum_rejects() {
        LeagueWizardForm form = validForm(6);
        form.setFormat(6);
        form.getNewTeams().get(0).setPlayers(squadOf(3));
        BindingResult br = new BeanPropertyBindingResult(form, "leagueWizardForm");
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.submit(form, br, null, model, auth, ra);

        assertThat(view).isEqualTo("leagues/wizard-new-teams");
        assertThat(br.hasErrors()).isTrue();
    }

    @Test
    void submit_newTeamSquadAboveMaximum_rejects() {
        LeagueWizardForm form = validForm(6);
        form.setFormat(6);
        form.getNewTeams().get(0).setPlayers(squadOf(13));
        BindingResult br = new BeanPropertyBindingResult(form, "leagueWizardForm");
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.submit(form, br, null, model, auth, ra);

        assertThat(view).isEqualTo("leagues/wizard-new-teams");
        assertThat(br.hasErrors()).isTrue();
    }

    @Test
    void submit_duplicateNameWithinBatch_rejects() {
        LeagueWizardForm form = validForm(6);
        form.setFormat(6);
        form.getNewTeams().get(1).setName(form.getNewTeams().get(0).getName());
        form.getNewTeams().get(1).setCity(form.getNewTeams().get(0).getCity());
        BindingResult br = new BeanPropertyBindingResult(form, "leagueWizardForm");
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.submit(form, br, null, model, auth, ra);

        assertThat(view).isEqualTo("leagues/wizard-new-teams");
        assertThat(br.hasFieldErrors("newTeams[1].name")).isTrue();
    }

    @Test
    void submit_nameAlreadyExistsInDb_rejects() {
        LeagueWizardForm form = validForm(6);
        form.setFormat(6);
        when(teamService.existsByNameAndCity("Team0", "City0")).thenReturn(true);
        BindingResult br = new BeanPropertyBindingResult(form, "leagueWizardForm");
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.submit(form, br, null, model, auth, ra);

        assertThat(view).isEqualTo("leagues/wizard-new-teams");
        assertThat(br.hasFieldErrors("newTeams[0].name")).isTrue();
    }

    @Test
    void submit_dataIntegrityViolation_rejectsAndReturnsView() {
        LeagueWizardForm form = validForm(6);
        form.setFormat(6);
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("dup"));
        BindingResult br = new BeanPropertyBindingResult(form, "leagueWizardForm");
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.submit(form, br, null, model, auth, ra);

        assertThat(view).isEqualTo("leagues/wizard-new-teams");
        assertThat(br.hasErrors()).isTrue();
    }

    @Test
    void submit_eligibleExistingTeam_executedWithMatchingLeague_redirectsToLeagueDetail() {
        UUID id1 = UUID.randomUUID();
        when(teamService.findById(id1)).thenReturn(team(id1, "EligibleTeam", null, 8));
        LeagueWizardForm form = validForm(5);
        form.setFormat(6);
        form.setExistingTeamIds(new ArrayList<>(List.of(id1)));
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(true);
        UUID leagueId = UUID.randomUUID();
        LeagueDto dto = new LeagueDto();
        dto.setId(leagueId);
        dto.setName("Test League");
        when(leagueService.findAll()).thenReturn(List.of(dto));
        BindingResult br = new BeanPropertyBindingResult(form, "leagueWizardForm");
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.submit(form, br, null, model, auth, ra);

        assertThat(br.hasErrors()).isFalse();
        assertThat(view).isEqualTo("redirect:/leagues/" + leagueId + "#schedule");
        assertThat(ra.getFlashAttributes().get("statusMessage")).isEqualTo("League created and schedule generated.");
    }

    @Test
    void submit_valid_executedNoMatchingLeague_redirectsToLeaguesList() {
        LeagueWizardForm form = validForm(6);
        form.setFormat(6);
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(true);
        when(leagueService.findAll()).thenReturn(List.of());
        BindingResult br = new BeanPropertyBindingResult(form, "leagueWizardForm");
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.submit(form, br, null, model, auth, ra);

        assertThat(view).isEqualTo("redirect:/leagues");
        assertThat(ra.getFlashAttributes().get("statusMessage")).isEqualTo("League created and schedule generated.");
    }

    @Test
    void submit_valid_notExecuted_redirectsWithApprovalMessage() {
        LeagueWizardForm form = validForm(6);
        form.setFormat(6);
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(false);
        BindingResult br = new BeanPropertyBindingResult(form, "leagueWizardForm");
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.submit(form, br, null, model, auth, ra);

        assertThat(view).isEqualTo("redirect:/leagues");
        assertThat(ra.getFlashAttributes().get("statusMessage")).isEqualTo("Submitted for admin approval.");
    }

    private LeagueBundlePayload bundlePayload(UUID existingTeamId) {
        LeagueBundlePayload payload = new LeagueBundlePayload();
        payload.setLeagueName("Rejected League");
        payload.setFormat(6);
        payload.setExistingTeamIds(List.of(existingTeamId));

        TeamDto teamDto = new TeamDto();
        teamDto.setName("Newcomers");
        teamDto.setCity("Varna");
        TeamSquadPayload squad = new TeamSquadPayload();
        squad.setTeam(teamDto);
        PlayerDto player = new PlayerDto();
        player.setShirtNumber(9);
        player.setFirstName("First");
        player.setLastName("Last");
        squad.setPlayers(List.of(player));
        payload.setNewTeams(List.of(squad));
        return payload;
    }

    @Test
    void edit_loadsPayloadAndPopulatesForm() {
        UUID reqId = UUID.randomUUID();
        UUID existingId = UUID.randomUUID();
        when(changeRequestService.getPayloadForResubmit(reqId, auth)).thenReturn(bundlePayload(existingId));
        when(teamService.findById(existingId)).thenReturn(team(existingId, "Existing", null, 8));
        Model model = new ExtendedModelMap();

        String view = controller.edit(reqId, model, auth);

        assertThat(view).isEqualTo("leagues/wizard-new-teams");
        LeagueWizardForm form = (LeagueWizardForm) model.getAttribute("leagueWizardForm");
        assertThat(form.getLeagueName()).isEqualTo("Rejected League");
        assertThat(form.getFormat()).isEqualTo(6);
        assertThat(form.getExistingTeamIds()).containsExactly(existingId);
        assertThat(form.getNewTeams()).hasSize(1);
        assertThat(form.getNewTeams().get(0).getName()).isEqualTo("Newcomers");
        assertThat(form.getNewTeams().get(0).getCity()).isEqualTo("Varna");
        assertThat(form.getNewTeams().get(0).getPlayers()).hasSize(1);
        assertThat((List<?>) model.getAttribute("existingTeams")).hasSize(1);
    }

    @Test
    void edit_withRejectionReason_showsErrorBanner() {
        UUID reqId = UUID.randomUUID();
        UUID existingId = UUID.randomUUID();
        when(changeRequestService.getPayloadForResubmit(reqId, auth)).thenReturn(bundlePayload(existingId));
        when(teamService.findById(existingId)).thenReturn(team(existingId, "Existing", null, 8));
        when(changeRequestService.getRejectionReason(reqId, auth)).thenReturn("Team name already exists");
        Model model = new ExtendedModelMap();

        controller.edit(reqId, model, auth);

        assertThat(model.getAttribute("errorMessage")).isEqualTo("Rejected: Team name already exists");
        assertThat(model.getAttribute("fromRequest")).isEqualTo(reqId);
    }

    @Test
    void edit_noRejectionReason_noBanner() {
        UUID reqId = UUID.randomUUID();
        UUID existingId = UUID.randomUUID();
        when(changeRequestService.getPayloadForResubmit(reqId, auth)).thenReturn(bundlePayload(existingId));
        when(teamService.findById(existingId)).thenReturn(team(existingId, "Existing", null, 8));
        when(changeRequestService.getRejectionReason(reqId, auth)).thenReturn(null);
        Model model = new ExtendedModelMap();

        controller.edit(reqId, model, auth);

        assertThat(model.getAttribute("errorMessage")).isNull();
    }

    @Test
    void submit_withFromRequest_bindingErrors_addsFromRequestToModel() {
        UUID reqId = UUID.randomUUID();
        LeagueWizardForm form = validForm(6);
        form.setFormat(7);
        BindingResult br = new BeanPropertyBindingResult(form, "leagueWizardForm");
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.submit(form, br, reqId, model, auth, ra);

        assertThat(view).isEqualTo("leagues/wizard-new-teams");
        assertThat(model.getAttribute("fromRequest")).isEqualTo(reqId);
    }

    @Test
    void submit_withFromRequest_dataIntegrityViolation_addsFromRequestToModel() {
        UUID reqId = UUID.randomUUID();
        LeagueWizardForm form = validForm(6);
        form.setFormat(6);
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("dup"));
        BindingResult br = new BeanPropertyBindingResult(form, "leagueWizardForm");
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.submit(form, br, reqId, model, auth, ra);

        assertThat(view).isEqualTo("leagues/wizard-new-teams");
        assertThat(model.getAttribute("fromRequest")).isEqualTo(reqId);
    }

    @Test
    void submit_withFromRequest_executed_cancelsPendingRequest() {
        UUID reqId = UUID.randomUUID();
        LeagueWizardForm form = validForm(6);
        form.setFormat(6);
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(true);
        when(leagueService.findAll()).thenReturn(List.of());
        BindingResult br = new BeanPropertyBindingResult(form, "leagueWizardForm");
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        controller.submit(form, br, reqId, model, auth, ra);

        verify(changeRequestService).cancelIfPending(reqId, auth);
    }

    @Test
    void submit_withFromRequest_notExecuted_cancelsPendingRequest() {
        UUID reqId = UUID.randomUUID();
        LeagueWizardForm form = validForm(6);
        form.setFormat(6);
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(false);
        BindingResult br = new BeanPropertyBindingResult(form, "leagueWizardForm");
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        controller.submit(form, br, reqId, model, auth, ra);

        verify(changeRequestService).cancelIfPending(reqId, auth);
    }
}
