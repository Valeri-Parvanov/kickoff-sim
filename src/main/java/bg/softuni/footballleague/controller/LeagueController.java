package bg.softuni.footballleague.controller;

import bg.softuni.footballleague.dto.LeagueDto;
import bg.softuni.footballleague.service.LeagueService;
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
@RequestMapping("/leagues")
public class LeagueController {

    private static final Sort DEFAULT_SORT = Sort.by("name");
    private static final Map<String, String> SORTABLE_FIELDS = Map.of(
            "name", "name",
            "country", "country"
    );

    private final LeagueService leagueService;

    @GetMapping
    public String list(@RequestParam(required = false) String sort,
                        @RequestParam(required = false) String dir,
                        Model model) {
        Sort resolvedSort = SortSupport.resolve(sort, dir, SORTABLE_FIELDS, DEFAULT_SORT);
        model.addAttribute("leagues", leagueService.findAll(resolvedSort));
        model.addAttribute("currentSort", sort);
        model.addAttribute("currentDir", dir == null ? "asc" : dir);
        return "leagues/list";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("leagueDto", new LeagueDto());
        return "leagues/form";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/create")
    public String create(@Valid @ModelAttribute("leagueDto") LeagueDto leagueDto, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "leagues/form";
        }

        leagueService.create(leagueDto);
        return "redirect:/leagues";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        model.addAttribute("leagueDto", leagueService.findById(id));
        return "leagues/form";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, @Valid @ModelAttribute("leagueDto") LeagueDto leagueDto,
                        BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "leagues/form";
        }

        leagueService.update(id, leagueDto);
        return "redirect:/leagues";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id) {
        leagueService.delete(id);
        return "redirect:/leagues";
    }
}
