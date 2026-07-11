package bg.softuni.footballleague.controller;

import bg.softuni.footballleague.model.Role;
import bg.softuni.footballleague.model.User;
import bg.softuni.footballleague.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private static final int PAGE_SIZE = 20;

    private final UserService userService;

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        Page<User> userPage = userService.findAllPaged(page, PAGE_SIZE);
        long adminCount = userService.countByRole(Role.ADMIN);

        model.addAttribute("users", userPage.getContent());
        model.addAttribute("currentPage", userPage.getNumber());
        model.addAttribute("totalPages", userPage.getTotalPages());
        model.addAttribute("totalUsers", userPage.getTotalElements());
        model.addAttribute("adminCount", adminCount);
        model.addAttribute("pageNumbers", buildPageNumbers(userPage.getNumber(), userPage.getTotalPages()));
        return "admin/users";
    }

    @PostMapping("/{id}/role")
    public String changeRole(@PathVariable UUID id,
                             @RequestParam Role newRole,
                             @RequestParam String targetUsername,
                             Authentication authentication,
                             HttpServletRequest request,
                             HttpServletResponse response,
                             RedirectAttributes redirectAttributes) {
        try {
            userService.changeRole(id, newRole, authentication.getName());
            if (authentication.getName().equals(targetUsername) && newRole == Role.USER) {
                new SecurityContextLogoutHandler().logout(request, response, authentication);
                return "redirect:/login";
            }
            redirectAttributes.addFlashAttribute("statusMessage", "Role updated successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    static List<Integer> buildPageNumbers(int current, int total) {
        if (total <= 9) {
            List<Integer> pages = new ArrayList<>();
            for (int i = 0; i < total; i++) pages.add(i);
            return pages;
        }
        List<Integer> pages = new ArrayList<>();
        int start = Math.max(1, current - 2);
        int end = Math.min(total - 2, current + 2);
        pages.add(0);
        if (start > 1) pages.add(-1);
        for (int i = start; i <= end; i++) pages.add(i);
        if (end < total - 2) pages.add(-1);
        pages.add(total - 1);
        return pages;
    }
}
