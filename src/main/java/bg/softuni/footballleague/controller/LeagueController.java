package bg.softuni.footballleague.controller;

import bg.softuni.footballleague.dto.LeagueDto;
import bg.softuni.footballleague.dto.ScheduleForm;
import bg.softuni.footballleague.exception.InvalidLeagueOperationException;
import bg.softuni.footballleague.model.ChangeAction;
import bg.softuni.footballleague.model.EntityType;
import bg.softuni.footballleague.model.LeagueFormat;
import bg.softuni.footballleague.service.ChangeRequestService;
import bg.softuni.footballleague.service.LeagueService;
import bg.softuni.footballleague.service.ScheduleService;
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

import bg.softuni.footballleague.dto.LeagueDetailView;
import bg.softuni.footballleague.dto.MatchDto;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/leagues")
public class LeagueController {

    private static final Sort DEFAULT_SORT = Sort.by("name");
    private static final Map<String, String> SORTABLE_FIELDS = Map.of(
            "name", "name"
    );

    private final LeagueService leagueService;
    private final TeamService teamService;
    private final ChangeRequestService changeRequestService;
    private final ScheduleService scheduleService;

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, @RequestParam(required = false) Integer round, Model model) {
        var league = leagueService.findDetail(id);
        model.addAttribute("league", league);
        if (!model.containsAttribute("scheduleForm")) {
            ScheduleForm scheduleForm = new ScheduleForm();
            if (league.getScheduleStartDate() != null) scheduleForm.setStartDate(league.getScheduleStartDate());
            if (league.getScheduleStartTime() != null) scheduleForm.setStartTime(league.getScheduleStartTime());
            model.addAttribute("scheduleForm", scheduleForm);
        }

        if (!league.getMatches().isEmpty()) {
            LocalDateTime now = LocalDateTime.now();

            List<Integer> availableRounds = league.getMatches().stream()
                    .map(MatchDto::getRoundNumber)
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted(Comparator.reverseOrder())
                    .toList();

            int lastActiveRound = league.getMatches().stream()
                    .filter(m -> m.getRoundNumber() != null && !m.getPlayedAt().isAfter(now))
                    .mapToInt(MatchDto::getRoundNumber)
                    .max()
                    .orElse(availableRounds.isEmpty() ? 1 : availableRounds.get(availableRounds.size() - 1));

            int selectedRound = round != null ? round : lastActiveRound;

            List<MatchDto> roundMatches = league.getMatches().stream()
                    .filter(m -> Objects.equals(m.getRoundNumber(), selectedRound))
                    .toList();

            model.addAttribute("availableRounds", availableRounds);
            model.addAttribute("selectedRound", selectedRound);
            model.addAttribute("roundMatches", roundMatches);
            model.addAttribute("now", now);
            model.addAttribute("liveThreshold", now.minusMinutes(50));
        }

        return "leagues/detail";
    }

    @PostMapping("/{id}/schedule")
    @PreAuthorize("hasRole('ADMIN')")
    public String generateSchedule(@PathVariable UUID id,
                                    @Valid @ModelAttribute("scheduleForm") ScheduleForm form,
                                    BindingResult bindingResult,
                                    RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("scheduleError",
                    bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/leagues/" + id;
        }
        LeagueDetailView scheduleDetail = leagueService.findDetail(id);
        int matchesPerRound = scheduleDetail.getTeams().size() / 2;
        if (matchesPerRound > 1) {
            LocalTime lastStart = form.getStartTime().plusMinutes((matchesPerRound - 1) * 60L);
            if (lastStart.isAfter(LocalTime.of(23, 30))) {
                LocalTime maxStart = LocalTime.of(23, 30).minusMinutes((matchesPerRound - 1) * 60L);
                redirectAttributes.addFlashAttribute("scheduleError",
                        "With " + scheduleDetail.getTeams().size() + " teams, last match starts at " +
                        lastStart + ". Use " + maxStart + " or earlier, or move Round 1 to the next day.");
                return "redirect:/leagues/" + id;
            }
        }
        try {
            scheduleService.generate(id, form.getStartDate(), form.getStartTime());
            redirectAttributes.addFlashAttribute("statusMessage", "Schedule generated successfully.");
        } catch (InvalidLeagueOperationException ex) {
            redirectAttributes.addFlashAttribute("scheduleError", ex.getMessage());
        }
        return "redirect:/leagues/" + id;
    }

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
        model.addAttribute("availableTeams", teamService.findAllFree());
        if (fromRequest != null) model.addAttribute("fromRequest", fromRequest);
        return "leagues/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("leagueDto") LeagueDto leagueDto, BindingResult bindingResult,
                          @RequestParam(required = false) UUID fromRequest,
                          Authentication authentication, RedirectAttributes redirectAttributes, Model model) {
        if (leagueDto.getTeamIds() == null || leagueDto.getTeamIds().isEmpty()) {
            bindingResult.reject("league.teams.required", "Please select at least one team.");
        } else if (LeagueFormat.forTeamCount(leagueDto.getTeamIds().size()).isEmpty()) {
            bindingResult.reject("league.teams.invalid.count",
                    "Please select exactly 6, 8, 10, or 16 teams (selected: " + leagueDto.getTeamIds().size() + ").");
        }
        if (leagueDto.getScheduleStartTime() != null
                && leagueDto.getTeamIds() != null && !leagueDto.getTeamIds().isEmpty()
                && LeagueFormat.forTeamCount(leagueDto.getTeamIds().size()).isPresent()) {
            int matchesPerRound = leagueDto.getTeamIds().size() / 2;
            if (matchesPerRound > 1) {
                LocalTime lastStart = leagueDto.getScheduleStartTime().plusMinutes((matchesPerRound - 1) * 60L);
                if (lastStart.isAfter(LocalTime.of(23, 30))) {
                    LocalTime maxStart = LocalTime.of(23, 30).minusMinutes((matchesPerRound - 1) * 60L);
                    bindingResult.rejectValue("scheduleStartTime", "schedule.time.toolate",
                            "With " + leagueDto.getTeamIds().size() + " teams, last match starts at " +
                            lastStart + ". Use " + maxStart + " or earlier, or choose the next day.");
                }
            }
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("availableTeams", teamService.findAllFree());
            if (fromRequest != null) model.addAttribute("fromRequest", fromRequest);
            return "leagues/form";
        }

        boolean executed = changeRequestService.submitOrExecute(
                EntityType.LEAGUE, ChangeAction.CREATE, leagueDto, null, authentication);
        if (fromRequest != null) changeRequestService.cancelIfPending(fromRequest, authentication);
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
        if (fromRequest != null) model.addAttribute("fromRequest", fromRequest);
        return "leagues/form";
    }

    @PutMapping("/{id}")
    public String edit(@PathVariable UUID id, @Valid @ModelAttribute("leagueDto") LeagueDto leagueDto,
                        BindingResult bindingResult,
                        @RequestParam(required = false) UUID fromRequest,
                        Authentication authentication, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "leagues/form";
        }

        boolean executed = changeRequestService.submitOrExecute(
                EntityType.LEAGUE, ChangeAction.UPDATE, leagueDto, id, authentication);
        if (fromRequest != null) changeRequestService.cancelIfPending(fromRequest, authentication);
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
