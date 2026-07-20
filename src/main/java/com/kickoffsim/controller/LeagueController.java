package com.kickoffsim.controller;

import com.kickoffsim.client.NotificationClient;
import com.kickoffsim.dto.LeagueDetailView;
import com.kickoffsim.dto.LeagueDto;
import com.kickoffsim.dto.MatchDto;
import com.kickoffsim.dto.ScheduleForm;
import com.kickoffsim.exception.InvalidLeagueOperationException;
import com.kickoffsim.model.ChangeAction;
import com.kickoffsim.model.EntityType;
import com.kickoffsim.model.LeagueFormat;
import com.kickoffsim.service.ChangeRequestService;
import com.kickoffsim.service.LeagueService;
import com.kickoffsim.service.ScheduleService;
import com.kickoffsim.service.TeamService;
import com.kickoffsim.service.UserService;
import com.kickoffsim.web.LiveMatchJsSupport;
import com.kickoffsim.web.MatchFollowSupport;
import com.kickoffsim.web.ResubmitSupport;
import com.kickoffsim.web.ScheduleWindowSupport;
import com.kickoffsim.web.SortSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Stream;

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
    private final MatchFollowSupport matchFollowSupport;
    private final NotificationClient notificationClient;
    private final UserService userService;

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, @RequestParam(required = false) Integer round,
                         Authentication authentication, HttpServletRequest request, Model model) {
        var league = leagueService.findDetail(id);
        model.addAttribute("league", league);
        if (!model.containsAttribute("scheduleForm")) {
            ScheduleForm scheduleForm = new ScheduleForm();
            if (league.getScheduleStartDate() != null) scheduleForm.setStartDate(league.getScheduleStartDate());
            if (league.getScheduleStartTime() != null) scheduleForm.setStartTime(league.getScheduleStartTime());
            model.addAttribute("scheduleForm", scheduleForm);
        }

        model.addAttribute("liveMatchesForJs", List.of());
        model.addAttribute("elapsedByMatchId", Map.of());
        model.addAttribute("selectedRound", (Integer) null);

        if (!league.getMatches().isEmpty()) {
            LocalDateTime now = LocalDateTime.now();

            List<Integer> availableRounds = league.getMatches().stream()
                    .map(MatchDto::getRoundNumber)
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted(Comparator.reverseOrder())
                    .toList();

            LocalDate today = now.toLocalDate();
            OptionalInt todayRoundOpt = league.getMatches().stream()
                    .filter(m -> m.getRoundNumber() != null && m.getPlayedAt().toLocalDate().equals(today))
                    .mapToInt(MatchDto::getRoundNumber)
                    .findFirst();

            int lastActiveRound;
            if (todayRoundOpt.isPresent()) {
                lastActiveRound = todayRoundOpt.getAsInt();
            } else {
                lastActiveRound = league.getMatches().stream()
                        .filter(m -> m.getRoundNumber() != null && !m.getPlayedAt().isAfter(now))
                        .mapToInt(MatchDto::getRoundNumber)
                        .max()
                        .orElse(availableRounds.isEmpty() ? 1 : availableRounds.get(availableRounds.size() - 1));
            }

            int selectedRound = round != null ? round : lastActiveRound;

            List<MatchDto> roundMatches = league.getMatches().stream()
                    .filter(m -> Objects.equals(m.getRoundNumber(), selectedRound))
                    .toList();

            model.addAttribute("availableRounds", availableRounds);
            model.addAttribute("selectedRound", selectedRound);
            model.addAttribute("roundMatches", roundMatches);
            LocalDateTime liveThreshold = now.minusMinutes(46);
            model.addAttribute("now", now);
            model.addAttribute("liveThreshold", liveThreshold);

            Set<UUID> liveTeamIds = league.getMatches().stream()
                    .filter(m -> m.getPlayedAt().isBefore(now) && m.getPlayedAt().isAfter(liveThreshold))
                    .flatMap(m -> Stream.of(m.getHomeTeamId(), m.getAwayTeamId()))
                    .collect(java.util.stream.Collectors.toSet());
            model.addAttribute("liveTeamIds", liveTeamIds);

            List<MatchDto> liveLeagueMatches = league.getMatches().stream()
                    .filter(m -> m.getPlayedAt().isBefore(now) && m.getPlayedAt().isAfter(liveThreshold))
                    .toList();
            model.addAttribute("liveMatchesForJs", LiveMatchJsSupport.toJs(liveLeagueMatches, now));
            model.addAttribute("elapsedByMatchId", LiveMatchJsSupport.elapsedByMatchId(liveLeagueMatches, now));
            model.addAttribute("totalMatchCount", (long) league.getMatches().size());
            model.addAttribute("playedMatchCount", league.getMatches().stream()
                    .filter(m -> m.getPlayedAt().isBefore(liveThreshold))
                    .count());
        }

        model.addAttribute("subscribedMatchIds", matchFollowSupport.subscribedMatchIds(authentication));
        if (authentication != null) {
            try {
                UUID userId = userService.findByUsername(authentication.getName()).getId();
                notificationClient.getSubscriptions(userId).stream()
                        .filter(s -> "LEAGUE".equals(s.getEntityType()) && id.equals(s.getEntityId()))
                        .findFirst()
                        .ifPresent(s -> model.addAttribute("followedSubscriptionId", s.getId()));
            } catch (Exception ignored) {}
        }
        model.addAttribute("currentUrl", request.getQueryString() == null
                ? request.getRequestURI()
                : request.getRequestURI() + "?" + request.getQueryString());
        return "leagues/detail";
    }

    @GetMapping("/{id}/standings-summary")
    @ResponseBody
    public List<Map<String, Object>> standingsSummary(@PathVariable UUID id) {
        return leagueService.findDetail(id).getStandings().stream()
                .map(row -> Map.<String, Object>of(
                        "teamId", row.getTeamId().toString(),
                        "played", row.getPlayed(),
                        "wins", row.getWins(),
                        "draws", row.getDraws(),
                        "losses", row.getLosses(),
                        "goalsFor", row.getGoalsFor(),
                        "goalsAgainst", row.getGoalsAgainst(),
                        "goalDiff", row.getGoalDiff(),
                        "points", row.getPoints()))
                .toList();
    }

    @GetMapping("/{id}/live-summary")
    @ResponseBody
    public Map<String, Object> liveSummary(@PathVariable UUID id, @RequestParam(required = false) Integer round,
                                            Authentication authentication) {
        var league = leagueService.findDetail(id);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime liveThreshold = now.minusMinutes(46);

        List<MatchDto> live = league.getMatches().stream()
                .filter(m -> round == null || Objects.equals(m.getRoundNumber(), round))
                .filter(m -> m.getPlayedAt().isBefore(now) && m.getPlayedAt().isAfter(liveThreshold))
                .toList();

        Set<UUID> subscribedMatchIds = matchFollowSupport.subscribedMatchIds(authentication);

        List<Map<String, Object>> matches = live.stream()
                .map(m -> {
                    Map<String, Object> entry = new LinkedHashMap<>(LiveMatchJsSupport.toJsEntry(m, now));
                    entry.put("homeTeamName", m.getHomeTeamName());
                    entry.put("homeTeamCity", m.getHomeTeamCity());
                    entry.put("awayTeamName", m.getAwayTeamName());
                    entry.put("awayTeamCity", m.getAwayTeamCity());
                    entry.put("roundNumber", m.getRoundNumber());
                    entry.put("playedAtUtcIso", m.getPlayedAtUtcIso());
                    entry.put("followed", subscribedMatchIds.contains(m.getId()));
                    return entry;
                })
                .toList();

        return Map.of("matches", matches);
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
        Optional<String> windowError = ScheduleWindowSupport.checkLastMatchTooLate(
                scheduleDetail.getTeams().size(), form.getStartTime());
        if (windowError.isPresent()) {
            redirectAttributes.addFlashAttribute("scheduleError", windowError.get());
            return "redirect:/leagues/" + id;
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
        var freeTeams = teamService.findAllFree();
        long eligibleFreeCount = freeTeams.stream()
                .filter(t -> t.getPlayerCount() >= 6)
                .count();
        model.addAttribute("leagueEligibleCount", eligibleFreeCount);
        model.addAttribute("leagueFreeCount", freeTeams.size());
        return "leagues/list";
    }

    @GetMapping("/form")
    public String createForm(@RequestParam(required = false) UUID fromRequest, Model model,
                              Authentication authentication, RedirectAttributes redirectAttributes) {
        var freeTeams = teamService.findAllFree();
        long eligibleCount = freeTeams.stream().filter(t -> t.getPlayerCount() >= 6).count();
        if (fromRequest == null && eligibleCount < 6) {
            redirectAttributes.addFlashAttribute("warnMessage",
                    "A league requires at least 6 teams with 6+ players. " +
                    "You currently have " + eligibleCount + " eligible team(s). Create teams first.");
            return "redirect:/teams/form";
        }
        LeagueDto leagueDto = fromRequest != null
                ? (LeagueDto) changeRequestService.getPayloadForResubmit(fromRequest, authentication)
                : new LeagueDto();
        model.addAttribute("leagueDto", leagueDto);
        model.addAttribute("availableTeams", freeTeams);
        model.addAttribute("eligibleCount", eligibleCount);
        if (fromRequest != null) ResubmitSupport.addRejectionBanner(model, changeRequestService, fromRequest, authentication);
        return "leagues/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("leagueDto") LeagueDto leagueDto, BindingResult bindingResult,
                          @RequestParam(required = false) UUID fromRequest,
                          Authentication authentication, RedirectAttributes redirectAttributes, Model model) {
        if (leagueDto.getName() == null || leagueDto.getName().isBlank()) {
            bindingResult.rejectValue("name", "NotBlank", "League name is required.");
        }
        if (leagueDto.getTeamIds() == null || leagueDto.getTeamIds().isEmpty()) {
            bindingResult.reject("league.teams.required", "Please select at least one team.");
        } else if (LeagueFormat.forTeamCount(leagueDto.getTeamIds().size()).isEmpty()) {
            bindingResult.reject("league.teams.invalid.count",
                    "Please select exactly 6, 8, 10, or 16 teams (selected: " + leagueDto.getTeamIds().size() + ").");
        }
        if (leagueDto.getScheduleStartTime() != null
                && leagueDto.getTeamIds() != null && !leagueDto.getTeamIds().isEmpty()
                && LeagueFormat.forTeamCount(leagueDto.getTeamIds().size()).isPresent()) {
            ScheduleWindowSupport.checkLastMatchTooLate(leagueDto.getTeamIds().size(), leagueDto.getScheduleStartTime())
                    .ifPresent(message -> bindingResult.rejectValue("scheduleStartTime", "schedule.time.toolate", message));
        }
        if (bindingResult.hasErrors()) {
            var freeTeams = teamService.findAllFree();
            model.addAttribute("availableTeams", freeTeams);
            model.addAttribute("eligibleCount", freeTeams.stream().filter(t -> t.getPlayerCount() >= 6).count());
            if (fromRequest != null) model.addAttribute("fromRequest", fromRequest);
            return "leagues/form";
        }

        boolean executed;
        try {
            executed = changeRequestService.submitOrExecute(
                    EntityType.LEAGUE, ChangeAction.CREATE, leagueDto, null, authentication);
        } catch (DataIntegrityViolationException e) {
            bindingResult.rejectValue("name", "league.name.taken",
                    "A league named \"" + leagueDto.getName() + "\" already exists. Please choose a different name.");
            var freeTeams = teamService.findAllFree();
            model.addAttribute("availableTeams", freeTeams);
            model.addAttribute("eligibleCount", freeTeams.stream().filter(t -> t.getPlayerCount() >= 6).count());
            if (fromRequest != null) model.addAttribute("fromRequest", fromRequest);
            return "leagues/form";
        }
        if (fromRequest != null) changeRequestService.cancelIfPending(fromRequest, authentication);

        if (executed) {
            UUID newId = leagueService.findAll().stream()
                    .filter(l -> leagueDto.getName().equals(l.getName()))
                    .map(LeagueDto::getId)
                    .findFirst().orElse(null);
            if (newId != null) {
                LocalDate startDate = leagueDto.getScheduleStartDate() != null
                        ? leagueDto.getScheduleStartDate() : LocalDate.now();
                LocalTime startTime = leagueDto.getScheduleStartTime() != null
                        ? leagueDto.getScheduleStartTime() : LocalTime.of(11, 0);
                try {
                    scheduleService.generate(newId, startDate, startTime);
                    redirectAttributes.addFlashAttribute("statusMessage", "League created and schedule generated.");
                } catch (InvalidLeagueOperationException ex) {
                    redirectAttributes.addFlashAttribute("statusMessage", "League created. " + ex.getMessage());
                }
                return "redirect:/leagues/" + newId + "#schedule";
            }
        }

        redirectAttributes.addFlashAttribute("statusMessage",
                executed ? "League created." : "Submitted for admin approval.");
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
