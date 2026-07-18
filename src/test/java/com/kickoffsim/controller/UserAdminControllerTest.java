package com.kickoffsim.controller;

import com.kickoffsim.model.Role;
import com.kickoffsim.model.User;
import com.kickoffsim.service.UserService;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;
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
    void list_smallPageCount_showsAllPageNumbers() {
        Page<User> page = new PageImpl<>(List.of(), PageRequest.of(1, 20), 41);
        when(userService.findAllPaged(1, 20)).thenReturn(page);
        when(userService.countByRole(any())).thenReturn(1L);
        Model model = new ExtendedModelMap();

        controller.list(1, model);

        assertThat(model.getAttribute("totalPages")).isEqualTo(3);
        assertThat(model.getAttribute("pageNumbers")).isEqualTo(List.of(0, 1, 2));
    }

    @Test
    void list_manyPages_middlePage_showsBothEllipses() {
        Page<User> page = new PageImpl<>(List.of(), PageRequest.of(5, 20), 200);
        when(userService.findAllPaged(5, 20)).thenReturn(page);
        when(userService.countByRole(any())).thenReturn(1L);
        Model model = new ExtendedModelMap();

        controller.list(5, model);

        assertThat(model.getAttribute("totalPages")).isEqualTo(10);
        assertThat(model.getAttribute("pageNumbers")).isEqualTo(List.of(0, -1, 3, 4, 5, 6, 7, -1, 9));
    }

    @Test
    void list_manyPages_firstPage_showsOnlyTrailingEllipsis() {
        Page<User> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 200);
        when(userService.findAllPaged(0, 20)).thenReturn(page);
        when(userService.countByRole(any())).thenReturn(1L);
        Model model = new ExtendedModelMap();

        controller.list(0, model);

        assertThat(model.getAttribute("pageNumbers")).isEqualTo(List.of(0, 1, 2, -1, 9));
    }

    @Test
    void list_manyPages_lastPage_showsOnlyLeadingEllipsis() {
        Page<User> page = new PageImpl<>(List.of(), PageRequest.of(9, 20), 200);
        when(userService.findAllPaged(9, 20)).thenReturn(page);
        when(userService.countByRole(any())).thenReturn(1L);
        Model model = new ExtendedModelMap();

        controller.list(9, model);

        assertThat(model.getAttribute("pageNumbers")).isEqualTo(List.of(0, -1, 7, 8, 9));
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
    void changeRole_selfButNotDemote_doesNotLogout() {
        UUID id = UUID.randomUUID();
        when(auth.getName()).thenReturn("admin");
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.changeRole(id, Role.ADMIN, "admin", auth, req, resp, ra);

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
