package bg.softuni.footballleague.controller;

import bg.softuni.footballleague.model.Role;
import bg.softuni.footballleague.model.User;
import bg.softuni.footballleague.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserAdminControllerTest {

    @Mock private UserService userService;

    @InjectMocks private UserAdminController controller;

    private final Authentication auth = mock(Authentication.class);

    @Test
    void list_returnsView() {
        when(userService.findAllPaged(anyInt(), anyInt())).thenReturn(Page.empty());
        when(userService.countByRole(any())).thenReturn(1L);
        Model model = new ExtendedModelMap();

        assertThat(controller.list(0, model)).isEqualTo("admin/users");
        assertThat(model.getAttribute("adminCount")).isEqualTo(1L);
    }

    @Test
    void changeRole_success_setsStatusMessage() {
        UUID id = UUID.randomUUID();
        when(auth.getName()).thenReturn("admin");
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.changeRole(id, Role.ADMIN, "bob", auth, req, resp, ra);

        assertThat(view).isEqualTo("redirect:/admin/users");
        assertThat(ra.getFlashAttributes().get("statusMessage")).isEqualTo("Role updated successfully.");
    }

    @Test
    void changeRole_selfDemote_logsOutAndRedirectsToLogin() {
        UUID id = UUID.randomUUID();
        when(auth.getName()).thenReturn("admin");
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.changeRole(id, Role.USER, "admin", auth, req, resp, ra);

        assertThat(view).isEqualTo("redirect:/login");
    }

    @Test
    void changeRole_exception_setsError() {
        UUID id = UUID.randomUUID();
        when(auth.getName()).thenReturn("admin");
        doThrow(new IllegalStateException("last admin"))
                .when(userService).changeRole(eq(id), any(), eq("admin"));
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.changeRole(id, Role.USER, "bob", auth, req, resp, ra);

        assertThat(view).isEqualTo("redirect:/admin/users");
        assertThat(ra.getFlashAttributes()).containsKey("errorMessage");
    }
}
