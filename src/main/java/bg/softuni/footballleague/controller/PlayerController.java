package bg.softuni.footballleague.controller;

import bg.softuni.footballleague.dto.PlayerDto;
import bg.softuni.footballleague.exception.DuplicateShirtNumberException;
import bg.softuni.footballleague.exception.SquadLimitExceededException;
import bg.softuni.footballleague.service.PlayerService;
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
@RequestMapping("/players")
public class PlayerController {

    private static final Sort DEFAULT_SORT = Sort.by("team.name").and(Sort.by("shirtNumber"));
    private static final Map<String, String> SORTABLE_FIELDS = Map.of(
            "firstName", "firstName",
            "lastName", "lastName",
            "shirtNumber", "shirtNumber",
            "team", "team.name"
    );

    private final PlayerService playerService;
    private final TeamService teamService;

    @GetMapping
    public String list(@RequestParam(required = false) String sort,
                        @RequestParam(required = false) String dir,
                        Model model) {
        Sort resolvedSort = SortSupport.resolve(sort, dir, SORTABLE_FIELDS, DEFAULT_SORT);
        model.addAttribute("players", playerService.findAll(resolvedSort));
        model.addAttribute("currentSort", sort);
        model.addAttribute("currentDir", dir == null ? "asc" : dir);
        return "players/list";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("playerDto", new PlayerDto());
        model.addAttribute("teams", teamService.findAll());
        return "players/form";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/create")
    public String create(@Valid @ModelAttribute("playerDto") PlayerDto playerDto, BindingResult bindingResult,
                          Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("teams", teamService.findAll());
            return "players/form";
        }

        try {
            playerService.create(playerDto);
        } catch (SquadLimitExceededException e) {
            bindingResult.rejectValue("teamId", "team.full", e.getMessage());
            model.addAttribute("teams", teamService.findAll());
            return "players/form";
        } catch (DuplicateShirtNumberException e) {
            bindingResult.rejectValue("shirtNumber", "shirtNumber.taken", e.getMessage());
            model.addAttribute("teams", teamService.findAll());
            return "players/form";
        }

        return "redirect:/players";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        model.addAttribute("playerDto", playerService.findById(id));
        model.addAttribute("teams", teamService.findAll());
        return "players/form";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, @Valid @ModelAttribute("playerDto") PlayerDto playerDto,
                        BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("teams", teamService.findAll());
            return "players/form";
        }

        try {
            playerService.update(id, playerDto);
        } catch (SquadLimitExceededException e) {
            bindingResult.rejectValue("teamId", "team.full", e.getMessage());
            model.addAttribute("teams", teamService.findAll());
            return "players/form";
        } catch (DuplicateShirtNumberException e) {
            bindingResult.rejectValue("shirtNumber", "shirtNumber.taken", e.getMessage());
            model.addAttribute("teams", teamService.findAll());
            return "players/form";
        }

        return "redirect:/players";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id) {
        playerService.delete(id);
        return "redirect:/players";
    }
}
