package com.kickoffsim.controller;

import com.kickoffsim.dto.ChangeRequestView;
import com.kickoffsim.exception.ChangeRequestApprovalException;
import com.kickoffsim.model.ChangeRequestStatus;
import com.kickoffsim.service.ChangeRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/my-change-requests")
public class MyChangeRequestController {

    private static final int PAGE_SIZE = 10;

    private final ChangeRequestService changeRequestService;

    @GetMapping
    public String myProposals(Authentication authentication, Model model,
                              @RequestParam(required = false, defaultValue = "ALL") String status,
                              @RequestParam(required = false, defaultValue = "ALL") String type,
                              @RequestParam(required = false, defaultValue = "0") int page) {
        List<ChangeRequestView> all = changeRequestService.findMine(authentication);

        long pendingCount = all.stream().filter(cr -> cr.getStatus() == ChangeRequestStatus.PENDING).count();
        long approvedCount = all.stream().filter(cr -> cr.getStatus() == ChangeRequestStatus.APPROVED).count();
        long rejectedCount = all.stream().filter(cr -> cr.getStatus() == ChangeRequestStatus.REJECTED).count();

        List<ChangeRequestView> filtered = all.stream()
                .filter(cr -> "ALL".equals(status) || cr.getStatus().name().equals(status))
                .filter(cr -> "ALL".equals(type) || cr.getEntityType().name().equals(type))
                .toList();

        int totalPages = filtered.isEmpty() ? 1 : (int) Math.ceil((double) filtered.size() / PAGE_SIZE);
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        int fromIdx = safePage * PAGE_SIZE;
        int toIdx = Math.min(fromIdx + PAGE_SIZE, filtered.size());
        List<ChangeRequestView> displayed = filtered.isEmpty() ? filtered : filtered.subList(fromIdx, toIdx);

        model.addAttribute("changeRequests", displayed);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedType", type);
        model.addAttribute("totalCount", all.size());
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("approvedCount", approvedCount);
        model.addAttribute("rejectedCount", rejectedCount);
        model.addAttribute("currentPage", safePage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("filteredCount", filtered.size());
        model.addAttribute("pageNumbers", buildPageNumbers(safePage, totalPages));
        return "my-change-requests";
    }

    private static List<Integer> buildPageNumbers(int current, int total) {
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

    @DeleteMapping("/{id}")
    public String cancel(@PathVariable UUID id, Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        try {
            changeRequestService.cancelMine(id, authentication);
            redirectAttributes.addFlashAttribute("statusMessage", "Request cancelled.");
        } catch (ChangeRequestApprovalException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/my-change-requests";
    }
}
