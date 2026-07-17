package bg.softuni.footballleague.controller;

import bg.softuni.footballleague.service.ChangeRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalModelAttributes {

    private final ChangeRequestService changeRequestService;

    @ModelAttribute("pendingChangeCount")
    public Long pendingChangeCount(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
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
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
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
