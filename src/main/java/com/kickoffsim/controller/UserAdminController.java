package com.kickoffsim.controller;

import com.kickoffsim.model.Role;
import com.kickoffsim.model.User;
import com.kickoffsim.service.UserService;
import com.kickoffsim.web.SortSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private static final int PAGE_SIZE = 20;

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "username", "username",
            "email", "email",
            "role", "role",
            "status", "enabled");

    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.desc("enabled"),
            Sort.Order.asc("role"),
            Sort.Order.asc("username"));

    private final UserService userService;

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(required = false) String sort,
                       @RequestParam(required = false) String dir,
                       Model model) {
        Sort resolved = SortSupport.resolve(sort, dir, SORT_FIELDS, DEFAULT_SORT);
        Page<User> userPage = userService.findAllPaged(page, PAGE_SIZE, resolved);
        long adminCount = userService.countByRole(Role.ADMIN);

        model.addAttribute("currentSort", sort);
        model.addAttribute("currentDir", "desc".equalsIgnoreCase(dir) ? "desc" : "asc");
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
            redirectAttributes.addFlashAttribute("statusMessage", "flash.user.roleupdated");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/status")
    public String setStatus(@PathVariable UUID id,
                            @RequestParam boolean enabled,
                            @RequestParam String targetUsername,
                            Authentication authentication,
                            HttpServletRequest request,
                            HttpServletResponse response,
                            RedirectAttributes redirectAttributes) {
        try {
            userService.setEnabled(id, enabled, authentication.getName());
            if (!enabled && authentication.getName().equals(targetUsername)) {
                new SecurityContextLogoutHandler().logout(request, response, authentication);
                return "redirect:/login";
            }
            redirectAttributes.addFlashAttribute("statusMessage",
                    enabled ? "flash.user.reactivated" : "flash.user.deactivated");
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
