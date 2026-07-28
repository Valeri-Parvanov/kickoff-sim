package com.kickoffsim.controller;

import com.kickoffsim.dto.LeagueWizardForm;
import com.kickoffsim.dto.TeamDto;
import com.kickoffsim.model.LeagueFormat;
import com.kickoffsim.service.LeagueDraftService;
import com.kickoffsim.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/leagues/wizard/ai")
public class LeagueDraftController {

    private static final int MIN_SQUAD_SIZE = 6;

    private final TeamService teamService;
    private final LeagueDraftService leagueDraftService;

    @GetMapping
    public String generate(@RequestParam int format,
                           @RequestParam(required = false) List<UUID> existingTeamIds,
                           Model model, RedirectAttributes redirectAttributes) {
        List<UUID> selected = existingTeamIds != null ? existingTeamIds : List.of();
        Selection selection = resolve(format, selected, redirectAttributes);
        if (selection.redirect() != null) {
            return selection.redirect();
        }

        LeagueWizardForm wizardForm;
        try {
            wizardForm = leagueDraftService.draft(format, selection.eligible());
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "flash.leaguedraft.failed");
            return "redirect:" + blankWizardUrl(format, selected);
        }

        model.addAttribute("leagueWizardForm", wizardForm);
        model.addAttribute("existingTeams", selection.eligible());
        model.addAttribute("statusMessage", "flash.leaguedraft.generated");
        return "leagues/wizard-new-teams";
    }

    private Selection resolve(int format, List<UUID> selected, RedirectAttributes redirectAttributes) {
        if (LeagueFormat.forTeamCount(format).isEmpty()) {
            redirectAttributes.addFlashAttribute("warnMessage", "flash.league.invalidformat");
            return new Selection(List.of(), "redirect:/leagues/wizard");
        }
        if (selected.size() > format) {
            redirectAttributes.addFlashAttribute("warnMessage", "flash.league.toomanyteams");
            return new Selection(List.of(), "redirect:/leagues/wizard/teams?format=" + format);
        }

        List<TeamDto> eligible = selected.stream()
                .map(teamService::findById)
                .filter(t -> t.getLeagueId() == null && t.getPlayerCount() >= MIN_SQUAD_SIZE)
                .toList();
        if (eligible.size() != selected.size()) {
            redirectAttributes.addFlashAttribute("warnMessage", "flash.league.teamsineligible");
            return new Selection(List.of(), "redirect:/leagues/wizard/teams?format=" + format);
        }
        return new Selection(eligible, null);
    }

    private String blankWizardUrl(int format, List<UUID> selected) {
        StringBuilder url = new StringBuilder("/leagues/wizard/new-teams?format=").append(format);
        for (UUID id : selected) {
            url.append("&existingTeamIds=").append(id);
        }
        return url.toString();
    }

    private record Selection(List<TeamDto> eligible, String redirect) {
    }
}
