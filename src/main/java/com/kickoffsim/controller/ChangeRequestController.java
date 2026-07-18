package bg.softuni.footballleague.controller;

import bg.softuni.footballleague.exception.ChangeRequestApprovalException;
import bg.softuni.footballleague.service.ChangeRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/change-requests")
@PreAuthorize("hasRole('ADMIN')")
public class ChangeRequestController {

    private final ChangeRequestService changeRequestService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("changeRequests", changeRequestService.findPending());
        return "admin/change-requests";
    }

    @PostMapping("/{id}/approval")
    public String approve(@PathVariable UUID id, Authentication authentication,
                           RedirectAttributes redirectAttributes) {
        try {
            changeRequestService.approve(id, authentication);
            redirectAttributes.addFlashAttribute("statusMessage", "Change request approved.");
        } catch (ChangeRequestApprovalException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Could not approve change request: %s".formatted(e.getMessage()));
            redirectAttributes.addFlashAttribute("failedRequestId", id);
            redirectAttributes.addFlashAttribute("suggestedReason", e.getMessage());
        } catch (DataIntegrityViolationException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "This request cannot be approved — a record with that name already exists. Please reject it instead.");
            redirectAttributes.addFlashAttribute("failedRequestId", id);
            redirectAttributes.addFlashAttribute("suggestedReason", "A record with that name already exists");
        }
        return "redirect:/admin/change-requests";
    }

    @PostMapping("/{id}/rejection")
    public String reject(@PathVariable UUID id, @RequestParam(required = false) String reason,
                          Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            changeRequestService.reject(id, authentication, reason);
            redirectAttributes.addFlashAttribute("statusMessage", "Change request rejected.");
        } catch (ChangeRequestApprovalException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/change-requests";
    }
}
