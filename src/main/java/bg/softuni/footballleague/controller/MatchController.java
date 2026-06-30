package bg.softuni.footballleague.controller;

import bg.softuni.footballleague.dto.MatchDto;
import bg.softuni.footballleague.dto.TeamDto;
import bg.softuni.footballleague.service.MatchService;
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
@RequestMapping("/matches")
public class MatchController {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "playedAt");
    private static final Map<String, String> SORTABLE_FIELDS = Map.of(
            "homeTeam", "homeTeam.name",
            "awayTeam", "awayTeam.name",
            "homeScore", "homeScore",
            "awayScore", "awayScore",
            "playedAt", "playedAt"
    );

    private final MatchService matchService;
    private final TeamService teamService;

    @GetMapping
    public String list(@RequestParam(required = false) String sort,
                        @RequestParam(required = false) String dir,
                        Model model) {
        Sort resolvedSort = SortSupport.resolve(sort, dir, SORTABLE_FIELDS, DEFAULT_SORT);
        model.addAttribute("matches", matchService.findAll(resolvedSort));
        model.addAttribute("currentSort", sort);
        model.addAttribute("currentDir", dir == null ? "asc" : dir);
        return "matches/list";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("matchDto", new MatchDto());
        model.addAttribute("teams", teamService.findAll());
        return "matches/form";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/create")
    public String create(@Valid @ModelAttribute("matchDto") MatchDto matchDto, BindingResult bindingResult,
                          Model model) {
        validateTeams(matchDto, bindingResult);
        if (bindingResult.hasErrors()) {
            model.addAttribute("teams", teamService.findAll());
            return "matches/form";
        }

        matchService.create(matchDto);
        return "redirect:/matches";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        model.addAttribute("matchDto", matchService.findById(id));
        model.addAttribute("teams", teamService.findAll());
        return "matches/form";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, @Valid @ModelAttribute("matchDto") MatchDto matchDto,
                        BindingResult bindingResult, Model model) {
        validateTeams(matchDto, bindingResult);
        if (bindingResult.hasErrors()) {
            model.addAttribute("teams", teamService.findAll());
            return "matches/form";
        }

        matchService.update(id, matchDto);
        return "redirect:/matches";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id) {
        matchService.delete(id);
        return "redirect:/matches";
    }

    private void validateTeams(MatchDto matchDto, BindingResult bindingResult) {
        if (matchDto.getHomeTeamId() == null || matchDto.getAwayTeamId() == null) {
            return;
        }
        if (matchDto.getHomeTeamId().equals(matchDto.getAwayTeamId())) {
            bindingResult.rejectValue("awayTeamId", "team.same", "Away team must differ from home team");
            return;
        }

        TeamDto homeTeam = teamService.findById(matchDto.getHomeTeamId());
        TeamDto awayTeam = teamService.findById(matchDto.getAwayTeamId());
        if (!homeTeam.getLeagueId().equals(awayTeam.getLeagueId())) {
            bindingResult.rejectValue("awayTeamId", "team.differentLeague", "Both teams must be in the same league");
        }
    }
}
