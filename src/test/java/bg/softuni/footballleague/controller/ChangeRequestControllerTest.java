package bg.softuni.footballleague.controller;

import bg.softuni.footballleague.exception.ChangeRequestApprovalException;
import bg.softuni.footballleague.service.ChangeRequestService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChangeRequestControllerTest {

    @Mock private ChangeRequestService changeRequestService;

    @InjectMocks private ChangeRequestController controller;

    private final Authentication auth = mock(Authentication.class);

    @Test
    void list_returnsView() {
        when(changeRequestService.findPending()).thenReturn(List.of());
        Model model = new ExtendedModelMap();

        assertThat(controller.list(model)).isEqualTo("admin/change-requests");
    }

    @Test
    void approve_success_setsStatusMessage() {
        UUID id = UUID.randomUUID();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.approve(id, auth, ra)).isEqualTo("redirect:/admin/change-requests");
        assertThat(ra.getFlashAttributes().get("statusMessage")).isEqualTo("Change request approved.");
    }

    @Test
    void approve_approvalException_setsError() {
        UUID id = UUID.randomUUID();
        doThrow(new ChangeRequestApprovalException("nope"))
                .when(changeRequestService).approve(eq(id), any());
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.approve(id, auth, ra)).isEqualTo("redirect:/admin/change-requests");
        assertThat(ra.getFlashAttributes()).containsKey("errorMessage");
    }

    @Test
    void reject_success_setsStatusMessage() {
        UUID id = UUID.randomUUID();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.reject(id, "spam", auth, ra)).isEqualTo("redirect:/admin/change-requests");
        assertThat(ra.getFlashAttributes().get("statusMessage")).isEqualTo("Change request rejected.");
    }

    @Test
    void reject_approvalException_setsError() {
        UUID id = UUID.randomUUID();
        doThrow(new ChangeRequestApprovalException("cannot"))
                .when(changeRequestService).reject(eq(id), any(), any());
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.reject(id, null, auth, ra)).isEqualTo("redirect:/admin/change-requests");
        assertThat(ra.getFlashAttributes()).containsKey("errorMessage");
    }
}
