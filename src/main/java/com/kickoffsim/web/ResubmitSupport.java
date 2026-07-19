package com.kickoffsim.web;

import com.kickoffsim.service.ChangeRequestService;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;

import java.util.UUID;

public final class ResubmitSupport {

    private ResubmitSupport() {
    }

    public static void addRejectionBanner(Model model, ChangeRequestService changeRequestService,
                                           UUID fromRequest, Authentication authentication) {
        model.addAttribute("fromRequest", fromRequest);
        String reason = changeRequestService.getRejectionReason(fromRequest, authentication);
        if (reason != null && !reason.isBlank()) {
            model.addAttribute("errorMessage", "Rejected: " + reason);
        }
    }
}
