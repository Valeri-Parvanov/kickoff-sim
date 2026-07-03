package bg.softuni.footballleague.controller;

import bg.softuni.footballleague.dto.LeagueDto;
import bg.softuni.footballleague.model.ChangeAction;
import bg.softuni.footballleague.model.EntityType;
import bg.softuni.footballleague.service.ChangeRequestService;
import bg.softuni.footballleague.service.LeagueService;
import bg.softuni.footballleague.web.SortSupport;
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
    private final ChangeRequestService changeRequestService;

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

    @GetMapping("/form")
    public String createForm(@RequestParam(required = false) UUID fromRequest, Model model,
                              Authentication authentication) {
        LeagueDto leagueDto = fromRequest != null
                ? (LeagueDto) changeRequestService.getPayloadForResubmit(fromRequest, authentication)
                : new LeagueDto();
        model.addAttribute("leagueDto", leagueDto);
        return "leagues/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("leagueDto") LeagueDto leagueDto, BindingResult bindingResult,
                          Authentication authentication, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "leagues/form";
        }

        boolean executed = changeRequestService.submitOrExecute(
                EntityType.LEAGUE, ChangeAction.CREATE, leagueDto, null, authentication);
        redirectAttributes.addFlashAttribute("statusMessage",
                executed ? "League created." : "Submitted for admin approval.");
        return "redirect:/leagues";
    }

    @GetMapping("/{id}/form")
    public String editForm(@PathVariable UUID id, @RequestParam(required = false) UUID fromRequest, Model model,
                            Authentication authentication) {
        LeagueDto leagueDto = fromRequest != null
                ? (LeagueDto) changeRequestService.getPayloadForResubmit(fromRequest, authentication)
                : leagueService.findById(id);
        leagueDto.setId(id);
        model.addAttribute("leagueDto", leagueDto);
        return "leagues/form";
    }

    @PutMapping("/{id}")
    public String edit(@PathVariable UUID id, @Valid @ModelAttribute("leagueDto") LeagueDto leagueDto,
                        BindingResult bindingResult, Authentication authentication,
                        RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "leagues/form";
        }

        boolean executed = changeRequestService.submitOrExecute(
                EntityType.LEAGUE, ChangeAction.UPDATE, leagueDto, id, authentication);
        redirectAttributes.addFlashAttribute("statusMessage",
                executed ? "League updated." : "Submitted for admin approval.");
        return "redirect:/leagues";
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable UUID id, Authentication authentication,
                          RedirectAttributes redirectAttributes) {
        boolean executed = changeRequestService.submitOrExecute(
                EntityType.LEAGUE, ChangeAction.DELETE, null, id, authentication);
        redirectAttributes.addFlashAttribute("statusMessage",
                executed ? "League deleted." : "Submitted for admin approval.");
        return "redirect:/leagues";
    }
}
