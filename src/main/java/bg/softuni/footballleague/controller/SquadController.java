package bg.softuni.footballleague.controller;

import bg.softuni.footballleague.dto.PlayerDto;
import bg.softuni.footballleague.dto.PlayerRowDto;
import bg.softuni.footballleague.dto.SquadForm;
import bg.softuni.footballleague.dto.TeamDto;
import bg.softuni.footballleague.dto.TeamSquadPayload;
import bg.softuni.footballleague.model.ChangeAction;
import bg.softuni.footballleague.model.EntityType;
import bg.softuni.footballleague.service.ChangeRequestService;
import bg.softuni.footballleague.service.PlayerService;
import bg.softuni.footballleague.service.TeamService;
import bg.softuni.footballleague.web.SquadRowValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@RequestMapping("/teams/{teamId}/squad")
public class SquadController {

    private final TeamService teamService;
    private final PlayerService playerService;
    private final ChangeRequestService changeRequestService;

    @GetMapping
    public String form(@PathVariable UUID teamId, Model model) {
        TeamDto team = teamService.findById(teamId);
        int remaining = playerService.squadRemainingSlots(teamId);
        model.addAttribute("team", team);
        model.addAttribute("remaining", remaining);

        if (remaining > 0) {
            SquadForm squadForm = new SquadForm();
            List<Integer> freeNumbers = freeShirtNumbers(teamId, remaining);
            for (int i = 0; i < remaining; i++) {
                PlayerRowDto row = new PlayerRowDto();
                row.setShirtNumber(freeNumbers.get(i));
                squadForm.getRows().add(row);
            }
            model.addAttribute("squadForm", squadForm);
        }
        return "teams/squad";
    }

    @PostMapping
    public String submit(@PathVariable UUID teamId,
                         @ModelAttribute("squadForm") SquadForm squadForm,
                         BindingResult bindingResult, Model model,
                         Authentication authentication, RedirectAttributes redirectAttributes) {
        TeamDto team = teamService.findById(teamId);
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
