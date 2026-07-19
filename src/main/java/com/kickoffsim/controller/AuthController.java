package com.kickoffsim.controller;

import com.kickoffsim.dto.RegisterDto;
import com.kickoffsim.exception.UsernameAlreadyExistsException;
import com.kickoffsim.model.Role;
import com.kickoffsim.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
                            BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        if (!registerDto.getPassword().equals(registerDto.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch", "Passwords do not match");
        }

        if (bindingResult.hasErrors()) {
            return "register";
        }

        Role assignedRole;
        try {
            assignedRole = userService.register(registerDto);
        } catch (UsernameAlreadyExistsException e) {
            bindingResult.rejectValue("username", "username.taken", e.getMessage());
            return "register";
        }

        if (assignedRole == Role.ADMIN) {
            redirectAttributes.addFlashAttribute("statusMessage",
                    "Account created! You're the first registered user, so you've been given admin access.");
        }

        return "redirect:/login";
    }
}
