package bg.softuni.footballleague.controller;

import bg.softuni.footballleague.dto.PlayerRowDto;
import bg.softuni.footballleague.dto.TeamCreateForm;
import bg.softuni.footballleague.dto.TeamDto;
import bg.softuni.footballleague.dto.TeamSquadPayload;
import bg.softuni.footballleague.model.ChangeAction;
import bg.softuni.footballleague.model.EntityType;
import bg.softuni.footballleague.service.ChangeRequestService;
import bg.softuni.footballleague.service.LeagueService;
import bg.softuni.footballleague.service.TeamService;
import bg.softuni.footballleague.web.SortSupport;
import bg.softuni.footballleague.web.SquadRowValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/teams")
public class TeamController {

    private static final int MAX_SQUAD_SIZE = 12;
    private static final Sort DEFAULT_SORT = Sort.by("league.name").and(Sort.by("name"));
    private static final Map<String, String> SORTABLE_FIELDS = Map.of(
            "name", "name",
            "city", "city",
            "league", "league.name"
    );

    private final TeamService teamService;
    private final LeagueService leagueService;
    private final ChangeRequestService changeRequestService;

    @GetMapping
    public String list(@RequestParam(required = false) String sort,
                        @RequestParam(required = false) String dir,
                        Model model) {
        Sort resolvedSort = SortSupport.resolve(sort, dir, SORTABLE_FIELDS, DEFAULT_SORT);
        model.addAttribute("teams", teamService.findAll(resolvedSort));
        model.addAttribute("currentSort", sort);
        model.addAttribute("currentDir", dir == null ? "asc" : dir);
        return "teams/list";
    }

    @GetMapping("/form")
    public String createForm(@RequestParam(required = false) UUID fromRequest, Model model,
                              Authentication authentication) {
        TeamCreateForm form = new TeamCreateForm();
        if (fromRequest != null) {
            TeamDto payload = (TeamDto) changeRequestService.getPayloadForResubmit(fromRequest, authentication);
            form.setName(payload.getName());
            form.setCity(payload.getCity());
            form.setLeagueId(payload.getLeagueId());
        }
        prefillSquadRows(form);
        model.addAttribute("teamCreateForm", form);
        model.addAttribute("leagues", leagueService.findAll());
        return "teams/create";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("teamCreateForm") TeamCreateForm form, BindingResult bindingResult,
                          Model model, Authentication authentication, RedirectAttributes redirectAttributes) {
        List<Integer> filledRows =
                SquadRowValidator.validate(form.getPlayers(), "players", Collections.emptySet(), bindingResult);
        if (filledRows.size() > MAX_SQUAD_SIZE) {
            bindingResult.reject("squad.capacity", "A team can have at most " + MAX_SQUAD_SIZE + " players.");
        }
        if (form.getName() != null && !form.getName().isBlank() && teamService.existsByName(form.getName())) {
            bindingResult.rejectValue("name", "team.name.taken", "A team with this name already exists");
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("leagues", leagueService.findAll());
            return "teams/create";
        }

        TeamDto teamDto = new TeamDto();
        teamDto.setName(form.getName());
        teamDto.setCity(form.getCity());
        teamDto.setLeagueId(form.getLeagueId());

        boolean executed;
        if (filledRows.isEmpty()) {
            executed = changeRequestService.submitOrExecute(
                    EntityType.TEAM, ChangeAction.CREATE, teamDto, null, authentication);
        } else {
            TeamSquadPayload payload = new TeamSquadPayload();
            payload.setTeam(teamDto);
            payload.setPlayers(SquadRowValidator.toPlayers(form.getPlayers(), filledRows));
            executed = changeRequestService.submitOrExecute(
                    EntityType.TEAM_SQUAD, ChangeAction.CREATE, payload, null, authentication);
        }

        redirectAttributes.addFlashAttribute("statusMessage",
                executed ? "Team created." : "Submitted for admin approval.");
        return "redirect:/teams";
    }

    private void prefillSquadRows(TeamCreateForm form) {
        if (!form.getPlayers().isEmpty()) {
            return;
        }
        for (int number = 1; number <= MAX_SQUAD_SIZE; number++) {
            PlayerRowDto row = new PlayerRowDto();
            row.setShirtNumber(number);
            form.getPlayers().add(row);
        }
    }

    @GetMapping("/{id}/form")
    public String editForm(@PathVariable UUID id, @RequestParam(required = false) UUID fromRequest, Model model,
                            Authentication authentication) {
        TeamDto teamDto = fromRequest != null
                ? (TeamDto) changeRequestService.getPayloadForResubmit(fromRequest, authentication)
                : teamService.findById(id);
        teamDto.setId(id);
        model.addAttribute("teamDto", teamDto);
        model.addAttribute("leagues", leagueService.findAll());
        return "teams/form";
    }

    @PutMapping("/{id}")
    public String edit(@PathVariable UUID id, @Valid @ModelAttribute("teamDto") TeamDto teamDto,
                        BindingResult bindingResult, Model model, Authentication authentication,
                        RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("leagues", leagueService.findAll());
            return "teams/form";
        }

        boolean executed = changeRequestService.submitOrExecute(
                EntityType.TEAM, ChangeAction.UPDATE, teamDto, id, authentication);
        redirectAttributes.addFlashAttribute("statusMessage",
                executed ? "Team updated." : "Submitted for admin approval.");
        return "redirect:/teams";
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable UUID id, Authentication authentication,
                          RedirectAttributes redirectAttributes) {
        boolean executed = changeRequestService.submitOrExecute(
                EntityType.TEAM, ChangeAction.DELETE, null, id, authentication);
        redirectAttributes.addFlashAttribute("statusMessage",
                executed ? "Team deleted." : "Submitted for admin approval.");
        return "redirect:/teams";
    }
}
