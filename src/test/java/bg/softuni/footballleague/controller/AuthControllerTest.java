package bg.softuni.footballleague.controller;

import bg.softuni.footballleague.dto.RegisterDto;
import bg.softuni.footballleague.exception.UsernameAlreadyExistsException;
import bg.softuni.footballleague.model.Role;
import bg.softuni.footballleague.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthControllerTest {

    @Mock private UserService userService;

    @InjectMocks private AuthController controller;

    private RegisterDto register(String pass, String confirm) {
        RegisterDto dto = new RegisterDto();
        dto.setUsername("alice");
        dto.setPassword(pass);
        dto.setConfirmPassword(confirm);
        return dto;
    }

    @Test
    void login_returnsView() {
        assertThat(controller.login()).isEqualTo("login");
    }

    @Test
    void registerForm_returnsView() {
        Model model = new ExtendedModelMap();
        assertThat(controller.registerForm(model)).isEqualTo("register");
        assertThat(model.getAttribute("registerDto")).isNotNull();
    }

    @Test
    void register_passwordMismatch_returnsView() {
        RegisterDto dto = register("a", "b");
        BindingResult br = new BeanPropertyBindingResult(dto, "registerDto");
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.register(dto, br, ra)).isEqualTo("register");
        assertThat(br.hasErrors()).isTrue();
    }

    @Test
    void register_valid_admin_redirectsToLogin() {
        RegisterDto dto = register("pass", "pass");
        BindingResult br = new BeanPropertyBindingResult(dto, "registerDto");
        when(userService.register(any())).thenReturn(Role.ADMIN);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.register(dto, br, ra)).isEqualTo("redirect:/login");
        assertThat(ra.getFlashAttributes()).containsKey("statusMessage");
    }

    @Test
    void register_usernameTaken_returnsView() {
        RegisterDto dto = register("pass", "pass");
        BindingResult br = new BeanPropertyBindingResult(dto, "registerDto");
        when(userService.register(any())).thenThrow(new UsernameAlreadyExistsException("taken"));
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.register(dto, br, ra)).isEqualTo("register");
        assertThat(br.hasErrors()).isTrue();
    }
}
