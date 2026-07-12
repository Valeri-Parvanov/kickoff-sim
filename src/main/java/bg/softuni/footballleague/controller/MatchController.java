package bg.softuni.footballleague.controller;

import bg.softuni.footballleague.dto.*;
import bg.softuni.footballleague.exception.InvalidGoalException;
import bg.softuni.footballleague.model.ChangeAction;
import bg.softuni.footballleague.model.EntityType;
import bg.softuni.footballleague.model.Half;
import bg.softuni.footballleague.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@RequestMapping("/matches")
public class MatchController {

    private final MatchService matchService;
    private final TeamService teamService;
    private final PlayerService playerService;
    private final LeagueService leagueService;
    private final ChangeRequestService changeRequestService;

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        MatchDto match = matchService.findById(id);
        model.addAttribute("match", match);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime liveThreshold = now.minusMinutes(46);
        model.addAttribute("now", now);
        model.addAttribute("liveThreshold", liveThreshold);
        boolean isLive = match.getPlayedAt().isBefore(now) && match.getPlayedAt().isAfter(liveThreshold);
        if (isLive) {
            List<Map<String, Object>> goals = match.getGoalTimeline().stream()
                    .map(g -> Map.<String, Object>of(
                            "minute", g.getMinute() != null ? g.getMinute() : 0,
                            "half", g.getHalf() != null ? g.getHalf().name() : "FIRST",
                            "homeGoal", g.isHomeGoal(),
                            "rh", g.getRunningHomeScore() != null ? g.getRunningHomeScore() : 0,
                            "ra", g.getRunningAwayScore() != null ? g.getRunningAwayScore() : 0))
                    .toList();
            model.addAttribute("liveMatchForJs", Map.of(
                    "id", match.getId().toString(),
                    "elapsedMin", Duration.between(match.getPlayedAt(), now).toMinutes(),
                    "goals", goals));
        }
        return "matches/detail";
    }

    @GetMapping
    public String list(@RequestParam(required = false) UUID league,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                       @RequestParam(required = false) UUID team,
                       Model model) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        LocalDateTime liveThreshold = now.minusMinutes(46);

        List<LocalDate> matchDates;
        List<MatchDto> dateMatches = null;
        List<MatchDto> recentMatches = List.of();
        List<MatchDto> upcomingMatches = List.of();
        LocalDate selectedDate = date;

        List<MatchDto> allFiltered;
        if (league == null && team == null) {
            matchDates = matchService.findAllMatchDates();
            allFiltered = date != null ? List.of() : matchService.findAll(Sort.by(Sort.Direction.ASC, "playedAt"));
        } else {
            allFiltered = league != null
                    ? matchService.findByLeague(league)
                    : matchService.findAll(Sort.by(Sort.Direction.ASC, "playedAt"));
            if (team != null) {
                final UUID t = team;
                allFiltered = allFiltered.stream()
                        .filter(m -> t.equals(m.getHomeTeamId()) || t.equals(m.getAwayTeamId()))
                        .toList();
            }
            matchDates = allFiltered.stream()
                    .map(m -> m.getPlayedAt().toLocalDate())
                    .distinct()
                    .sorted()
                    .toList();
        }

        if (date != null) {
            if (league == null && team == null) {
                dateMatches = sortByStatus(matchService.findByDate(date), now, liveThreshold);
            } else {
                final LocalDate d = date;
                dateMatches = sortByStatus(
                        allFiltered.stream().filter(m -> m.getPlayedAt().toLocalDate().equals(d)).toList(),
                        now, liveThreshold);
            }
        } else {
            recentMatches = allFiltered.stream()
                    .filter(m -> !m.getPlayedAt().isAfter(now) && m.getPlayedAt().toLocalDate().equals(today))
                    .sorted(Comparator.comparing(MatchDto::getPlayedAt).reversed())
                    .toList();
            upcomingMatches = allFiltered.stream()
                    .filter(m -> m.getPlayedAt().isAfter(now))
                    .toList();
        }

        List<String> matchDateStrings = matchDates.stream().map(LocalDate::toString).toList();

        model.addAttribute("upcomingMatches", upcomingMatches);
        model.addAttribute("recentMatches", recentMatches);
        model.addAttribute("dateMatches", dateMatches);
        model.addAttribute("leagues", leagueService.findAll());
        model.addAttribute("matchDates", matchDates);
        model.addAttribute("matchDateStrings", matchDateStrings);
        if (!matchDates.isEmpty()) {
            model.addAttribute("firstMatchDate", matchDates.get(0));
            model.addAttribute("lastMatchDate", matchDates.get(matchDates.size() - 1));
        }
        model.addAttribute("selectedLeague", league);
        model.addAttribute("selectedDate", selectedDate);
        model.addAttribute("selectedTeam", team);
        if (team != null) {
            TeamDto t = teamService.findById(team);
            model.addAttribute("selectedTeamName",
                    t.getName() + (t.getCity() != null ? " (" + t.getCity() + ")" : ""));
            if (t.getLeagueId() != null) {
                LeagueDetailView detail = leagueService.findDetail(t.getLeagueId());
                model.addAttribute("teamStandings", detail.getStandings());
                model.addAttribute("teamLeagueName", detail.getName());
                model.addAttribute("teamLeagueId", t.getLeagueId());
            }
        }
        List<MatchDto> liveSource = dateMatches != null ? dateMatches : recentMatches;
        List<MatchDto> liveMatches = liveSource.stream()
                .filter(m -> m.getPlayedAt().isBefore(now) && m.getPlayedAt().isAfter(liveThreshold))
                .sorted(Comparator.comparing(MatchDto::getPlayedAt))
                .toList();
        model.addAttribute("liveMatches", liveMatches);
        List<Map<String, Object>> liveMatchesForJs = liveMatches.stream()
                .map(m -> {
                    List<Map<String, Object>> goals = m.getGoalTimeline().stream()
                            .map(g -> Map.<String, Object>of(
                                    "minute", g.getMinute() != null ? g.getMinute() : 0,
                                    "half", g.getHalf() != null ? g.getHalf().name() : "FIRST",
                                    "homeGoal", g.isHomeGoal(),
                                    "rh", g.getRunningHomeScore() != null ? g.getRunningHomeScore() : 0,
                                    "ra", g.getRunningAwayScore() != null ? g.getRunningAwayScore() : 0))
                            .toList();
                    return Map.<String, Object>of(
                            "id", m.getId().toString(),
                            "elapsedMin", Duration.between(m.getPlayedAt(), now).toMinutes(),
                            "goals", goals);
                })
                .toList();
        boolean hasTodayResults = recentMatches.stream()
                .anyMatch(m -> !m.getPlayedAt().isAfter(liveThreshold));
        model.addAttribute("liveMatchesForJs", liveMatchesForJs);
        Map<UUID, Long> elapsedByMatchId = liveMatches.stream()
                .collect(Collectors.toMap(MatchDto::getId, m -> Duration.between(m.getPlayedAt(), now).toMinutes()));
        model.addAttribute("elapsedByMatchId", elapsedByMatchId);
        model.addAttribute("hasTodayResults", hasTodayResults);
        model.addAttribute("now", now);
        model.addAttribute("liveThreshold", liveThreshold);
        model.addAttribute("today", today);
        model.addAttribute("todayStr", today.toString());
        model.addAttribute("selectedDateStr", selectedDate != null ? selectedDate.toString() : "");
        if (selectedDate != null) {
            model.addAttribute("selectedDateUtcNoon",
                    selectedDate.atTime(12, 0)
                            .atZone(ZoneId.of("Europe/Sofia"))
                            .toInstant()
                            .toString());
        }
        List<String> allMatchUtcIsos;
        if (league == null && team == null) {
            allMatchUtcIsos = matchService.findAllMatchUtcIsos();
        } else {
            allMatchUtcIsos = allFiltered.stream()
                    .map(m -> m.getPlayedAt().atZone(ZoneId.of("Europe/Sofia")).toInstant().toString())
                    .toList();
        }
        model.addAttribute("allMatchUtcIsos", allMatchUtcIsos);
        return "matches/list";
    }

    private List<MatchDto> sortByStatus(List<MatchDto> matches, LocalDateTime now, LocalDateTime liveThreshold) {
        return matches.stream()
                .sorted((a, b) -> {
                    int pa = statusPriority(a, now, liveThreshold);
                    int pb = statusPriority(b, now, liveThreshold);
                    if (pa != pb) return Integer.compare(pa, pb);
                    if (pa == 2) return b.getPlayedAt().compareTo(a.getPlayedAt());
                    return a.getPlayedAt().compareTo(b.getPlayedAt());
                })
                .toList();
    }

    private int statusPriority(MatchDto m, LocalDateTime now, LocalDateTime liveThreshold) {
        if (m.getPlayedAt().isBefore(now) && m.getPlayedAt().isAfter(liveThreshold)) return 0;
        if (!m.getPlayedAt().isBefore(now)) return 1;
        return 2;
    }

    private LocalDate resolveDate(LocalDate requested, List<LocalDate> matchDates, LocalDate today) {
        if (requested != null) return requested;
        if (matchDates.isEmpty()) return null;
        if (matchDates.contains(today)) return today;
        return matchDates.stream()
                .filter(d -> !d.isAfter(today))
                .max(Comparator.naturalOrder())
                .orElse(matchDates.get(0));
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
        if (fromRequest != null) model.addAttribute("fromRequest", fromRequest);
        return "matches/form";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public String create(@Valid @ModelAttribute("matchDto") MatchDto matchDto, BindingResult bindingResult,
                          @RequestParam(required = false) UUID fromRequest,
                          Model model, Authentication authentication, RedirectAttributes redirectAttributes) {
        validateTeams(matchDto, bindingResult);
        if (bindingResult.hasErrors()) {
            model.addAttribute("teams", teamService.findAll());
            if (fromRequest != null) model.addAttribute("fromRequest", fromRequest);
            return "matches/form";
        }

        boolean executed = changeRequestService.submitOrExecute(
                EntityType.MATCH, ChangeAction.CREATE, matchDto, null, authentication);
        if (fromRequest != null) changeRequestService.cancelIfPending(fromRequest, authentication);
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
        if (fromRequest != null) model.addAttribute("fromRequest", fromRequest);
        return "matches/form";
    }

    @PutMapping("/{id}")
    public String edit(@PathVariable UUID id, @Valid @ModelAttribute("matchDto") MatchDto matchDto,
                        BindingResult bindingResult,
                        @RequestParam(required = false) UUID fromRequest,
                        Model model, Authentication authentication,
                        RedirectAttributes redirectAttributes) {
        validateTeams(matchDto, bindingResult);
        if (bindingResult.hasErrors()) {
            model.addAttribute("teams", teamService.findAll());
            if (fromRequest != null) model.addAttribute("fromRequest", fromRequest);
            return "matches/form";
        }

        boolean executed = changeRequestService.submitOrExecute(
                EntityType.MATCH, ChangeAction.UPDATE, matchDto, id, authentication);
        if (fromRequest != null) changeRequestService.cancelIfPending(fromRequest, authentication);
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
            return "redirect:/matches/" + id;
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
            return "redirect:/matches/" + id;
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
        return "redirect:/matches/" + id;
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
