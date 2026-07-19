package com.kickoffsim.controller;

import com.kickoffsim.dto.PlayerDto;
import com.kickoffsim.dto.PlayerRowDto;
import com.kickoffsim.dto.SquadForm;
import com.kickoffsim.dto.TeamDto;
import com.kickoffsim.dto.TeamSquadPayload;
import com.kickoffsim.model.ChangeAction;
import com.kickoffsim.model.EntityType;
import com.kickoffsim.service.ChangeRequestService;
import com.kickoffsim.service.PlayerService;
import com.kickoffsim.service.TeamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SquadControllerTest {

    @Mock private TeamService teamService;
    @Mock private PlayerService playerService;
    @Mock private ChangeRequestService changeRequestService;
    @Mock private Authentication authentication;

    @InjectMocks
    private SquadController squadController;

    private UUID teamId;
    private TeamDto team;

    @BeforeEach
    void setUp() {
        teamId = UUID.randomUUID();
        team = new TeamDto();
        team.setId(teamId);
        team.setName("Test FC");
        team.setLeagueId(UUID.randomUUID());

        when(teamService.findById(teamId)).thenReturn(team);
        when(playerService.squadRemainingSlots(teamId)).thenReturn(12);
        when(playerService.findAllByTeam(teamId)).thenReturn(List.of());
    }

    @Test
    void form_emptyTeam_rendersTwelveRowsWithSequentialShirtNumbers() {
        Model model = new ExtendedModelMap();

        String view = squadController.form(teamId, model);

        assertThat(view).isEqualTo("teams/squad");
        SquadForm squadForm = (SquadForm) model.getAttribute("squadForm");
        assertThat(squadForm).isNotNull();
        assertThat(squadForm.getRows()).hasSize(12);
        assertThat(squadForm.getRows().get(0).getShirtNumber()).isEqualTo(1);
        assertThat(squadForm.getRows().get(11).getShirtNumber()).isEqualTo(12);
    }

    @Test
    void form_fullSquad_doesNotAddForm() {
        when(playerService.squadRemainingSlots(teamId)).thenReturn(0);
        Model model = new ExtendedModelMap();

        String view = squadController.form(teamId, model);

        assertThat(view).isEqualTo("teams/squad");
        assertThat(model.getAttribute("squadForm")).isNull();
        assertThat(model.getAttribute("remaining")).isEqualTo(0);
    }

    @Test
    void form_fewerFreeNumbersThanRemaining_leavesExtraRowsBlank() {
        List<PlayerDto> taken = new java.util.ArrayList<>();
        for (int shirt = 1; shirt <= 97; shirt++) {
            PlayerDto p = new PlayerDto();
            p.setShirtNumber(shirt);
            taken.add(p);
        }
        when(playerService.findAllByTeam(teamId)).thenReturn(taken);
        when(playerService.squadRemainingSlots(teamId)).thenReturn(5);
        Model model = new ExtendedModelMap();

        squadController.form(teamId, model);

        SquadForm squadForm = (SquadForm) model.getAttribute("squadForm");
        assertThat(squadForm.getRows()).hasSize(5);
        assertThat(squadForm.getRows().get(0).getShirtNumber()).isEqualTo(98);
        assertThat(squadForm.getRows().get(1).getShirtNumber()).isEqualTo(99);
        assertThat(squadForm.getRows().get(2).getShirtNumber()).isNull();
        assertThat(squadForm.getRows().get(3).getShirtNumber()).isNull();
        assertThat(squadForm.getRows().get(4).getShirtNumber()).isNull();
    }

    @Test
    void submit_validRows_admin_createsOneSquadRequest() {
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(true);

        SquadForm form = squadFormOf(row(1, "Ivan", "Ivanov"), row(2, "Georgi", "Petrov"));
        BindingResult binding = binding(form);

        String view = squadController.submit(teamId, form, binding, new ExtendedModelMap(),
                authentication, new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("redirect:/teams");
        assertThat(binding.hasErrors()).isFalse();

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(changeRequestService).submitOrExecute(eq(EntityType.TEAM_SQUAD), eq(ChangeAction.CREATE),
                payload.capture(), eq(null), eq(authentication));
        assertThat(((TeamSquadPayload) payload.getValue()).getPlayers()).hasSize(2);
    }

    @Test
    void submit_validRows_user_submitsForApproval() {
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(false);

        SquadForm form = squadFormOf(row(1, "Ivan", "Ivanov"));
        BindingResult binding = binding(form);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = squadController.submit(teamId, form, binding, new ExtendedModelMap(),
                authentication, redirect);

        assertThat(view).isEqualTo("redirect:/teams");
        assertThat((String) redirect.getFlashAttributes().get("statusMessage")).contains("approval");
    }

    @Test
    void submit_ignoresEmptyRows() {
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(true);

        SquadForm form = squadFormOf(row(1, "Ivan", "Ivanov"), new PlayerRowDto());
        BindingResult binding = binding(form);

        squadController.submit(teamId, form, binding, new ExtendedModelMap(),
                authentication, new RedirectAttributesModelMap());

        assertThat(binding.hasErrors()).isFalse();
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(changeRequestService).submitOrExecute(eq(EntityType.TEAM_SQUAD), any(),
                payload.capture(), any(), any());
        assertThat(((TeamSquadPayload) payload.getValue()).getPlayers()).hasSize(1);
    }

    @Test
    void submit_prefilledNumberButBlankNames_isSkipped() {
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(true);

        SquadForm form = squadFormOf(row(1, "Ivan", "Ivanov"), row(2, null, null));
        BindingResult binding = binding(form);

        squadController.submit(teamId, form, binding, new ExtendedModelMap(),
                authentication, new RedirectAttributesModelMap());

        assertThat(binding.hasErrors()).isFalse();
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(changeRequestService).submitOrExecute(eq(EntityType.TEAM_SQUAD), any(),
                payload.capture(), any(), any());
        assertThat(((TeamSquadPayload) payload.getValue()).getPlayers()).hasSize(1);
    }

    @Test
    void submit_duplicateShirtNumberInBatch_addsFieldErrorAndSavesNothing() {
        SquadForm form = squadFormOf(row(7, "Ivan", "Ivanov"), row(7, "Georgi", "Petrov"));
        BindingResult binding = binding(form);

        String view = squadController.submit(teamId, form, binding, new ExtendedModelMap(),
                authentication, new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("teams/squad");
        assertThat(binding.hasFieldErrors("rows[1].shirtNumber")).isTrue();
        verify(changeRequestService, never()).submitOrExecute(any(), any(), any(), any(), any());
    }

    @Test
    void submit_shirtNumberTakenByExistingPlayer_addsFieldError() {
        PlayerDto existing = new PlayerDto();
        existing.setShirtNumber(5);
        when(playerService.findAllByTeam(teamId)).thenReturn(List.of(existing));

        SquadForm form = squadFormOf(row(5, "Ivan", "Ivanov"));
        BindingResult binding = binding(form);

        String view = squadController.submit(teamId, form, binding, new ExtendedModelMap(),
                authentication, new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("teams/squad");
        assertThat(binding.hasFieldErrors("rows[0].shirtNumber")).isTrue();
        verify(changeRequestService, never()).submitOrExecute(any(), any(), any(), any(), any());
    }

    @Test
    void submit_missingName_addsFieldError() {
        SquadForm form = squadFormOf(row(1, "", "Ivanov"));
        BindingResult binding = binding(form);

        String view = squadController.submit(teamId, form, binding, new ExtendedModelMap(),
                authentication, new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("teams/squad");
        assertThat(binding.hasFieldErrors("rows[0].firstName")).isTrue();
    }

    @Test
    void submit_shirtNumberOutOfRange_addsFieldError() {
        SquadForm form = squadFormOf(row(150, "Ivan", "Ivanov"));
        BindingResult binding = binding(form);

        squadController.submit(teamId, form, binding, new ExtendedModelMap(),
                authentication, new RedirectAttributesModelMap());

        assertThat(binding.hasFieldErrors("rows[0].shirtNumber")).isTrue();
    }

    @Test
    void submit_noFilledRows_addsGlobalError() {
        SquadForm form = squadFormOf(new PlayerRowDto(), new PlayerRowDto());
        BindingResult binding = binding(form);

        String view = squadController.submit(teamId, form, binding, new ExtendedModelMap(),
                authentication, new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("teams/squad");
        assertThat(binding.getGlobalErrorCount()).isGreaterThan(0);
        verify(changeRequestService, never()).submitOrExecute(any(), any(), any(), any(), any());
    }

    @Test
    void submit_exceedsRemainingCapacity_addsGlobalError() {
        when(playerService.squadRemainingSlots(teamId)).thenReturn(1);

        SquadForm form = squadFormOf(row(1, "Ivan", "Ivanov"), row(2, "Georgi", "Petrov"));
        BindingResult binding = binding(form);

        String view = squadController.submit(teamId, form, binding, new ExtendedModelMap(),
                authentication, new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("teams/squad");
        assertThat(binding.getGlobalErrorCount()).isGreaterThan(0);
        verify(changeRequestService, never()).submitOrExecute(any(), any(), any(), any(), any());
    }

    @Test
    void form_leaguelessTeam_setsEditModeAndLoadsExistingPlayersPaddedToTwelve() {
        team.setLeagueId(null);
        PlayerDto existing = new PlayerDto();
        existing.setId(UUID.randomUUID());
        existing.setShirtNumber(1);
        existing.setFirstName("Ivan");
        existing.setLastName("Ivanov");
        when(playerService.findAllByTeam(teamId)).thenReturn(List.of(existing));
        Model model = new ExtendedModelMap();

        String view = squadController.form(teamId, model);

        assertThat(view).isEqualTo("teams/squad");
        assertThat(model.getAttribute("editMode")).isEqualTo(true);
        SquadForm squadForm = (SquadForm) model.getAttribute("squadForm");
        assertThat(squadForm.getRows()).hasSize(12);
        assertThat(squadForm.getRows().get(0).getId()).isEqualTo(existing.getId());
        assertThat(squadForm.getRows().get(0).getFirstName()).isEqualTo("Ivan");
        assertThat(squadForm.getRows().get(1).getId()).isNull();
        assertThat(squadForm.getRows().get(1).getShirtNumber()).isEqualTo(2);
    }

    @Test
    void submit_leaguelessTeam_admin_sendsUpdateActionWithTeamIdAsTarget() {
        team.setLeagueId(null);
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(true);

        SquadForm form = squadFormOf(row(1, "Ivan", "Ivanov"), row(2, "Georgi", "Petrov"),
                row(3, "Petar", "Petrov"), row(4, "Stoyan", "Stoyanov"),
                row(5, "Dimitar", "Dimitrov"), row(6, "Hristo", "Hristov"));
        BindingResult binding = binding(form);

        String view = squadController.submit(teamId, form, binding, new ExtendedModelMap(),
                authentication, new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("redirect:/teams");
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(changeRequestService).submitOrExecute(eq(EntityType.TEAM_SQUAD), eq(ChangeAction.UPDATE),
                payload.capture(), eq(teamId), eq(authentication));
        assertThat(((TeamSquadPayload) payload.getValue()).getPlayers()).hasSize(6);
    }

    @Test
    void submit_leaguelessTeam_preservesRowIdForEditedExistingPlayer() {
        team.setLeagueId(null);
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(true);
        UUID existingId = UUID.randomUUID();

        SquadForm form = squadFormOf(
                rowWithId(existingId, 1, "Ivan", "Renamed"), row(2, "Georgi", "Petrov"),
                row(3, "Petar", "Petrov"), row(4, "Stoyan", "Stoyanov"),
                row(5, "Dimitar", "Dimitrov"), row(6, "Hristo", "Hristov"));
        BindingResult binding = binding(form);

        squadController.submit(teamId, form, binding, new ExtendedModelMap(),
                authentication, new RedirectAttributesModelMap());

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(changeRequestService).submitOrExecute(eq(EntityType.TEAM_SQUAD), eq(ChangeAction.UPDATE),
                payload.capture(), any(), any());
        assertThat(((TeamSquadPayload) payload.getValue()).getPlayers())
                .anyMatch(p -> existingId.equals(p.getId()) && "Renamed".equals(p.getLastName()));
    }

    @Test
    void submit_leaguelessTeam_belowMinimumSquadSize_addsGlobalError() {
        team.setLeagueId(null);

        SquadForm form = squadFormOf(row(1, "Ivan", "Ivanov"), row(2, "Georgi", "Petrov"));
        BindingResult binding = binding(form);

        String view = squadController.submit(teamId, form, binding, new ExtendedModelMap(),
                authentication, new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("teams/squad");
        assertThat(binding.getGlobalErrorCount()).isGreaterThan(0);
        verify(changeRequestService, never()).submitOrExecute(any(), any(), any(), any(), any());
    }

    @Test
    void submit_leaguelessTeam_user_submitsForApproval() {
        team.setLeagueId(null);
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(false);

        SquadForm form = squadFormOf(row(1, "Ivan", "Ivanov"), row(2, "Georgi", "Petrov"),
                row(3, "Petar", "Petrov"), row(4, "Stoyan", "Stoyanov"),
                row(5, "Dimitar", "Dimitrov"), row(6, "Hristo", "Hristov"));
        BindingResult binding = binding(form);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        squadController.submit(teamId, form, binding, new ExtendedModelMap(),
                authentication, redirect);

        assertThat((String) redirect.getFlashAttributes().get("statusMessage")).contains("approval");
    }

    private static PlayerRowDto row(Integer shirtNumber, String firstName, String lastName) {
        PlayerRowDto r = new PlayerRowDto();
        r.setShirtNumber(shirtNumber);
        r.setFirstName(firstName);
        r.setLastName(lastName);
        return r;
    }

    private static PlayerRowDto rowWithId(UUID id, Integer shirtNumber, String firstName, String lastName) {
        PlayerRowDto r = row(shirtNumber, firstName, lastName);
        r.setId(id);
        return r;
    }

    private static SquadForm squadFormOf(PlayerRowDto... rows) {
        SquadForm form = new SquadForm();
        for (PlayerRowDto r : rows) {
            form.getRows().add(r);
        }
        return form;
    }

    private static BindingResult binding(SquadForm form) {
        return new BeanPropertyBindingResult(form, "squadForm");
    }
}
