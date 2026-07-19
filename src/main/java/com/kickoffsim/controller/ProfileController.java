package com.kickoffsim.controller;

import com.kickoffsim.dto.ProfileDto;
import com.kickoffsim.model.User;
import com.kickoffsim.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
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
            model.addAttribute("profileUsername", user.getUsername());
            model.addAttribute("userRole", user.getRole().name());
            return "profile";
        }

        try {
            userService.updateProfile(authentication.getName(), dto);
        } catch (IllegalArgumentException e) {
            model.addAttribute("profileUsername", user.getUsername());
            model.addAttribute("userRole", user.getRole().name());
            model.addAttribute("errorMessage", e.getMessage());
            return "profile";
        }

        redirectAttributes.addFlashAttribute("statusMessage", "Profile updated successfully.");
        return "redirect:/profile";
    }
}
