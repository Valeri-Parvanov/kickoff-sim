package com.kickoffsim.controller;

import com.kickoffsim.dto.ChangePasswordDto;
import com.kickoffsim.dto.DeactivateAccountDto;
import com.kickoffsim.dto.ProfileDto;
import com.kickoffsim.model.User;
import com.kickoffsim.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;

    @GetMapping
    public String profile(Authentication authentication, Model model) {
        User user = userService.findByUsername(authentication.getName());
        ProfileDto dto = new ProfileDto();
        dto.setEmail(user.getEmail());
        model.addAttribute("profileDto", dto);
        model.addAttribute("deactivateAccountDto", new DeactivateAccountDto());
        model.addAttribute("changePasswordDto", new ChangePasswordDto());
        model.addAttribute("profileUsername", user.getUsername());
        model.addAttribute("userRole", user.getRole().name());
        return "profile";
    }

    @PostMapping
    public String updateProfile(@Valid @ModelAttribute("profileDto") ProfileDto dto,
                                BindingResult bindingResult,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        User user = userService.findByUsername(authentication.getName());
        if (bindingResult.hasErrors()) {
            model.addAttribute("deactivateAccountDto", new DeactivateAccountDto());
            model.addAttribute("changePasswordDto", new ChangePasswordDto());
            model.addAttribute("profileUsername", user.getUsername());
            model.addAttribute("userRole", user.getRole().name());
            return "profile";
        }

        try {
            userService.updateProfile(authentication.getName(), dto);
        } catch (IllegalArgumentException e) {
            model.addAttribute("deactivateAccountDto", new DeactivateAccountDto());
            model.addAttribute("changePasswordDto", new ChangePasswordDto());
            model.addAttribute("profileUsername", user.getUsername());
            model.addAttribute("userRole", user.getRole().name());
            model.addAttribute("errorMessage", e.getMessage());
            return "profile";
        }

        redirectAttributes.addFlashAttribute("statusMessage", "flash.profile.updated");
        return "redirect:/profile";
    }

    @PostMapping("/password")
    public String changePassword(@Valid @ModelAttribute("changePasswordDto") ChangePasswordDto dto,
                                 BindingResult bindingResult,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes,
                                 Model model) {
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch", "Passwords do not match");
        }

        User user = userService.findByUsername(authentication.getName());
        if (bindingResult.hasErrors()) {
            model.addAttribute("profileDto", toProfileDto(user));
            model.addAttribute("deactivateAccountDto", new DeactivateAccountDto());
            model.addAttribute("profileUsername", user.getUsername());
            model.addAttribute("userRole", user.getRole().name());
            return "profile";
        }

        try {
            userService.changePassword(authentication.getName(), dto);
        } catch (IllegalArgumentException e) {
            model.addAttribute("profileDto", toProfileDto(user));
            model.addAttribute("deactivateAccountDto", new DeactivateAccountDto());
            model.addAttribute("changePasswordDto", new ChangePasswordDto());
            model.addAttribute("profileUsername", user.getUsername());
            model.addAttribute("userRole", user.getRole().name());
            model.addAttribute("errorMessage", e.getMessage());
            return "profile";
        }

        redirectAttributes.addFlashAttribute("statusMessage", "flash.profile.passwordchanged");
        return "redirect:/profile";
    }

    @PostMapping("/deactivate")
    public String deactivateSelf(@Valid @ModelAttribute("deactivateAccountDto") DeactivateAccountDto dto,
                                 BindingResult bindingResult,
                                 Authentication authentication,
                                 HttpServletRequest request,
                                 HttpServletResponse response,
                                 Model model) {
        User user = userService.findByUsername(authentication.getName());
        if (bindingResult.hasErrors()) {
            model.addAttribute("profileDto", toProfileDto(user));
            model.addAttribute("changePasswordDto", new ChangePasswordDto());
            model.addAttribute("profileUsername", user.getUsername());
            model.addAttribute("userRole", user.getRole().name());
            return "profile";
        }

        try {
            userService.deactivateSelf(authentication.getName(), dto.getPassword());
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("profileDto", toProfileDto(user));
            model.addAttribute("deactivateAccountDto", new DeactivateAccountDto());
            model.addAttribute("changePasswordDto", new ChangePasswordDto());
            model.addAttribute("profileUsername", user.getUsername());
            model.addAttribute("userRole", user.getRole().name());
            model.addAttribute("errorMessage", e.getMessage());
            return "profile";
        }

        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return "redirect:/login?deactivated";
    }

    private static ProfileDto toProfileDto(User user) {
        ProfileDto dto = new ProfileDto();
        dto.setEmail(user.getEmail());
        return dto;
    }
}
