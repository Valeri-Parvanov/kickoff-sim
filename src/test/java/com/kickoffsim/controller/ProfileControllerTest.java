package com.kickoffsim.controller;

import com.kickoffsim.dto.ChangePasswordDto;
import com.kickoffsim.dto.DeactivateAccountDto;
import com.kickoffsim.dto.ProfileDto;
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
import org.springframework.security.core.Authentication;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileControllerTest {

    @Mock private UserService userService;

    @InjectMocks private ProfileController controller;

    private final Authentication auth = mock(Authentication.class);

    private User user() {
        User u = new User();
        u.setUsername("alice");
        u.setEmail("alice@example.com");
        u.setRole(Role.USER);
        return u;
    }

    @Test
    void profile_returnsViewWithDto() {
        when(auth.getName()).thenReturn("alice");
        when(userService.findByUsername("alice")).thenReturn(user());
        Model model = new ExtendedModelMap();

        assertThat(controller.profile(auth, model)).isEqualTo("profile");
        assertThat(model.getAttribute("profileDto")).isNotNull();
        assertThat(model.getAttribute("profileUsername")).isEqualTo("alice");
    }

    @Test
    void updateProfile_valid_redirects() {
        when(auth.getName()).thenReturn("alice");
        when(userService.findByUsername("alice")).thenReturn(user());
        ProfileDto dto = new ProfileDto();
        dto.setEmail("new@example.com");
        BindingResult br = new BeanPropertyBindingResult(dto, "profileDto");
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        Model model = new ExtendedModelMap();

        assertThat(controller.updateProfile(dto, br, auth, ra, model)).isEqualTo("redirect:/profile");
        assertThat(ra.getFlashAttributes()).containsKey("statusMessage");
    }

    @Test
    void updateProfile_bindingErrors_returnsView() {
        when(auth.getName()).thenReturn("alice");
        when(userService.findByUsername("alice")).thenReturn(user());
        ProfileDto dto = new ProfileDto();
        BindingResult br = new BeanPropertyBindingResult(dto, "profileDto");
        br.reject("err", "bad");
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        Model model = new ExtendedModelMap();

        assertThat(controller.updateProfile(dto, br, auth, ra, model)).isEqualTo("profile");
    }

    @Test
    void updateProfile_illegalArgument_returnsViewWithError() {
        when(auth.getName()).thenReturn("alice");
        when(userService.findByUsername("alice")).thenReturn(user());
        doThrow(new IllegalArgumentException("bad email"))
                .when(userService).updateProfile(eq("alice"), any());
        ProfileDto dto = new ProfileDto();
        dto.setEmail("x");
        BindingResult br = new BeanPropertyBindingResult(dto, "profileDto");
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        Model model = new ExtendedModelMap();

        assertThat(controller.updateProfile(dto, br, auth, ra, model)).isEqualTo("profile");
        assertThat(model.getAttribute("errorMessage")).isEqualTo("bad email");
    }

    @Test
    void changePassword_valid_redirects() {
        when(auth.getName()).thenReturn("alice");
        when(userService.findByUsername("alice")).thenReturn(user());
        ChangePasswordDto dto = new ChangePasswordDto();
        dto.setCurrentPassword("old");
        dto.setNewPassword("newpass1");
        dto.setConfirmPassword("newpass1");
        BindingResult br = new BeanPropertyBindingResult(dto, "changePasswordDto");
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        Model model = new ExtendedModelMap();

        assertThat(controller.changePassword(dto, br, auth, ra, model)).isEqualTo("redirect:/profile");
        assertThat(ra.getFlashAttributes().get("statusMessage")).isEqualTo("flash.profile.passwordchanged");
    }

    @Test
    void changePassword_mismatchedConfirm_returnsViewWithFieldError() {
        when(auth.getName()).thenReturn("alice");
        when(userService.findByUsername("alice")).thenReturn(user());
        ChangePasswordDto dto = new ChangePasswordDto();
        dto.setCurrentPassword("old");
        dto.setNewPassword("newpass1");
        dto.setConfirmPassword("different");
        BindingResult br = new BeanPropertyBindingResult(dto, "changePasswordDto");
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        Model model = new ExtendedModelMap();

        String view = controller.changePassword(dto, br, auth, ra, model);

        assertThat(view).isEqualTo("profile");
        assertThat(br.getFieldError("confirmPassword")).isNotNull();
    }

    @Test
    void changePassword_bindingErrors_returnsView() {
        when(auth.getName()).thenReturn("alice");
        when(userService.findByUsername("alice")).thenReturn(user());
        ChangePasswordDto dto = new ChangePasswordDto();
        dto.setCurrentPassword("old");
        dto.setNewPassword("newpass1");
        dto.setConfirmPassword("newpass1");
        BindingResult br = new BeanPropertyBindingResult(dto, "changePasswordDto");
        br.reject("err", "bad");
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        Model model = new ExtendedModelMap();

        assertThat(controller.changePassword(dto, br, auth, ra, model)).isEqualTo("profile");
    }

    @Test
    void changePassword_wrongCurrentPassword_returnsViewWithError() {
        when(auth.getName()).thenReturn("alice");
        when(userService.findByUsername("alice")).thenReturn(user());
        doThrow(new IllegalArgumentException("Current password is incorrect."))
                .when(userService).changePassword(eq("alice"), any());
        ChangePasswordDto dto = new ChangePasswordDto();
        dto.setCurrentPassword("wrong");
        dto.setNewPassword("newpass1");
        dto.setConfirmPassword("newpass1");
        BindingResult br = new BeanPropertyBindingResult(dto, "changePasswordDto");
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        Model model = new ExtendedModelMap();

        String view = controller.changePassword(dto, br, auth, ra, model);

        assertThat(view).isEqualTo("profile");
        assertThat(model.getAttribute("errorMessage")).isEqualTo("Current password is incorrect.");
    }

    @Test
    void deactivateSelf_bindingErrors_returnsView() {
        when(auth.getName()).thenReturn("alice");
        when(userService.findByUsername("alice")).thenReturn(user());
        DeactivateAccountDto dto = new DeactivateAccountDto();
        BindingResult br = new BeanPropertyBindingResult(dto, "deactivateAccountDto");
        br.reject("err", "bad");
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        Model model = new ExtendedModelMap();

        assertThat(controller.deactivateSelf(dto, br, auth, req, resp, model)).isEqualTo("profile");
    }

    @Test
    void deactivateSelf_success_logsOutAndRedirects() {
        when(auth.getName()).thenReturn("alice");
        when(userService.findByUsername("alice")).thenReturn(user());
        DeactivateAccountDto dto = new DeactivateAccountDto();
        dto.setPassword("correct");
        BindingResult br = new BeanPropertyBindingResult(dto, "deactivateAccountDto");
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        Model model = new ExtendedModelMap();

        String view = controller.deactivateSelf(dto, br, auth, req, resp, model);

        assertThat(view).isEqualTo("redirect:/login?deactivated");
    }

    @Test
    void deactivateSelf_wrongPassword_returnsViewWithError() {
        when(auth.getName()).thenReturn("alice");
        when(userService.findByUsername("alice")).thenReturn(user());
        doThrow(new IllegalArgumentException("Incorrect password."))
                .when(userService).deactivateSelf(eq("alice"), any());
        DeactivateAccountDto dto = new DeactivateAccountDto();
        dto.setPassword("wrong");
        BindingResult br = new BeanPropertyBindingResult(dto, "deactivateAccountDto");
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        Model model = new ExtendedModelMap();

        String view = controller.deactivateSelf(dto, br, auth, req, resp, model);

        assertThat(view).isEqualTo("profile");
        assertThat(model.getAttribute("errorMessage")).isEqualTo("Incorrect password.");
    }

    @Test
    void deactivateSelf_lastAdmin_returnsViewWithError() {
        when(auth.getName()).thenReturn("alice");
        when(userService.findByUsername("alice")).thenReturn(user());
        doThrow(new IllegalStateException("Cannot deactivate the last administrator."))
                .when(userService).deactivateSelf(eq("alice"), any());
        DeactivateAccountDto dto = new DeactivateAccountDto();
        dto.setPassword("correct");
        BindingResult br = new BeanPropertyBindingResult(dto, "deactivateAccountDto");
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        Model model = new ExtendedModelMap();

        String view = controller.deactivateSelf(dto, br, auth, req, resp, model);

        assertThat(view).isEqualTo("profile");
        assertThat(model.getAttribute("errorMessage")).isEqualTo("Cannot deactivate the last administrator.");
    }
}
