package bg.softuni.footballleague.controller;

import bg.softuni.footballleague.dto.PlayerDto;
import bg.softuni.footballleague.exception.DuplicateShirtNumberException;
import bg.softuni.footballleague.exception.SquadLimitExceededException;
import bg.softuni.footballleague.model.ChangeAction;
import bg.softuni.footballleague.model.EntityType;
import bg.softuni.footballleague.service.ChangeRequestService;
import bg.softuni.footballleague.service.PlayerService;
import bg.softuni.footballleague.service.TeamService;
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
    private final ChangeRequestService changeRequestService;

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

    @GetMapping("/form")
    public String createForm(@RequestParam(required = false) UUID fromRequest,
                              @RequestParam(required = false) UUID teamId,
                              Model model, Authentication authentication) {
        PlayerDto playerDto = fromRequest != null
                ? (PlayerDto) changeRequestService.getPayloadForResubmit(fromRequest, authentication)
                : new PlayerDto();
        if (teamId != null && playerDto.getTeamId() == null) {
            playerDto.setTeamId(teamId);
        }
        model.addAttribute("playerDto", playerDto);
        model.addAttribute("teams", teamService.findAll());
        if (fromRequest != null) model.addAttribute("fromRequest", fromRequest);
        return "players/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("playerDto") PlayerDto playerDto, BindingResult bindingResult,
                          @RequestParam(required = false) UUID fromRequest,
                          Model model, Authentication authentication, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("teams", teamService.findAll());
            if (fromRequest != null) model.addAttribute("fromRequest", fromRequest);
            return "players/form";
        }

        boolean executed;
        try {
            executed = changeRequestService.submitOrExecute(
                    EntityType.PLAYER, ChangeAction.CREATE, playerDto, null, authentication);
        } catch (SquadLimitExceededException e) {
            bindingResult.rejectValue("teamId", "team.full", e.getMessage());
            model.addAttribute("teams", teamService.findAll());
            if (fromRequest != null) model.addAttribute("fromRequest", fromRequest);
            return "players/form";
        } catch (DuplicateShirtNumberException e) {
            bindingResult.rejectValue("shirtNumber", "shirtNumber.taken", e.getMessage());
            model.addAttribute("teams", teamService.findAll());
            if (fromRequest != null) model.addAttribute("fromRequest", fromRequest);
            return "players/form";
        }

        if (fromRequest != null) changeRequestService.cancelIfPending(fromRequest, authentication);
        redirectAttributes.addFlashAttribute("statusMessage",
                executed ? "Player created." : "Submitted for admin approval.");
        return "redirect:/teams/" + playerDto.getTeamId();
    }

    @GetMapping("/{id}/form")
    public String editForm(@PathVariable UUID id, @RequestParam(required = false) UUID fromRequest, Model model,
                            Authentication authentication) {
        PlayerDto playerDto = fromRequest != null
                ? (PlayerDto) changeRequestService.getPayloadForResubmit(fromRequest, authentication)
                : playerService.findById(id);
        playerDto.setId(id);
        model.addAttribute("playerDto", playerDto);
        model.addAttribute("teams", teamService.findAll());
        if (fromRequest != null) model.addAttribute("fromRequest", fromRequest);
        return "players/form";
    }

    @PutMapping("/{id}")
    public String edit(@PathVariable UUID id, @Valid @ModelAttribute("playerDto") PlayerDto playerDto,
                        BindingResult bindingResult,
                        @RequestParam(required = false) UUID fromRequest,
                        Model model, Authentication authentication,
                        RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("teams", teamService.findAll());
            if (fromRequest != null) model.addAttribute("fromRequest", fromRequest);
            return "players/form";
        }

        boolean executed;
        try {
            executed = changeRequestService.submitOrExecute(
                    EntityType.PLAYER, ChangeAction.UPDATE, playerDto, id, authentication);
        } catch (SquadLimitExceededException e) {
            bindingResult.rejectValue("teamId", "team.full", e.getMessage());
            model.addAttribute("teams", teamService.findAll());
            if (fromRequest != null) model.addAttribute("fromRequest", fromRequest);
            return "players/form";
        } catch (DuplicateShirtNumberException e) {
            bindingResult.rejectValue("shirtNumber", "shirtNumber.taken", e.getMessage());
            model.addAttribute("teams", teamService.findAll());
            if (fromRequest != null) model.addAttribute("fromRequest", fromRequest);
            return "players/form";
        }

        if (fromRequest != null) changeRequestService.cancelIfPending(fromRequest, authentication);
        UUID redirectTeamId = executed ? playerDto.getTeamId() : playerService.findById(id).getTeamId();
        redirectAttributes.addFlashAttribute("statusMessage",
                executed ? "Player updated." : "Submitted for admin approval.");
        return "redirect:/teams/" + redirectTeamId;
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable UUID id,
                          @RequestParam(required = false) UUID returnTeam,
                          Authentication authentication,
                          RedirectAttributes redirectAttributes) {
        boolean executed = changeRequestService.submitOrExecute(
                EntityType.PLAYER, ChangeAction.DELETE, null, id, authentication);
        redirectAttributes.addFlashAttribute("statusMessage",
                executed ? "Player deleted." : "Submitted for admin approval.");
        return returnTeam != null ? "redirect:/teams/" + returnTeam : "redirect:/teams";
    }
}
