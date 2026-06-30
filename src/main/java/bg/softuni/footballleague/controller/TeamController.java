package bg.softuni.footballleague.controller;

import bg.softuni.footballleague.dto.TeamDto;
import bg.softuni.footballleague.service.LeagueService;
import bg.softuni.footballleague.service.TeamService;
import bg.softuni.footballleague.web.SortSupport;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/teams")
public class TeamController {

    private static final Sort DEFAULT_SORT = Sort.by("league.name").and(Sort.by("name"));
    private static final Map<String, String> SORTABLE_FIELDS = Map.of(
            "name", "name",
            "city", "city",
            "league", "league.name"
    );

    private final TeamService teamService;
    private final LeagueService leagueService;

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

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("teamDto", new TeamDto());
        model.addAttribute("leagues", leagueService.findAll());
        return "teams/form";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/create")
    public String create(@Valid @ModelAttribute("teamDto") TeamDto teamDto, BindingResult bindingResult,
                          Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("leagues", leagueService.findAll());
            return "teams/form";
        }

        teamService.create(teamDto);
        return "redirect:/teams";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        model.addAttribute("teamDto", teamService.findById(id));
        model.addAttribute("leagues", leagueService.findAll());
        return "teams/form";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, @Valid @ModelAttribute("teamDto") TeamDto teamDto,
                        BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("leagues", leagueService.findAll());
            return "teams/form";
        }

        teamService.update(id, teamDto);
        return "redirect:/teams";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id) {
        teamService.delete(id);
        return "redirect:/teams";
    }
}
