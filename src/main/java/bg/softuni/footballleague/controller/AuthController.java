package bg.softuni.footballleague.controller;

import bg.softuni.footballleague.dto.RegisterDto;
import bg.softuni.footballleague.exception.UsernameAlreadyExistsException;
import bg.softuni.footballleague.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("registerDto", new RegisterDto());
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registerDto") RegisterDto registerDto,
                            BindingResult bindingResult) {
        if (!registerDto.getPassword().equals(registerDto.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch", "Passwords do not match");
        }

        if (bindingResult.hasErrors()) {
            return "register";
        }

        try {
            userService.register(registerDto);
        } catch (UsernameAlreadyExistsException e) {
            bindingResult.rejectValue("username", "username.taken", e.getMessage());
            return "register";
        }

        return "redirect:/login";
    }
}
