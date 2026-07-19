package com.kickoffsim.controller;

import com.kickoffsim.dto.*;
import com.kickoffsim.model.ChangeAction;
import com.kickoffsim.model.EntityType;
import com.kickoffsim.service.ChangeRequestService;
import com.kickoffsim.service.PlayerService;
import com.kickoffsim.service.TeamService;
import com.kickoffsim.web.SquadRowValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@RequestMapping("/teams/{teamId}/squad")
public class SquadController {

    private static final int MAX_SQUAD_SIZE = 12;
    private static final int MIN_SQUAD_SIZE = 6;

    private final TeamService teamService;
    private final PlayerService playerService;
    private final ChangeRequestService changeRequestService;

    @GetMapping
    public String form(@PathVariable UUID teamId, Model model) {
        TeamDto team = teamService.findById(teamId);
        model.addAttribute("team", team);

        if (team.getLeagueId() == null) {
            model.addAttribute("editMode", true);
            model.addAttribute("squadForm", buildEditSquadForm(teamId));
            return "teams/squad";
        }

        model.addAttribute("editMode", false);
        int remaining = playerService.squadRemainingSlots(teamId);
        model.addAttribute("remaining", remaining);

        if (remaining > 0) {
            SquadForm squadForm = new SquadForm();
            List<Integer> freeNumbers = freeShirtNumbers(teamId, remaining);
            for (int i = 0; i < remaining; i++) {
                PlayerRowDto row = new PlayerRowDto();
                if (i < freeNumbers.size()) {
                    row.setShirtNumber(freeNumbers.get(i));
                }
                squadForm.getRows().add(row);
            }
            model.addAttribute("squadForm", squadForm);
        }
        return "teams/squad";
    }

    @PostMapping
    public String submit(@PathVariable UUID teamId,
                         @Valid @ModelAttribute("squadForm") SquadForm squadForm,
                         BindingResult bindingResult, Model model,
                         Authentication authentication, RedirectAttributes redirectAttributes) {
        TeamDto team = teamService.findById(teamId);

        if (team.getLeagueId() == null) {
            return submitEdit(teamId, team, squadForm, bindingResult, model, authentication, redirectAttributes);
        }

        int remaining = playerService.squadRemainingSlots(teamId);

        List<Integer> filledRows =
                SquadRowValidator.validate(squadForm.getRows(), "rows", takenShirtNumbers(teamId), bindingResult);

        if (filledRows.isEmpty()) {
            bindingResult.reject("squad.empty", "Fill in at least one player.");
        }
        if (filledRows.size() > remaining) {
            bindingResult.reject("squad.capacity",
                    "This team has room for only " + remaining + " more player(s).");
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("team", team);
            model.addAttribute("editMode", false);
            model.addAttribute("remaining", remaining);
            return "teams/squad";
        }

        TeamSquadPayload payload = new TeamSquadPayload();
        payload.setTeam(team);
        payload.setPlayers(SquadRowValidator.toPlayers(squadForm.getRows(), filledRows));

        boolean executed = changeRequestService.submitOrExecute(
                EntityType.TEAM_SQUAD, ChangeAction.CREATE, payload, null, authentication);

        redirectAttributes.addFlashAttribute("statusMessage", executed
                ? filledRows.size() + " player(s) added to " + team.getName() + "."
                : "Squad submitted for admin approval.");
        return "redirect:/teams";
    }

    private String submitEdit(UUID teamId, TeamDto team, SquadForm squadForm, BindingResult bindingResult,
                               Model model, Authentication authentication, RedirectAttributes redirectAttributes) {
        List<Integer> filledRows =
                SquadRowValidator.validate(squadForm.getRows(), "rows", Collections.emptySet(), bindingResult);

        if (filledRows.size() < MIN_SQUAD_SIZE) {
            bindingResult.reject("squad.minimum", "A team must have at least " + MIN_SQUAD_SIZE + " players.");
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("team", team);
            model.addAttribute("editMode", true);
            return "teams/squad";
        }

        TeamSquadPayload payload = new TeamSquadPayload();
        payload.setTeam(team);
        payload.setPlayers(SquadRowValidator.toPlayers(squadForm.getRows(), filledRows));

        boolean executed = changeRequestService.submitOrExecute(
                EntityType.TEAM_SQUAD, ChangeAction.UPDATE, payload, teamId, authentication);

        redirectAttributes.addFlashAttribute("statusMessage", executed
                ? "Squad updated."
                : "Squad update submitted for admin approval.");
        return "redirect:/teams";
    }

    private SquadForm buildEditSquadForm(UUID teamId) {
        SquadForm squadForm = new SquadForm();
        Set<Integer> taken = new HashSet<>();
        for (PlayerDto p : playerService.findAllByTeam(teamId)) {
            PlayerRowDto row = new PlayerRowDto();
            row.setId(p.getId());
            row.setShirtNumber(p.getShirtNumber());
            row.setFirstName(p.getFirstName());
            row.setLastName(p.getLastName());
            squadForm.getRows().add(row);
            taken.add(p.getShirtNumber());
        }
        int next = 1;
        while (squadForm.getRows().size() < MAX_SQUAD_SIZE) {
            while (taken.contains(next)) next++;
            PlayerRowDto row = new PlayerRowDto();
            row.setShirtNumber(next);
            taken.add(next++);
            squadForm.getRows().add(row);
        }
        return squadForm;
    }

    private List<Integer> freeShirtNumbers(UUID teamId, int count) {
        Set<Integer> taken = takenShirtNumbers(teamId);

        List<Integer> free = new ArrayList<>();
        int number = 1;
        while (free.size() < count && number <= 99) {
            if (!taken.contains(number)) {
                free.add(number);
            }
            number++;
        }
        return free;
    }

    private Set<Integer> takenShirtNumbers(UUID teamId) {
        return playerService.findAllByTeam(teamId).stream()
                .map(PlayerDto::getShirtNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
