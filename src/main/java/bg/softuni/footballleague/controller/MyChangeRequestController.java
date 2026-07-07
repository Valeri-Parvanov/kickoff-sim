package bg.softuni.footballleague.controller;

import bg.softuni.footballleague.dto.ChangeRequestView;
import bg.softuni.footballleague.exception.ChangeRequestApprovalException;
import bg.softuni.footballleague.model.ChangeRequestStatus;
import bg.softuni.footballleague.service.ChangeRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/my-change-requests")
public class MyChangeRequestController {

    private static final int MAX_DISPLAY = 50;

    private final ChangeRequestService changeRequestService;

    @GetMapping
    public String myProposals(Authentication authentication, Model model,
                              @RequestParam(required = false, defaultValue = "ALL") String status,
                              @RequestParam(required = false, defaultValue = "ALL") String type) {
        List<ChangeRequestView> all = changeRequestService.findMine(authentication);

        long pendingCount = all.stream().filter(cr -> cr.getStatus() == ChangeRequestStatus.PENDING).count();
        long approvedCount = all.stream().filter(cr -> cr.getStatus() == ChangeRequestStatus.APPROVED).count();
        long rejectedCount = all.stream().filter(cr -> cr.getStatus() == ChangeRequestStatus.REJECTED).count();

        List<ChangeRequestView> filtered = all.stream()
                .filter(cr -> "ALL".equals(status) || cr.getStatus().name().equals(status))
                .filter(cr -> "ALL".equals(type) || cr.getEntityType().name().equals(type))
                .toList();

        boolean truncated = filtered.size() > MAX_DISPLAY;
        List<ChangeRequestView> displayed = truncated ? filtered.subList(0, MAX_DISPLAY) : filtered;

        model.addAttribute("changeRequests", displayed);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedType", type);
        model.addAttribute("totalCount", all.size());
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("approvedCount", approvedCount);
        model.addAttribute("rejectedCount", rejectedCount);
        model.addAttribute("truncated", truncated);
        model.addAttribute("filteredCount", filtered.size());
        return "my-change-requests";
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
