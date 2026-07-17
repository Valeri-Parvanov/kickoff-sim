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
class MyChangeRequestControllerTest {

    @Mock private ChangeRequestService changeRequestService;

    @InjectMocks private MyChangeRequestController controller;

    private final Authentication auth = mock(Authentication.class);

    @Test
    void myProposals_returnsView() {
        when(changeRequestService.findMine(auth)).thenReturn(List.of());
        Model model = new ExtendedModelMap();

        assertThat(controller.myProposals(auth, model, "ALL", "ALL", 0)).isEqualTo("my-change-requests");
        assertThat(model.getAttribute("totalCount")).isEqualTo(0);
    }

    @Test
    void cancel_success_setsStatusMessage() {
        UUID id = UUID.randomUUID();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.cancel(id, auth, ra)).isEqualTo("redirect:/my-change-requests");
        assertThat(ra.getFlashAttributes().get("statusMessage")).isEqualTo("Request cancelled.");
    }

    @Test
    void cancel_approvalException_setsError() {
        UUID id = UUID.randomUUID();
        doThrow(new ChangeRequestApprovalException("nope"))
                .when(changeRequestService).cancelMine(eq(id), any());
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.cancel(id, auth, ra)).isEqualTo("redirect:/my-change-requests");
        assertThat(ra.getFlashAttributes()).containsKey("errorMessage");
    }
}
