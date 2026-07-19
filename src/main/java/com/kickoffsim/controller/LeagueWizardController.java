package com.kickoffsim.controller;

import com.kickoffsim.dto.LeagueBundlePayload;
import com.kickoffsim.dto.LeagueDto;
import com.kickoffsim.dto.LeagueWizardForm;
import com.kickoffsim.dto.PlayerRowDto;
import com.kickoffsim.dto.TeamCreateForm;
import com.kickoffsim.dto.TeamDto;
import com.kickoffsim.dto.TeamSquadPayload;
import com.kickoffsim.model.ChangeAction;
import com.kickoffsim.model.EntityType;
import com.kickoffsim.model.LeagueFormat;
import com.kickoffsim.service.ChangeRequestService;
import com.kickoffsim.service.LeagueService;
import com.kickoffsim.service.TeamService;
import com.kickoffsim.web.ResubmitSupport;
import com.kickoffsim.web.SquadRowValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/leagues/wizard")
public class LeagueWizardController {

    private static final int MAX_SQUAD_SIZE = 12;
    private static final int MIN_SQUAD_SIZE = 6;

    private final TeamService teamService;
    private final LeagueService leagueService;
    private final ChangeRequestService changeRequestService;

    @GetMapping
    public String start(Model model) {
        model.addAttribute("formats", LeagueFormat.values());
        return "leagues/wizard-format";
    }

    @GetMapping("/teams")
    public String chooseTeams(@RequestParam int format, Model model, RedirectAttributes redirectAttributes) {
        if (LeagueFormat.forTeamCount(format).isEmpty()) {
            redirectAttributes.addFlashAttribute("warnMessage", "Invalid league format.");
            return "redirect:/leagues/wizard";
        }

        List<TeamDto> freeTeams = teamService.findAllFree();
        boolean hasEligible = freeTeams.stream().anyMatch(t -> t.getPlayerCount() >= MIN_SQUAD_SIZE);
        if (!hasEligible) {
            return "redirect:/leagues/wizard/new-teams?format=" + format;
        }

        model.addAttribute("format", format);
        model.addAttribute("availableTeams", freeTeams);
        return "leagues/wizard-teams";
    }

    @GetMapping("/new-teams")
    public String newTeams(@RequestParam int format,
                            @RequestParam(required = false) List<UUID> existingTeamIds,
                            Model model, RedirectAttributes redirectAttributes) {
        if (LeagueFormat.forTeamCount(format).isEmpty()) {
            redirectAttributes.addFlashAttribute("warnMessage", "Invalid league format.");
            return "redirect:/leagues/wizard";
        }
        List<UUID> selected = existingTeamIds != null ? existingTeamIds : List.of();
        if (selected.size() > format) {
            redirectAttributes.addFlashAttribute("warnMessage",
                    "You selected more teams than the chosen format allows.");
            return "redirect:/leagues/wizard/teams?format=" + format;
        }

        List<TeamDto> eligibleSelected = selected.stream()
                .map(teamService::findById)
                .filter(t -> t.getLeagueId() == null && t.getPlayerCount() >= MIN_SQUAD_SIZE)
                .toList();
        if (eligibleSelected.size() != selected.size()) {
            redirectAttributes.addFlashAttribute("warnMessage",
                    "One or more selected teams are no longer eligible. Please reselect.");
            return "redirect:/leagues/wizard/teams?format=" + format;
        }

        int shortfall = format - eligibleSelected.size();

        LeagueWizardForm form = new LeagueWizardForm();
        form.setFormat(format);
        form.setExistingTeamIds(eligibleSelected.stream().map(TeamDto::getId).toList());
        for (int i = 0; i < shortfall; i++) {
            form.getNewTeams().add(prefilledTeamBlock());
        }

        model.addAttribute("leagueWizardForm", form);
        model.addAttribute("existingTeams", eligibleSelected);
        return "leagues/wizard-new-teams";
    }

    @GetMapping("/edit")
    public String edit(@RequestParam UUID fromRequest, Model model, Authentication authentication) {
        LeagueBundlePayload payload =
                (LeagueBundlePayload) changeRequestService.getPayloadForResubmit(fromRequest, authentication);

        LeagueWizardForm form = new LeagueWizardForm();
        form.setLeagueName(payload.getLeagueName());
        form.setScheduleStartDate(payload.getScheduleStartDate());
        form.setScheduleStartTime(payload.getScheduleStartTime());
        form.setFormat(payload.getFormat());
        form.setExistingTeamIds(payload.getExistingTeamIds());
        for (TeamSquadPayload newTeam : payload.getNewTeams()) {
            form.getNewTeams().add(toTeamCreateForm(newTeam));
        }

        List<TeamDto> existingTeams = payload.getExistingTeamIds().stream()
                .map(teamService::findById)
                .toList();

        model.addAttribute("leagueWizardForm", form);
        model.addAttribute("existingTeams", existingTeams);
        ResubmitSupport.addRejectionBanner(model, changeRequestService, fromRequest, authentication);
        return "leagues/wizard-new-teams";
    }

    @PostMapping
    public String submit(@Valid @ModelAttribute("leagueWizardForm") LeagueWizardForm form,
                          BindingResult bindingResult,
                          @RequestParam(required = false) UUID fromRequest, Model model,
                          Authentication authentication, RedirectAttributes redirectAttributes) {
        if (LeagueFormat.forTeamCount(form.getFormat()).isEmpty()) {
            bindingResult.reject("wizard.format.invalid", "Invalid league format.");
        }

        List<TeamDto> existingTeams = new ArrayList<>();
        for (UUID teamId : form.getExistingTeamIds()) {
            TeamDto team = teamService.findById(teamId);
            if (team.getLeagueId() != null || team.getPlayerCount() < MIN_SQUAD_SIZE) {
                bindingResult.reject("wizard.existing.ineligible",
                        "Team '" + team.getName() + "' is no longer eligible. Please restart the wizard.");
            }
            existingTeams.add(team);
        }

        if (form.getExistingTeamIds().size() + form.getNewTeams().size() != form.getFormat()) {
            bindingResult.reject("wizard.count.mismatch",
                    "The number of teams must exactly match the chosen format (" + form.getFormat() + ").");
        }

        Set<String> namesInBatch = new HashSet<>();
        List<List<Integer>> filledRowsPerTeam = new ArrayList<>();
        for (int i = 0; i < form.getNewTeams().size(); i++) {
            TeamCreateForm teamForm = form.getNewTeams().get(i);
            String prefix = "newTeams[" + i + "]";

            if (teamForm.getName() == null || teamForm.getName().isBlank()) {
                bindingResult.rejectValue(prefix + ".name", "NotBlank", "Team name is required");
            }
            if (teamForm.getCity() == null || teamForm.getCity().isBlank()) {
                bindingResult.rejectValue(prefix + ".city", "NotBlank", "City is required");
            }

            List<Integer> filledRows = SquadRowValidator.validate(
                    teamForm.getPlayers(), prefix + ".players", Set.of(), bindingResult);
            filledRowsPerTeam.add(filledRows);

            if (filledRows.size() < MIN_SQUAD_SIZE) {
                bindingResult.reject("wizard.squad.minimum",
                        "Team " + (i + 1) + " must have at least " + MIN_SQUAD_SIZE + " players.");
            }
            if (filledRows.size() > MAX_SQUAD_SIZE) {
                bindingResult.reject("wizard.squad.capacity",
                        "Team " + (i + 1) + " can have at most " + MAX_SQUAD_SIZE + " players.");
            }

            if (teamForm.getName() != null && !teamForm.getName().isBlank()) {
                String key = teamForm.getName().trim().toLowerCase() + "|"
                        + (teamForm.getCity() != null ? teamForm.getCity().trim().toLowerCase() : "");
                if (!namesInBatch.add(key)) {
                    bindingResult.rejectValue(prefix + ".name", "team.name.duplicate",
                            "This team name/city combination is used more than once in this submission");
                } else if (teamService.existsByNameAndCity(teamForm.getName(), teamForm.getCity())) {
                    bindingResult.rejectValue(prefix + ".name", "team.name.taken",
                            "A team with this name and city already exists");
                }
            }
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("existingTeams", existingTeams);
            if (fromRequest != null) model.addAttribute("fromRequest", fromRequest);
            return "leagues/wizard-new-teams";
        }

        LeagueBundlePayload payload = new LeagueBundlePayload();
        payload.setLeagueName(form.getLeagueName());
        payload.setScheduleStartDate(form.getScheduleStartDate());
        payload.setScheduleStartTime(form.getScheduleStartTime());
        payload.setFormat(form.getFormat());
        payload.setExistingTeamIds(form.getExistingTeamIds());

        for (int i = 0; i < form.getNewTeams().size(); i++) {
            TeamCreateForm teamForm = form.getNewTeams().get(i);
            TeamDto teamDto = new TeamDto();
            teamDto.setName(teamForm.getName());
            teamDto.setCity(teamForm.getCity());
            TeamSquadPayload squadPayload = new TeamSquadPayload();
            squadPayload.setTeam(teamDto);
            squadPayload.setPlayers(SquadRowValidator.toPlayers(teamForm.getPlayers(), filledRowsPerTeam.get(i)));
            payload.getNewTeams().add(squadPayload);
        }

        boolean executed;
        try {
            executed = changeRequestService.submitOrExecute(
                    EntityType.LEAGUE_BUNDLE, ChangeAction.CREATE, payload, null, authentication);
        } catch (DataIntegrityViolationException e) {
            bindingResult.reject("wizard.name.taken",
                    "A team or league name in this bundle already exists. Please choose different names.");
            model.addAttribute("existingTeams", existingTeams);
            if (fromRequest != null) model.addAttribute("fromRequest", fromRequest);
            return "leagues/wizard-new-teams";
        }
        if (fromRequest != null) changeRequestService.cancelIfPending(fromRequest, authentication);

        if (executed) {
            UUID newLeagueId = leagueService.findAll().stream()
                    .filter(l -> form.getLeagueName().equals(l.getName()))
                    .map(LeagueDto::getId)
                    .findFirst().orElse(null);
            redirectAttributes.addFlashAttribute("statusMessage", "League created and schedule generated.");
            return newLeagueId != null ? "redirect:/leagues/" + newLeagueId + "#schedule" : "redirect:/leagues";
        }

        redirectAttributes.addFlashAttribute("statusMessage", "Submitted for admin approval.");
        return "redirect:/leagues";
    }

    private TeamCreateForm prefilledTeamBlock() {
        TeamCreateForm form = new TeamCreateForm();
        for (int number = 1; number <= MAX_SQUAD_SIZE; number++) {
            PlayerRowDto row = new PlayerRowDto();
            row.setShirtNumber(number);
            form.getPlayers().add(row);
        }
        return form;
    }

    private TeamCreateForm toTeamCreateForm(TeamSquadPayload squadPayload) {
        TeamCreateForm form = new TeamCreateForm();
        form.setName(squadPayload.getTeam().getName());
        form.setCity(squadPayload.getTeam().getCity());
        for (var player : squadPayload.getPlayers()) {
            PlayerRowDto row = new PlayerRowDto();
            row.setShirtNumber(player.getShirtNumber());
            row.setFirstName(player.getFirstName());
            row.setLastName(player.getLastName());
            form.getPlayers().add(row);
        }
        return form;
    }
}
