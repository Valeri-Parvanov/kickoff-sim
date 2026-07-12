package bg.softuni.footballleague.controller;

import bg.softuni.footballleague.dto.*;
import bg.softuni.footballleague.model.ChangeAction;
import bg.softuni.footballleague.model.EntityType;
import bg.softuni.footballleague.service.*;
import bg.softuni.footballleague.web.LogoGenerator;
import bg.softuni.footballleague.web.SortSupport;
import bg.softuni.footballleague.web.SquadRowValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Controller
@RequiredArgsConstructor
@RequestMapping("/teams")
public class TeamController {

    private static final int MAX_SQUAD_SIZE = 12;
    private static final Sort DEFAULT_SORT = Sort.by("league.name").and(Sort.by("name"));
    private static final Map<String, String> SORTABLE_FIELDS = Map.of(
            "name", "name",
            "city", "city",
            "league", "league.name"
    );

    private final TeamService teamService;
    private final LeagueService leagueService;
    private final ChangeRequestService changeRequestService;
    private final PlayerService playerService;
    private final MatchService matchService;

    @GetMapping("/{id}/logo")
    @ResponseBody
    public ResponseEntity<String> logo(@PathVariable UUID id) {
        TeamDto team = teamService.findById(id);
        String svg = LogoGenerator.generate(team.getName(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("image/svg+xml"))
                .header("Cache-Control", "public, max-age=86400")
                .body(svg);
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        TeamDto team = teamService.findById(id);
        model.addAttribute("team", team);
        model.addAttribute("players", playerService.findAllByTeam(id));
        model.addAttribute("remainingSlots", playerService.squadRemainingSlots(id));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime liveThreshold = now.minusMinutes(46);
        model.addAttribute("now", now);
        model.addAttribute("liveThreshold", liveThreshold);
        model.addAttribute("liveMatchesForJs", List.of());
        model.addAttribute("elapsedByMatchId", Map.of());

        if (team.getLeagueId() != null) {
            LeagueDetailView league = leagueService.findDetail(team.getLeagueId());
            model.addAttribute("leagueStandings", league.getStandings());

            List<Map<String, Object>> liveMatchesForJs = league.getMatches().stream()
                    .filter(m -> m.getPlayedAt().isBefore(now) && m.getPlayedAt().isAfter(liveThreshold))
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
                                "homeTeamId", m.getHomeTeamId().toString(),
                                "awayTeamId", m.getAwayTeamId().toString(),
                                "elapsedMin", Duration.between(m.getPlayedAt(), now).toMinutes(),
                                "goals", goals);
                    })
                    .toList();
            model.addAttribute("liveMatchesForJs", liveMatchesForJs);
            Map<UUID, Long> elapsedByMatchId = league.getMatches().stream()
                    .filter(m -> m.getPlayedAt().isBefore(now) && m.getPlayedAt().isAfter(liveThreshold))
                    .collect(Collectors.toMap(MatchDto::getId, m -> Duration.between(m.getPlayedAt(), now).toMinutes()));
            model.addAttribute("elapsedByMatchId", elapsedByMatchId);
        }

        List<MatchDto> all = matchService.findAll(Sort.by(Sort.Direction.ASC, "playedAt"));
        model.addAttribute("teamResults", all.stream()
                .filter(m -> id.equals(m.getHomeTeamId()) || id.equals(m.getAwayTeamId()))
                .filter(m -> !m.getPlayedAt().isAfter(now))
                .sorted(Comparator.comparing(MatchDto::getPlayedAt).reversed())
                .toList());
        model.addAttribute("teamUpcoming", all.stream()
                .filter(m -> id.equals(m.getHomeTeamId()) || id.equals(m.getAwayTeamId()))
                .filter(m -> m.getPlayedAt().isAfter(now))
                .toList());
        return "teams/detail";
    }

    @GetMapping
    public String list(@RequestParam(required = false) String sort,
                        @RequestParam(required = false) String dir,
                        Model model) {
        Sort resolvedSort = SortSupport.resolve(sort, dir, SORTABLE_FIELDS, DEFAULT_SORT);
        model.addAttribute("teams", teamService.findAll(resolvedSort));
        model.addAttribute("currentSort", sort);
        model.addAttribute("currentDir", dir == null ? "asc" : dir);
        long eligibleFreeCount = teamService.findAllFree().stream()
                .filter(t -> t.getPlayerCount() >= 6)
                .count();
        if (eligibleFreeCount >= 6) {
            model.addAttribute("leagueReadyCount", eligibleFreeCount);
        }
        return "teams/list";
    }

    @GetMapping("/form")
    public String createForm(@RequestParam(required = false) UUID fromRequest, Model model,
                              Authentication authentication) {
        TeamCreateForm form = new TeamCreateForm();
        if (fromRequest != null) {
            Object payload = changeRequestService.getPayloadForResubmit(fromRequest, authentication);
            if (payload instanceof TeamSquadPayload squadPayload) {
                TeamDto team = squadPayload.getTeam();
                form.setName(team.getName());
                form.setCity(team.getCity());
                form.setLeagueId(team.getLeagueId());
                form.setTeamId(team.getId());
                for (PlayerDto p : squadPayload.getPlayers()) {
                    PlayerRowDto row = new PlayerRowDto();
                    row.setShirtNumber(p.getShirtNumber());
                    row.setFirstName(p.getFirstName());
                    row.setLastName(p.getLastName());
                    form.getPlayers().add(row);
                }
                int remaining = playerService.squadRemainingSlots(form.getTeamId());
                Set<Integer> taken = new HashSet<>();
                playerService.findAllByTeam(form.getTeamId())
                        .forEach(p -> { if (p.getShirtNumber() != null) taken.add(p.getShirtNumber()); });
                squadPayload.getPlayers()
                        .forEach(p -> { if (p.getShirtNumber() != null) taken.add(p.getShirtNumber()); });
                int next = 1;
                while (form.getPlayers().size() < remaining) {
                    PlayerRowDto row = new PlayerRowDto();
                    while (next <= 99 && taken.contains(next)) next++;
                    if (next <= 99) { row.setShirtNumber(next); taken.add(next++); }
                    form.getPlayers().add(row);
                }
            } else {
                TeamDto team = (TeamDto) payload;
                form.setName(team.getName());
                form.setCity(team.getCity());
                form.setLeagueId(team.getLeagueId());
            }
            model.addAttribute("fromRequest", fromRequest);
        }
        prefillSquadRows(form);
        model.addAttribute("teamCreateForm", form);
        model.addAttribute("leagues", leagueService.findAll());
        return "teams/create";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("teamCreateForm") TeamCreateForm form, BindingResult bindingResult,
                          @RequestParam(required = false) UUID fromRequest,
                          Model model, Authentication authentication, RedirectAttributes redirectAttributes) {
        List<Integer> filledRows =
                SquadRowValidator.validate(form.getPlayers(), "players", Collections.emptySet(), bindingResult);
        if (form.getTeamId() == null && filledRows.size() < 6) {
            bindingResult.reject("squad.minimum", "A team must have at least 6 players.");
        }
        if (filledRows.size() > MAX_SQUAD_SIZE) {
            bindingResult.reject("squad.capacity", "A team can have at most " + MAX_SQUAD_SIZE + " players.");
        }
        if (form.getTeamId() == null
                && form.getName() != null && !form.getName().isBlank()
                && teamService.existsByNameAndCity(form.getName(), form.getCity())) {
            bindingResult.rejectValue("name", "team.name.taken",
                    "A team with this name and city already exists");
        }
        if (form.getLeagueId() != null && leagueService.hasLeagueStarted(form.getLeagueId())) {
            bindingResult.rejectValue("leagueId", "league.started",
                    "Cannot add a team to a league that has already started.");
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("leagues", leagueService.findAll());
            if (fromRequest != null) model.addAttribute("fromRequest", fromRequest);
            return "teams/create";
        }

        TeamDto teamDto = new TeamDto();
        teamDto.setName(form.getName());
        teamDto.setCity(form.getCity());
        teamDto.setLeagueId(form.getLeagueId());
        teamDto.setId(form.getTeamId());

        boolean executed;
        try {
            if (filledRows.isEmpty()) {
                executed = changeRequestService.submitOrExecute(
                        EntityType.TEAM, ChangeAction.CREATE, teamDto, null, authentication);
            } else {
                TeamSquadPayload payload = new TeamSquadPayload();
                payload.setTeam(teamDto);
                payload.setPlayers(SquadRowValidator.toPlayers(form.getPlayers(), filledRows));
                executed = changeRequestService.submitOrExecute(
                        EntityType.TEAM_SQUAD, ChangeAction.CREATE, payload, null, authentication);
            }
        } catch (DataIntegrityViolationException e) {
            bindingResult.rejectValue("name", "team.name.taken",
                    "A team with this name already exists");
            model.addAttribute("leagues", leagueService.findAll());
            if (fromRequest != null) model.addAttribute("fromRequest", fromRequest);
            return "teams/create";
        }

        if (fromRequest != null) changeRequestService.cancelIfPending(fromRequest, authentication);
        redirectAttributes.addFlashAttribute("statusMessage",
                executed ? "Team created." : "Submitted for admin approval.");
        return "redirect:/teams";
    }

    private void prefillSquadRows(TeamCreateForm form) {
        if (!form.getPlayers().isEmpty()) {
            return;
        }
        for (int number = 1; number <= MAX_SQUAD_SIZE; number++) {
            PlayerRowDto row = new PlayerRowDto();
            row.setShirtNumber(number);
            form.getPlayers().add(row);
        }
    }

    @GetMapping("/{id}/form")
    public String editForm(@PathVariable UUID id, @RequestParam(required = false) UUID fromRequest, Model model,
                            Authentication authentication) {
        TeamDto teamDto = fromRequest != null
                ? (TeamDto) changeRequestService.getPayloadForResubmit(fromRequest, authentication)
                : teamService.findById(id);
        teamDto.setId(id);
        model.addAttribute("teamDto", teamDto);
        model.addAttribute("leagues", leagueService.findAll());
        if (fromRequest != null) model.addAttribute("fromRequest", fromRequest);
        return "teams/form";
    }

    @PutMapping("/{id}")
    public String edit(@PathVariable UUID id, @Valid @ModelAttribute("teamDto") TeamDto teamDto,
                        BindingResult bindingResult,
                        @RequestParam(required = false) UUID fromRequest,
                        Model model, Authentication authentication,
                        RedirectAttributes redirectAttributes) {
        UUID newLeagueId = teamDto.getLeagueId();
        if (newLeagueId != null) {
            UUID currentLeagueId = teamService.findById(id).getLeagueId();
            if (!newLeagueId.equals(currentLeagueId) && leagueService.hasLeagueStarted(newLeagueId)) {
                bindingResult.rejectValue("leagueId", "league.started",
                        "Cannot add a team to a league that has already started.");
            }
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("leagues", leagueService.findAll());
            if (fromRequest != null) model.addAttribute("fromRequest", fromRequest);
            return "teams/form";
        }

        boolean executed = changeRequestService.submitOrExecute(
                EntityType.TEAM, ChangeAction.UPDATE, teamDto, id, authentication);
        if (fromRequest != null) changeRequestService.cancelIfPending(fromRequest, authentication);
        redirectAttributes.addFlashAttribute("statusMessage",
                executed ? "Team updated." : "Submitted for admin approval.");
        return "redirect:/teams";
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable UUID id, Authentication authentication,
                          RedirectAttributes redirectAttributes) {
        boolean executed = changeRequestService.submitOrExecute(
                EntityType.TEAM, ChangeAction.DELETE, null, id, authentication);
        redirectAttributes.addFlashAttribute("statusMessage",
                executed ? "Team deleted." : "Submitted for admin approval.");
        return "redirect:/teams";
    }
}
