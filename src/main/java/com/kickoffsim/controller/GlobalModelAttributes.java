package com.kickoffsim.controller;

import com.kickoffsim.service.ChangeRequestService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Arrays;
import java.util.stream.Collectors;

@ControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalModelAttributes {

    private final ChangeRequestService changeRequestService;

    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }

    @ModelAttribute("currentQuery")
    public String currentQuery(HttpServletRequest request) {
        String query = request.getQueryString();
        if (query == null || query.isBlank()) {
            return null;
        }
        String kept = Arrays.stream(query.split("&"))
                .filter(part -> !part.isBlank() && !part.startsWith("lang="))
                .collect(Collectors.joining("&"));
        return kept.isBlank() ? null : kept;
    }

    @ModelAttribute("pendingChangeCount")
    public Long pendingChangeCount(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (!admin) {
            return null;
        }
        try {
            return changeRequestService.countPending();
        } catch (Exception ex) {
            log.warn("Failed to load pending change count for topbar badge", ex);
            return null;
        }
    }

    @ModelAttribute("myPendingCount")
    public Long myPendingCount(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (admin) {
            return null;
        }
        try {
            return changeRequestService.countMyPending(authentication);
        } catch (Exception ex) {
            log.warn("Failed to load personal pending change count for topbar badge", ex);
            return null;
        }
    }
}
