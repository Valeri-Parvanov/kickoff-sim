package bg.softuni.footballleague.controller;

import bg.softuni.footballleague.dto.ProfileDto;
import bg.softuni.footballleague.model.Role;
import bg.softuni.footballleague.model.User;
import bg.softuni.footballleague.service.UserService;
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
}
