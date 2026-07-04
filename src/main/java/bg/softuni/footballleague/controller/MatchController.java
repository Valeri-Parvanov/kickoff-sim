package bg.softuni.footballleague.controller;

import bg.softuni.footballleague.dto.GoalDto;
import bg.softuni.footballleague.dto.GoalEventDto;
import bg.softuni.footballleague.dto.MatchDto;
import bg.softuni.footballleague.dto.TeamDto;
import bg.softuni.footballleague.exception.InvalidGoalException;
import bg.softuni.footballleague.model.ChangeAction;
import bg.softuni.footballleague.model.EntityType;
import bg.softuni.footballleague.model.Half;
import bg.softuni.footballleague.service.ChangeRequestService;
import bg.softuni.footballleague.service.MatchService;
import bg.softuni.footballleague.service.PlayerService;
import bg.softuni.footballleague.service.TeamService;
import bg.softuni.footballleague.web.SortSupport;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final PlayerService playerService;
    private final ChangeRequestService changeRequestService;

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
    @GetMapping("/form")
    public String createForm(@RequestParam(required = false) UUID fromRequest, Model model,
                              Authentication authentication) {
        MatchDto matchDto = fromRequest != null
                ? (MatchDto) changeRequestService.getPayloadForResubmit(fromRequest, authentication)
                : new MatchDto();
        model.addAttribute("matchDto", matchDto);
        model.addAttribute("teams", teamService.findAll());
        return "matches/form";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public String create(@Valid @ModelAttribute("matchDto") MatchDto matchDto, BindingResult bindingResult,
                          Model model, Authentication authentication, RedirectAttributes redirectAttributes) {
        validateTeams(matchDto, bindingResult);
        if (bindingResult.hasErrors()) {
            model.addAttribute("teams", teamService.findAll());
            return "matches/form";
        }

        boolean executed = changeRequestService.submitOrExecute(
                EntityType.MATCH, ChangeAction.CREATE, matchDto, null, authentication);
        redirectAttributes.addFlashAttribute("statusMessage",
                executed ? "Match created." : "Submitted for admin approval.");
        return "redirect:/matches";
    }

    @GetMapping("/{id}/form")
    public String editForm(@PathVariable UUID id, @RequestParam(required = false) UUID fromRequest, Model model,
                            Authentication authentication) {
        MatchDto matchDto = fromRequest != null
                ? (MatchDto) changeRequestService.getPayloadForResubmit(fromRequest, authentication)
                : matchService.findById(id);
        matchDto.setId(id);
        model.addAttribute("matchDto", matchDto);
        model.addAttribute("teams", teamService.findAll());
        return "matches/form";
    }

    @PutMapping("/{id}")
    public String edit(@PathVariable UUID id, @Valid @ModelAttribute("matchDto") MatchDto matchDto,
                        BindingResult bindingResult, Model model, Authentication authentication,
                        RedirectAttributes redirectAttributes) {
        validateTeams(matchDto, bindingResult);
        if (bindingResult.hasErrors()) {
            model.addAttribute("teams", teamService.findAll());
            return "matches/form";
        }

        boolean executed = changeRequestService.submitOrExecute(
                EntityType.MATCH, ChangeAction.UPDATE, matchDto, id, authentication);
        redirectAttributes.addFlashAttribute("statusMessage",
                executed ? "Match updated." : "Submitted for admin approval.");
        return "redirect:/matches";
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable UUID id, Authentication authentication,
                          RedirectAttributes redirectAttributes) {
        boolean executed = changeRequestService.submitOrExecute(
                EntityType.MATCH, ChangeAction.DELETE, null, id, authentication);
        redirectAttributes.addFlashAttribute("statusMessage",
                executed ? "Match deleted." : "Submitted for admin approval.");
        return "redirect:/matches";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}/goals/form")
    public String addGoalForm(@PathVariable UUID id, Model model) {
        populateGoalFormModel(id, model);
        model.addAttribute("goalEventDto", new GoalEventDto());
        return "matches/goals/new";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/goals")
    public String addGoal(@PathVariable UUID id,
                          @Valid @ModelAttribute("goalEventDto") GoalEventDto goalEventDto,
                          BindingResult bindingResult, Model model,
                          RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populateGoalFormModel(id, model);
            return "matches/goals/new";
        }
        try {
            matchService.addGoal(id, goalEventDto);
            redirectAttributes.addFlashAttribute("statusMessage", "Goal recorded.");
            return "redirect:/matches";
        } catch (InvalidGoalException e) {
            populateGoalFormModel(id, model);
            model.addAttribute("errorMessage", e.getMessage());
            return "matches/goals/new";
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}/goals/{goalId}/form")
    public String editGoalForm(@PathVariable UUID id, @PathVariable UUID goalId, Model model) {
        GoalDto goal = matchService.findGoalById(goalId);

        GoalEventDto form = new GoalEventDto();
        form.setScorerId(goal.getScorerId());
        form.setAssistantId(goal.getAssistantId());
        if (goal.getMinute() != null && goal.getHalf() != null) {
            int fullMinute = Half.SECOND.equals(goal.getHalf()) ? goal.getMinute() + 20 : goal.getMinute();
            form.setMinute(fullMinute);
        }

        populateGoalFormModel(id, model);
        model.addAttribute("goalId", goalId);
        model.addAttribute("goalEventDto", form);
        return "matches/goals/edit";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/goals/{goalId}")
    public String editGoal(@PathVariable UUID id, @PathVariable UUID goalId,
                           @Valid @ModelAttribute("goalEventDto") GoalEventDto goalEventDto,
                           BindingResult bindingResult, Model model,
                           RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populateGoalFormModel(id, model);
            model.addAttribute("goalId", goalId);
            return "matches/goals/edit";
        }
        try {
            matchService.updateGoal(goalId, goalEventDto);
            redirectAttributes.addFlashAttribute("statusMessage", "Goal updated.");
            return "redirect:/matches";
        } catch (InvalidGoalException e) {
            populateGoalFormModel(id, model);
            model.addAttribute("goalId", goalId);
            model.addAttribute("errorMessage", e.getMessage());
            return "matches/goals/edit";
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}/goals/{goalId}")
    public String deleteGoal(@PathVariable UUID id, @PathVariable UUID goalId,
                             RedirectAttributes redirectAttributes) {
        matchService.deleteGoal(goalId);
        redirectAttributes.addFlashAttribute("statusMessage", "Goal removed.");
        return "redirect:/matches";
    }

    private void populateGoalFormModel(UUID matchId, Model model) {
        MatchDto match = matchService.findById(matchId);
        model.addAttribute("match", match);
        model.addAttribute("homePlayers", playerService.findAllByTeam(match.getHomeTeamId()));
        model.addAttribute("awayPlayers", playerService.findAllByTeam(match.getAwayTeamId()));
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
