package com.kickoffsim.controller;

import com.kickoffsim.client.NotificationClient;
import com.kickoffsim.dto.GoalDto;
import com.kickoffsim.dto.LeagueDetailView;
import com.kickoffsim.dto.MatchDto;
import com.kickoffsim.dto.PlayerDto;
import com.kickoffsim.dto.PlayerRowDto;
import com.kickoffsim.dto.TeamCreateForm;
import com.kickoffsim.dto.TeamDto;
import com.kickoffsim.dto.TeamSquadPayload;
import com.kickoffsim.model.Half;
import org.springframework.dao.DataIntegrityViolationException;
import com.kickoffsim.service.ChangeRequestService;
import com.kickoffsim.service.LeagueService;
import com.kickoffsim.service.MatchService;
import com.kickoffsim.service.PlayerService;
import com.kickoffsim.service.TeamService;
import com.kickoffsim.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.kickoffsim.client.SubscriptionDto;
import com.kickoffsim.model.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TeamControllerTest {

    @Mock private TeamService teamService;
    @Mock private LeagueService leagueService;
    @Mock private ChangeRequestService changeRequestService;
    @Mock private PlayerService playerService;
    @Mock private MatchService matchService;
    @Mock private UserService userService;
    @Mock private NotificationClient notificationClient;

    @InjectMocks private TeamController controller;

    private final Authentication auth = mock(Authentication.class);

    private TeamDto team(UUID id) {
        TeamDto t = new TeamDto();
        t.setId(id);
        t.setName("SofiaFC");
        t.setCity("Sofia");
        return t;
    }

    @Test
    void logo_returnsSvgResponse() {
        UUID id = UUID.randomUUID();
        when(teamService.findById(id)).thenReturn(team(id));

        ResponseEntity<String> resp = controller.logo(id);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).contains("svg");
    }

    @Test
    void list_returnsViewWithTeams() {
        when(teamService.findAll(any(Sort.class))).thenReturn(List.of());
        when(teamService.findAllFree()).thenReturn(List.of());
        Model model = new ExtendedModelMap();

        assertThat(controller.list(null, null, model)).isEqualTo("teams/list");
        assertThat(model.getAttribute("currentDir")).isEqualTo("asc");
    }

    @Test
    void detail_noLeague_returnsView() {
        UUID id = UUID.randomUUID();
        when(teamService.findById(id)).thenReturn(team(id));
        when(playerService.findAllByTeam(id)).thenReturn(List.of());
        when(playerService.squadRemainingSlots(id)).thenReturn(12);
        when(matchService.findAll(any(Sort.class))).thenReturn(List.of());
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getQueryString()).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/teams/" + id);
        Model model = new ExtendedModelMap();

        assertThat(controller.detail(id, model, null, request)).isEqualTo("teams/detail");
        assertThat(model.getAttribute("team")).isNotNull();
    }

    @Test
    void detail_withLeagueAndLiveMatch_returnsView() {
        UUID id = UUID.randomUUID();
        UUID leagueId = UUID.randomUUID();
        TeamDto t = team(id);
        t.setLeagueId(leagueId);
        when(teamService.findById(id)).thenReturn(t);
        when(playerService.findAllByTeam(id)).thenReturn(List.of());
        when(playerService.squadRemainingSlots(id)).thenReturn(0);

        LeagueDetailView league = mock(LeagueDetailView.class);
        MatchDto live = new MatchDto();
        live.setId(UUID.randomUUID());
        live.setHomeTeamId(id);
        live.setAwayTeamId(UUID.randomUUID());
        live.setPlayedAt(LocalDateTime.now().minusMinutes(30));
        GoalDto g = new GoalDto();
        g.setMinute(5);
        g.setHalf(Half.FIRST);
        g.setHomeGoal(true);
        g.setRunningHomeScore(1);
        g.setRunningAwayScore(0);
        live.getGoalTimeline().add(g);
        when(league.getStandings()).thenReturn(List.of());
        when(league.getMatches()).thenReturn(List.of(live));
        when(leagueService.findDetail(leagueId)).thenReturn(league);

        MatchDto past = new MatchDto();
        past.setId(UUID.randomUUID());
        past.setHomeTeamId(id);
        past.setAwayTeamId(UUID.randomUUID());
        past.setPlayedAt(LocalDateTime.now().minusDays(1));
        when(matchService.findAll(any(Sort.class))).thenReturn(List.of(past));

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getQueryString()).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/teams/" + id);
        Model model = new ExtendedModelMap();

        assertThat(controller.detail(id, model, null, request)).isEqualTo("teams/detail");
        assertThat(model.getAttribute("teamResults")).isNotNull();
    }

    @Test
    void createForm_blank_prefillsSquadAndShowsForm() {
        when(leagueService.findAll()).thenReturn(List.of());
        Model model = new ExtendedModelMap();

        assertThat(controller.createForm(null, model, auth)).isEqualTo("teams/create");
        assertThat(model.getAttribute("teamCreateForm")).isNotNull();
    }

    @Test
    void create_newTeamTooFewPlayers_returnsFormWithError() {
        TeamCreateForm form = new TeamCreateForm();
        form.setName("SofiaFC");
        form.setCity("Sofia");
        BindingResult br = new BeanPropertyBindingResult(form, "teamCreateForm");
        when(leagueService.findAll()).thenReturn(List.of());
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.create(form, br, null, model, auth, ra);

        assertThat(view).isEqualTo("teams/create");
        assertThat(br.hasErrors()).isTrue();
    }

    @Test
    void create_existingTeamNoNewPlayers_submitsAndRedirects() {
        TeamCreateForm form = new TeamCreateForm();
        form.setTeamId(UUID.randomUUID());
        BindingResult br = new BeanPropertyBindingResult(form, "teamCreateForm");
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(true);
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.create(form, br, null, model, auth, ra);

        assertThat(view).isEqualTo("redirect:/teams");
        assertThat(ra.getFlashAttributes().get("statusMessage")).isEqualTo("Team created.");
    }

    @Test
    void editForm_notFromRequest_showsForm() {
        UUID id = UUID.randomUUID();
        when(teamService.findById(id)).thenReturn(team(id));
        when(leagueService.findAll()).thenReturn(List.of());
        Model model = new ExtendedModelMap();

        assertThat(controller.editForm(id, null, model, auth)).isEqualTo("teams/form");
        assertThat(model.getAttribute("teamDto")).isNotNull();
    }

    @Test
    void edit_valid_executed_redirects() {
        UUID id = UUID.randomUUID();
        TeamDto dto = team(id);
        dto.setLeagueId(null);
        BindingResult br = new BeanPropertyBindingResult(dto, "teamDto");
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(true);
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.edit(id, dto, br, null, model, auth, ra);

        assertThat(view).isEqualTo("redirect:/teams");
        assertThat(ra.getFlashAttributes().get("statusMessage")).isEqualTo("Team updated.");
    }

    @Test
    void createForm_fromSquadPayload_prefillsPlayers() {
        UUID reqId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        TeamSquadPayload payload = new TeamSquadPayload();
        payload.setTeam(team(teamId));
        PlayerDto p = new PlayerDto();
        p.setShirtNumber(7);
        p.setFirstName("Ivan");
        p.setLastName("Petrov");
        payload.setPlayers(List.of(p));
        when(changeRequestService.getPayloadForResubmit(reqId, auth)).thenReturn(payload);
        when(playerService.squadRemainingSlots(teamId)).thenReturn(3);
        when(playerService.findAllByTeam(teamId)).thenReturn(List.of());
        when(leagueService.findAll()).thenReturn(List.of());
        Model model = new ExtendedModelMap();

        assertThat(controller.createForm(reqId, model, auth)).isEqualTo("teams/create");
        assertThat(model.getAttribute("fromRequest")).isEqualTo(reqId);
    }

    @Test
    void createForm_fromTeamDto_prefillsTeamOnly() {
        UUID reqId = UUID.randomUUID();
        when(changeRequestService.getPayloadForResubmit(reqId, auth)).thenReturn(team(null));
        when(leagueService.findAll()).thenReturn(List.of());
        Model model = new ExtendedModelMap();

        assertThat(controller.createForm(reqId, model, auth)).isEqualTo("teams/create");
        assertThat(model.getAttribute("teamCreateForm")).isNotNull();
    }

    @Test
    void create_withSixPlayers_submitsSquadPayload() {
        TeamCreateForm form = new TeamCreateForm();
        form.setName("SofiaFC");
        form.setCity("Sofia");
        for (int i = 1; i <= 6; i++) {
            PlayerRowDto row = new PlayerRowDto();
            row.setShirtNumber(i);
            row.setFirstName("First" + i);
            row.setLastName("Last" + i);
            form.getPlayers().add(row);
        }
        BindingResult br = new BeanPropertyBindingResult(form, "teamCreateForm");
        when(teamService.existsByNameAndCity("SofiaFC", "Sofia")).thenReturn(false);
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(true);
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.create(form, br, null, model, auth, ra)).isEqualTo("redirect:/teams");
        assertThat(ra.getFlashAttributes().get("statusMessage")).isEqualTo("Team created.");
    }

    @Test
    void create_duplicateName_returnsFormWithError() {
        TeamCreateForm form = new TeamCreateForm();
        form.setTeamId(UUID.randomUUID());
        BindingResult br = new BeanPropertyBindingResult(form, "teamCreateForm");
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("dup"));
        when(leagueService.findAll()).thenReturn(List.of());
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.create(form, br, null, model, auth, ra)).isEqualTo("teams/create");
        assertThat(br.hasErrors()).isTrue();
    }

    @Test
    void create_nameTaken_returnsFormWithError() {
        TeamCreateForm form = new TeamCreateForm();
        form.setName("SofiaFC");
        form.setCity("Sofia");
        for (int i = 1; i <= 6; i++) {
            PlayerRowDto row = new PlayerRowDto();
            row.setShirtNumber(i);
            row.setFirstName("First" + i);
            row.setLastName("Last" + i);
            form.getPlayers().add(row);
        }
        BindingResult br = new BeanPropertyBindingResult(form, "teamCreateForm");
        when(teamService.existsByNameAndCity("SofiaFC", "Sofia")).thenReturn(true);
        when(leagueService.findAll()).thenReturn(List.of());
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.create(form, br, null, model, auth, ra)).isEqualTo("teams/create");
        assertThat(br.hasFieldErrors("name")).isTrue();
    }

    @Test
    void edit_leagueStarted_returnsFormWithError() {
        UUID id = UUID.randomUUID();
        UUID newLeague = UUID.randomUUID();
        TeamDto current = team(id);
        current.setLeagueId(null);
        when(teamService.findById(id)).thenReturn(current);
        when(leagueService.hasLeagueStarted(newLeague)).thenReturn(true);
        when(leagueService.findAll()).thenReturn(List.of());
        TeamDto dto = team(id);
        dto.setLeagueId(newLeague);
        BindingResult br = new BeanPropertyBindingResult(dto, "teamDto");
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.edit(id, dto, br, null, model, auth, ra)).isEqualTo("teams/form");
        assertThat(br.hasFieldErrors("leagueId")).isTrue();
    }

    @Test
    void create_fromRequest_cancelsPending() {
        UUID reqId = UUID.randomUUID();
        TeamCreateForm form = new TeamCreateForm();
        form.setTeamId(UUID.randomUUID());
        BindingResult br = new BeanPropertyBindingResult(form, "teamCreateForm");
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(true);
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.create(form, br, reqId, model, auth, ra)).isEqualTo("redirect:/teams");
        org.mockito.Mockito.verify(changeRequestService).cancelIfPending(reqId, auth);
    }

    @Test
    void edit_sameLeague_skipsStartedCheckAndExecutes() {
        UUID id = UUID.randomUUID();
        UUID leagueId = UUID.randomUUID();
        TeamDto current = team(id);
        current.setLeagueId(leagueId);
        when(teamService.findById(id)).thenReturn(current);
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(true);
        TeamDto dto = team(id);
        dto.setLeagueId(leagueId);
        BindingResult br = new BeanPropertyBindingResult(dto, "teamDto");
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.edit(id, dto, br, null, model, auth, ra)).isEqualTo("redirect:/teams");
        org.mockito.Mockito.verify(leagueService, org.mockito.Mockito.never()).hasLeagueStarted(any());
    }

    @Test
    void createForm_fromSquadPayloadWithoutTeamId_fillsTwelveRows() {
        UUID reqId = UUID.randomUUID();
        TeamSquadPayload payload = new TeamSquadPayload();
        payload.setTeam(team(null));
        PlayerDto p = new PlayerDto();
        p.setShirtNumber(7);
        p.setFirstName("Ivan");
        p.setLastName("Petrov");
        payload.setPlayers(List.of(p));
        when(changeRequestService.getPayloadForResubmit(reqId, auth)).thenReturn(payload);
        when(leagueService.findAll()).thenReturn(List.of());
        Model model = new ExtendedModelMap();

        assertThat(controller.createForm(reqId, model, auth)).isEqualTo("teams/create");
        TeamCreateForm form = (TeamCreateForm) model.getAttribute("teamCreateForm");
        assertThat(form.getPlayers()).hasSize(12);
    }

    @Test
    void editForm_fromRequest_usesPayload() {
        UUID id = UUID.randomUUID();
        UUID reqId = UUID.randomUUID();
        when(changeRequestService.getPayloadForResubmit(reqId, auth)).thenReturn(team(id));
        when(leagueService.findAll()).thenReturn(List.of());
        Model model = new ExtendedModelMap();

        assertThat(controller.editForm(id, reqId, model, auth)).isEqualTo("teams/form");
        assertThat(model.getAttribute("fromRequest")).isEqualTo(reqId);
    }

    @Test
    void delete_notExecuted_redirectsWithApprovalMessage() {
        UUID id = UUID.randomUUID();
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(false);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.delete(id, auth, ra)).isEqualTo("redirect:/teams");
        assertThat(ra.getFlashAttributes().get("statusMessage")).isEqualTo("Submitted for admin approval.");
    }

    @Test
    void delete_executed_redirectsWithMessage() {
        UUID id = UUID.randomUUID();
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(true);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.delete(id, auth, ra)).isEqualTo("redirect:/teams");
        assertThat(ra.getFlashAttributes().get("statusMessage")).isEqualTo("Team deleted.");
    }

    private Authentication authWithSubs(String username, UUID userId) {
        User user = new User();
        user.setId(userId);
        when(userService.findByUsername(username)).thenReturn(user);
        Authentication a = mock(Authentication.class);
        when(a.getName()).thenReturn(username);
        return a;
    }

    private MatchDto sideMatch(UUID home, UUID away, LocalDateTime playedAt) {
        MatchDto m = new MatchDto();
        m.setId(UUID.randomUUID());
        m.setHomeTeamId(home);
        m.setAwayTeamId(away);
        m.setPlayedAt(playedAt);
        return m;
    }

    @Test
    void detail_authenticated_populatesSubscriptionsAndAllMatchSides() {
        UUID id = UUID.randomUUID();
        UUID leagueId = UUID.randomUUID();
        UUID otherTeamId = UUID.randomUUID();
        Authentication a = authWithSubs("bob", UUID.randomUUID());

        TeamDto t = team(id);
        t.setLeagueId(leagueId);
        when(teamService.findById(id)).thenReturn(t);
        when(playerService.findAllByTeam(id)).thenReturn(List.of());
        when(playerService.squadRemainingSlots(id)).thenReturn(5);

        LeagueDetailView league = mock(LeagueDetailView.class);
        MatchDto stale = new MatchDto();
        stale.setId(UUID.randomUUID());
        stale.setHomeTeamId(id);
        stale.setAwayTeamId(UUID.randomUUID());
        stale.setPlayedAt(LocalDateTime.now().minusHours(3));
        MatchDto live = new MatchDto();
        live.setId(UUID.randomUUID());
        live.setHomeTeamId(id);
        live.setAwayTeamId(UUID.randomUUID());
        live.setPlayedAt(LocalDateTime.now().minusMinutes(20));
        live.getGoalTimeline().add(new GoalDto());
        MatchDto futureLeagueMatch = new MatchDto();
        futureLeagueMatch.setId(UUID.randomUUID());
        futureLeagueMatch.setHomeTeamId(id);
        futureLeagueMatch.setAwayTeamId(UUID.randomUUID());
        futureLeagueMatch.setPlayedAt(LocalDateTime.now().plusDays(5));
        when(league.getStandings()).thenReturn(List.of());
        when(league.getMatches()).thenReturn(List.of(stale, live, futureLeagueMatch));
        when(leagueService.findDetail(leagueId)).thenReturn(league);

        UUID matchIdA = UUID.randomUUID();
        SubscriptionDto teamMatchSub = new SubscriptionDto();
        teamMatchSub.setId(UUID.randomUUID());
        teamMatchSub.setEntityType("TEAM");
        teamMatchSub.setEntityId(id);
        SubscriptionDto teamOtherSub = new SubscriptionDto();
        teamOtherSub.setId(UUID.randomUUID());
        teamOtherSub.setEntityType("TEAM");
        teamOtherSub.setEntityId(otherTeamId);
        SubscriptionDto matchSub = new SubscriptionDto();
        matchSub.setId(UUID.randomUUID());
        matchSub.setEntityType("MATCH");
        matchSub.setEntityId(matchIdA);
        when(notificationClient.getSubscriptions(any())).thenReturn(List.of(teamOtherSub, matchSub, teamMatchSub));

        MatchDto pastHome = sideMatch(id, UUID.randomUUID(), LocalDateTime.now().minusDays(1));
        MatchDto pastAway = sideMatch(UUID.randomUUID(), id, LocalDateTime.now().minusDays(2));
        MatchDto pastNeither = sideMatch(UUID.randomUUID(), UUID.randomUUID(), LocalDateTime.now().minusDays(3));
        MatchDto futureHome = sideMatch(id, UUID.randomUUID(), LocalDateTime.now().plusDays(1));
        MatchDto futureAway = sideMatch(UUID.randomUUID(), id, LocalDateTime.now().plusDays(2));
        MatchDto futureNeither = sideMatch(UUID.randomUUID(), UUID.randomUUID(), LocalDateTime.now().plusDays(3));
        when(matchService.findAll(any(Sort.class))).thenReturn(
                List.of(pastHome, pastAway, pastNeither, futureHome, futureAway, futureNeither));

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getQueryString()).thenReturn("tab=results");
        when(request.getRequestURI()).thenReturn("/teams/" + id);
        Model model = new ExtendedModelMap();

        assertThat(controller.detail(id, model, a, request)).isEqualTo("teams/detail");
        assertThat(model.getAttribute("followedSubscriptionId")).isNotNull();
        @SuppressWarnings("unchecked")
        java.util.Set<UUID> subscribedMatchIds = (java.util.Set<UUID>) model.getAttribute("subscribedMatchIds");
        assertThat(subscribedMatchIds).contains(matchIdA);
        assertThat((List<?>) model.getAttribute("teamResults")).hasSize(2);
        assertThat((List<?>) model.getAttribute("teamUpcoming")).hasSize(2);
        assertThat(model.getAttribute("playedMatchCount")).isEqualTo(1L);
    }

    @Test
    void detail_authenticated_subscriptionLookupFails_ignoresError() {
        UUID id = UUID.randomUUID();
        Authentication a = authWithSubs("bob", UUID.randomUUID());
        when(teamService.findById(id)).thenReturn(team(id));
        when(playerService.findAllByTeam(id)).thenReturn(List.of());
        when(playerService.squadRemainingSlots(id)).thenReturn(12);
        when(matchService.findAll(any(Sort.class))).thenReturn(List.of());
        when(notificationClient.getSubscriptions(any())).thenThrow(new RuntimeException("down"));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getQueryString()).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/teams/" + id);
        Model model = new ExtendedModelMap();

        assertThat(controller.detail(id, model, a, request)).isEqualTo("teams/detail");
        assertThat(model.getAttribute("followedSubscriptionId")).isNull();
    }

    @Test
    void list_eligibleFreeTeamsAboveThreshold_addsLeagueReadyCount() {
        List<TeamDto> free = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            TeamDto t = new TeamDto();
            t.setId(UUID.randomUUID());
            t.setName("T" + i);
            t.setPlayerCount(6);
            free.add(t);
        }
        TeamDto ineligible = new TeamDto();
        ineligible.setId(UUID.randomUUID());
        ineligible.setName("Small");
        ineligible.setPlayerCount(2);
        free.add(ineligible);
        when(teamService.findAll(any(Sort.class))).thenReturn(List.of());
        when(teamService.findAllFree()).thenReturn(free);
        Model model = new ExtendedModelMap();

        assertThat(controller.list("name", "desc", model)).isEqualTo("teams/list");
        assertThat(model.getAttribute("currentDir")).isEqualTo("desc");
        assertThat(model.getAttribute("leagueReadyCount")).isEqualTo(6L);
    }

    @Test
    void createForm_fromSquadPayload_marksExistingAndPayloadShirtNumbersTaken() {
        UUID reqId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        TeamSquadPayload payload = new TeamSquadPayload();
        payload.setTeam(team(teamId));
        PlayerDto withNumber = new PlayerDto();
        withNumber.setShirtNumber(7);
        withNumber.setFirstName("Ivan");
        withNumber.setLastName("Petrov");
        PlayerDto withoutNumber = new PlayerDto();
        withoutNumber.setFirstName("Petar");
        withoutNumber.setLastName("Georgiev");
        payload.setPlayers(List.of(withNumber, withoutNumber));
        when(changeRequestService.getPayloadForResubmit(reqId, auth)).thenReturn(payload);
        when(playerService.squadRemainingSlots(teamId)).thenReturn(4);

        PlayerDto existingWithNumber = new PlayerDto();
        existingWithNumber.setShirtNumber(9);
        PlayerDto existingWithoutNumber = new PlayerDto();
        when(playerService.findAllByTeam(teamId)).thenReturn(List.of(existingWithNumber, existingWithoutNumber));
        when(leagueService.findAll()).thenReturn(List.of());
        Model model = new ExtendedModelMap();

        assertThat(controller.createForm(reqId, model, auth)).isEqualTo("teams/create");
    }

    @Test
    void create_tooManyPlayers_rejectsCapacityError() {
        TeamCreateForm form = new TeamCreateForm();
        form.setName("SofiaFC");
        form.setCity("Sofia");
        for (int i = 1; i <= 13; i++) {
            PlayerRowDto row = new PlayerRowDto();
            row.setShirtNumber(i);
            row.setFirstName("First" + i);
            row.setLastName("Last" + i);
            form.getPlayers().add(row);
        }
        BindingResult br = new BeanPropertyBindingResult(form, "teamCreateForm");
        when(leagueService.findAll()).thenReturn(List.of());
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.create(form, br, null, model, auth, ra)).isEqualTo("teams/create");
        assertThat(br.hasErrors()).isTrue();
    }

    @Test
    void create_blankName_skipsDuplicateNameCheck() {
        TeamCreateForm form = new TeamCreateForm();
        form.setName("   ");
        form.setCity("Sofia");
        for (int i = 1; i <= 6; i++) {
            PlayerRowDto row = new PlayerRowDto();
            row.setShirtNumber(i);
            row.setFirstName("First" + i);
            row.setLastName("Last" + i);
            form.getPlayers().add(row);
        }
        BindingResult br = new BeanPropertyBindingResult(form, "teamCreateForm");
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(true);
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        controller.create(form, br, null, model, auth, ra);

        verify(teamService, never()).existsByNameAndCity(any(), any());
    }

    @Test
    void create_nullName_skipsDuplicateNameCheck() {
        TeamCreateForm form = new TeamCreateForm();
        form.setCity("Sofia");
        for (int i = 1; i <= 6; i++) {
            PlayerRowDto row = new PlayerRowDto();
            row.setShirtNumber(i);
            row.setFirstName("First" + i);
            row.setLastName("Last" + i);
            form.getPlayers().add(row);
        }
        BindingResult br = new BeanPropertyBindingResult(form, "teamCreateForm");
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(true);
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        controller.create(form, br, null, model, auth, ra);

        verify(teamService, never()).existsByNameAndCity(any(), any());
    }

    @Test
    void createForm_allShirtNumbersTaken_addsRowWithoutNumber() {
        UUID reqId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        TeamSquadPayload payload = new TeamSquadPayload();
        payload.setTeam(team(teamId));
        payload.setPlayers(List.of());
        when(changeRequestService.getPayloadForResubmit(reqId, auth)).thenReturn(payload);
        when(playerService.squadRemainingSlots(teamId)).thenReturn(1);
        List<PlayerDto> existing = new java.util.ArrayList<>();
        for (int i = 1; i <= 99; i++) {
            PlayerDto p = new PlayerDto();
            p.setShirtNumber(i);
            existing.add(p);
        }
        when(playerService.findAllByTeam(teamId)).thenReturn(existing);
        when(leagueService.findAll()).thenReturn(List.of());
        Model model = new ExtendedModelMap();

        assertThat(controller.createForm(reqId, model, auth)).isEqualTo("teams/create");
        TeamCreateForm form = (TeamCreateForm) model.getAttribute("teamCreateForm");
        assertThat(form.getPlayers()).hasSize(1);
        assertThat(form.getPlayers().get(0).getShirtNumber()).isNull();
    }

    @Test
    void create_leagueStarted_rejectsError() {
        UUID leagueId = UUID.randomUUID();
        TeamCreateForm form = new TeamCreateForm();
        form.setName("SofiaFC");
        form.setCity("Sofia");
        form.setLeagueId(leagueId);
        for (int i = 1; i <= 6; i++) {
            PlayerRowDto row = new PlayerRowDto();
            row.setShirtNumber(i);
            row.setFirstName("First" + i);
            row.setLastName("Last" + i);
            form.getPlayers().add(row);
        }
        BindingResult br = new BeanPropertyBindingResult(form, "teamCreateForm");
        when(teamService.existsByNameAndCity(any(), any())).thenReturn(false);
        when(leagueService.hasLeagueStarted(leagueId)).thenReturn(true);
        when(leagueService.findAll()).thenReturn(List.of());
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.create(form, br, null, model, auth, ra)).isEqualTo("teams/create");
        assertThat(br.hasFieldErrors("leagueId")).isTrue();
    }

    @Test
    void create_leagueNotStarted_executesSuccessfully() {
        UUID leagueId = UUID.randomUUID();
        TeamCreateForm form = new TeamCreateForm();
        form.setName("SofiaFC");
        form.setCity("Sofia");
        form.setLeagueId(leagueId);
        for (int i = 1; i <= 6; i++) {
            PlayerRowDto row = new PlayerRowDto();
            row.setShirtNumber(i);
            row.setFirstName("First" + i);
            row.setLastName("Last" + i);
            form.getPlayers().add(row);
        }
        BindingResult br = new BeanPropertyBindingResult(form, "teamCreateForm");
        when(teamService.existsByNameAndCity(any(), any())).thenReturn(false);
        when(leagueService.hasLeagueStarted(leagueId)).thenReturn(false);
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(true);
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.create(form, br, null, model, auth, ra)).isEqualTo("redirect:/teams");
        assertThat(br.hasFieldErrors("leagueId")).isFalse();
    }

    @Test
    void create_bindingErrors_withFromRequest_addsFromRequestToModel() {
        UUID reqId = UUID.randomUUID();
        TeamCreateForm form = new TeamCreateForm();
        form.setName("SofiaFC");
        form.setCity("Sofia");
        BindingResult br = new BeanPropertyBindingResult(form, "teamCreateForm");
        when(leagueService.findAll()).thenReturn(List.of());
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        controller.create(form, br, reqId, model, auth, ra);

        assertThat(model.getAttribute("fromRequest")).isEqualTo(reqId);
    }

    @Test
    void create_duplicateName_withFromRequest_addsFromRequestToModel() {
        UUID reqId = UUID.randomUUID();
        TeamCreateForm form = new TeamCreateForm();
        form.setTeamId(UUID.randomUUID());
        BindingResult br = new BeanPropertyBindingResult(form, "teamCreateForm");
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("dup"));
        when(leagueService.findAll()).thenReturn(List.of());
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        controller.create(form, br, reqId, model, auth, ra);

        assertThat(model.getAttribute("fromRequest")).isEqualTo(reqId);
    }

    @Test
    void create_valid_notExecuted_redirectsWithApprovalMessage() {
        TeamCreateForm form = new TeamCreateForm();
        form.setTeamId(UUID.randomUUID());
        BindingResult br = new BeanPropertyBindingResult(form, "teamCreateForm");
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(false);
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.create(form, br, null, model, auth, ra);

        assertThat(view).isEqualTo("redirect:/teams");
        assertThat(ra.getFlashAttributes().get("statusMessage")).isEqualTo("Submitted for admin approval.");
    }

    @Test
    void edit_differentLeagueNotStarted_executesSuccessfully() {
        UUID id = UUID.randomUUID();
        UUID currentLeague = UUID.randomUUID();
        UUID newLeague = UUID.randomUUID();
        TeamDto current = team(id);
        current.setLeagueId(currentLeague);
        when(teamService.findById(id)).thenReturn(current);
        when(leagueService.hasLeagueStarted(newLeague)).thenReturn(false);
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(true);
        TeamDto dto = team(id);
        dto.setLeagueId(newLeague);
        BindingResult br = new BeanPropertyBindingResult(dto, "teamDto");
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.edit(id, dto, br, null, model, auth, ra)).isEqualTo("redirect:/teams");
    }

    @Test
    void edit_bindingErrors_withFromRequest_addsFromRequestToModel() {
        UUID id = UUID.randomUUID();
        UUID reqId = UUID.randomUUID();
        UUID newLeague = UUID.randomUUID();
        TeamDto current = team(id);
        current.setLeagueId(null);
        when(teamService.findById(id)).thenReturn(current);
        when(leagueService.hasLeagueStarted(newLeague)).thenReturn(true);
        when(leagueService.findAll()).thenReturn(List.of());
        TeamDto dto = team(id);
        dto.setLeagueId(newLeague);
        BindingResult br = new BeanPropertyBindingResult(dto, "teamDto");
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        controller.edit(id, dto, br, reqId, model, auth, ra);

        assertThat(model.getAttribute("fromRequest")).isEqualTo(reqId);
    }

    @Test
    void edit_fromRequest_cancelsPending() {
        UUID id = UUID.randomUUID();
        UUID reqId = UUID.randomUUID();
        TeamDto dto = team(id);
        dto.setLeagueId(null);
        BindingResult br = new BeanPropertyBindingResult(dto, "teamDto");
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(true);
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.edit(id, dto, br, reqId, model, auth, ra)).isEqualTo("redirect:/teams");
        verify(changeRequestService).cancelIfPending(reqId, auth);
    }

    @Test
    void edit_valid_notExecuted_redirectsWithApprovalMessage() {
        UUID id = UUID.randomUUID();
        TeamDto dto = team(id);
        dto.setLeagueId(null);
        BindingResult br = new BeanPropertyBindingResult(dto, "teamDto");
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(false);
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.edit(id, dto, br, null, model, auth, ra);

        assertThat(view).isEqualTo("redirect:/teams");
        assertThat(ra.getFlashAttributes().get("statusMessage")).isEqualTo("Submitted for admin approval.");
    }

    @Test
    void liveSummary_anonymous_returnsLiveMatchAsUnfollowed() {
        UUID id = UUID.randomUUID();
        MatchDto live = sideMatch(id, UUID.randomUUID(), LocalDateTime.now().minusMinutes(10));
        when(matchService.findAll(any(Sort.class))).thenReturn(List.of(live));

        Map<String, Object> result = controller.liveSummary(id, null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = (List<Map<String, Object>>) result.get("matches");
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).get("followed")).isEqualTo(false);
    }

    @Test
    void liveSummary_matchWithLeagueId_includesLeagueIdInEntry() {
        UUID id = UUID.randomUUID();
        UUID leagueId = UUID.randomUUID();
        MatchDto live = sideMatch(id, UUID.randomUUID(), LocalDateTime.now().minusMinutes(10));
        live.setLeagueId(leagueId);
        when(matchService.findAll(any(Sort.class))).thenReturn(List.of(live));

        Map<String, Object> result = controller.liveSummary(id, null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = (List<Map<String, Object>>) result.get("matches");
        assertThat(matches.get(0).get("leagueId")).isEqualTo(leagueId.toString());
    }

    @Test
    void liveSummary_authenticated_marksFollowedMatch() {
        UUID id = UUID.randomUUID();
        Authentication a = authWithSubs("bob", UUID.randomUUID());
        MatchDto live = sideMatch(id, UUID.randomUUID(), LocalDateTime.now().minusMinutes(10));
        when(matchService.findAll(any(Sort.class))).thenReturn(List.of(live));

        SubscriptionDto matchSub = new SubscriptionDto();
        matchSub.setEntityType("MATCH");
        matchSub.setEntityId(live.getId());
        when(notificationClient.getSubscriptions(any())).thenReturn(List.of(matchSub));

        Map<String, Object> result = controller.liveSummary(id, a);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = (List<Map<String, Object>>) result.get("matches");
        assertThat(matches.get(0).get("followed")).isEqualTo(true);
    }

    @Test
    void liveSummary_authenticatedLookupFails_defaultsToUnfollowed() {
        UUID id = UUID.randomUUID();
        Authentication a = authWithSubs("bob", UUID.randomUUID());
        MatchDto live = sideMatch(id, UUID.randomUUID(), LocalDateTime.now().minusMinutes(10));
        when(matchService.findAll(any(Sort.class))).thenReturn(List.of(live));
        when(notificationClient.getSubscriptions(any())).thenThrow(new RuntimeException("down"));

        Map<String, Object> result = controller.liveSummary(id, a);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = (List<Map<String, Object>>) result.get("matches");
        assertThat(matches.get(0).get("followed")).isEqualTo(false);
    }

    @Test
    void liveSummary_awaySideMatch_isIncluded() {
        UUID id = UUID.randomUUID();
        MatchDto live = sideMatch(UUID.randomUUID(), id, LocalDateTime.now().minusMinutes(10));
        when(matchService.findAll(any(Sort.class))).thenReturn(List.of(live));

        Map<String, Object> result = controller.liveSummary(id, null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = (List<Map<String, Object>>) result.get("matches");
        assertThat(matches).hasSize(1);
    }

    @Test
    void liveSummary_excludesOtherTeamsAndOutOfWindowMatches() {
        UUID id = UUID.randomUUID();
        MatchDto notTeam = sideMatch(UUID.randomUUID(), UUID.randomUUID(), LocalDateTime.now().minusMinutes(10));
        MatchDto tooOld = sideMatch(id, UUID.randomUUID(), LocalDateTime.now().minusHours(2));
        MatchDto future = sideMatch(id, UUID.randomUUID(), LocalDateTime.now().plusHours(1));
        when(matchService.findAll(any(Sort.class))).thenReturn(List.of(notTeam, tooOld, future));

        Map<String, Object> result = controller.liveSummary(id, null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = (List<Map<String, Object>>) result.get("matches");
        assertThat(matches).isEmpty();
    }
}
