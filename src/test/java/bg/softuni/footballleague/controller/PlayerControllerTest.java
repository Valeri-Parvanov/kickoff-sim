package bg.softuni.footballleague.controller;

import bg.softuni.footballleague.dto.PlayerDto;
import bg.softuni.footballleague.exception.SquadLimitExceededException;
import bg.softuni.footballleague.service.ChangeRequestService;
import bg.softuni.footballleague.service.PlayerService;
import bg.softuni.footballleague.service.TeamService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Sort;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlayerControllerTest {

    @Mock private PlayerService playerService;
    @Mock private TeamService teamService;
    @Mock private ChangeRequestService changeRequestService;

    @InjectMocks private PlayerController controller;

    private final Authentication auth = mock(Authentication.class);

    @Test
    void list_returnsView() {
        when(playerService.findAll(any(Sort.class))).thenReturn(List.of());
        Model model = new ExtendedModelMap();

        assertThat(controller.list(null, null, model)).isEqualTo("players/list");
    }

    @Test
    void editForm_returnsForm() {
        UUID id = UUID.randomUUID();
        PlayerDto dto = new PlayerDto();
        when(playerService.findById(id)).thenReturn(dto);
        when(teamService.findAll()).thenReturn(List.of());
        Model model = new ExtendedModelMap();

        assertThat(controller.editForm(id, null, model, auth)).isEqualTo("players/form");
        assertThat(model.getAttribute("playerDto")).isNotNull();
    }

    @Test
    void edit_valid_executed_redirectsToTeam() {
        UUID id = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        PlayerDto dto = new PlayerDto();
        dto.setTeamId(teamId);
        BindingResult br = new BeanPropertyBindingResult(dto, "playerDto");
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(true);
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.edit(id, dto, br, null, model, auth, ra);

        assertThat(view).isEqualTo("redirect:/teams/" + teamId);
        assertThat(ra.getFlashAttributes().get("statusMessage")).isEqualTo("Player updated.");
    }

    @Test
    void edit_squadFull_returnsFormWithError() {
        UUID id = UUID.randomUUID();
        PlayerDto dto = new PlayerDto();
        dto.setTeamId(UUID.randomUUID());
        BindingResult br = new BeanPropertyBindingResult(dto, "playerDto");
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any()))
                .thenThrow(new SquadLimitExceededException("full"));
        when(teamService.findAll()).thenReturn(List.of());
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.edit(id, dto, br, null, model, auth, ra);

        assertThat(view).isEqualTo("players/form");
        assertThat(br.hasErrors()).isTrue();
    }

    @Test
    void delete_notExecuted_redirectsToTeams() {
        UUID id = UUID.randomUUID();
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(false);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.delete(id, null, auth, ra)).isEqualTo("redirect:/teams");
        assertThat(ra.getFlashAttributes().get("statusMessage")).isEqualTo("Submitted for admin approval.");
    }
}
