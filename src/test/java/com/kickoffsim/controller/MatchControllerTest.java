package com.kickoffsim.controller;

import com.kickoffsim.dto.GoalDto;
import com.kickoffsim.dto.GoalEventDto;
import com.kickoffsim.dto.LeagueDetailView;
import com.kickoffsim.dto.MatchDto;
import com.kickoffsim.dto.TeamDto;
import com.kickoffsim.exception.InvalidGoalException;
import com.kickoffsim.model.Half;
import com.kickoffsim.service.ChangeRequestService;
import com.kickoffsim.service.LeagueService;
import com.kickoffsim.service.MatchService;
import com.kickoffsim.service.PlayerService;
import com.kickoffsim.service.TeamService;
import com.kickoffsim.service.WeatherService;
import com.kickoffsim.web.MatchFollowSupport;
import com.kickoffsim.web.ViewerZone;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchControllerTest {

    @Mock private MatchService matchService;
    @Mock private TeamService teamService;
    @Mock private PlayerService playerService;
    @Mock private LeagueService leagueService;
    @Mock private ChangeRequestService changeRequestService;
    @Mock private MatchFollowSupport matchFollowSupport;
    @Mock private ViewerZone viewerZone;
    @Mock private WeatherService weatherService;
    @Spy private Clock clock = Clock.systemDefaultZone();

    @InjectMocks private MatchController controller;

    private final Authentication auth = mock(Authentication.class);

    private MatchDto matchWithTeams() {
        MatchDto m = new MatchDto();
        m.setId(UUID.randomUUID());
        m.setHomeTeamId(UUID.randomUUID());
        m.setAwayTeamId(UUID.randomUUID());
        m.setPlayedAt(LocalDateTime.now().plusDays(1));
        return m;
    }

    @Test
    void detail_notLive_returnsView() {
        UUID id = UUID.randomUUID();
        when(matchService.findById(id)).thenReturn(matchWithTeams());
        Model model = new ExtendedModelMap();

        assertThat(controller.detail(id, model)).isEqualTo("matches/detail");
        assertThat(model.getAttribute("match")).isNotNull();
    }

    @Test
    void list_noFilters_returnsView() {
        when(viewerZone.resolve(any())).thenReturn(ZoneId.of("Europe/Sofia"));
        when(viewerZone.today(any())).thenReturn(LocalDate.now());
        when(matchService.findAll(any(Sort.class))).thenReturn(List.of());
        when(matchService.findAllMatchUtcIsos()).thenReturn(List.of());
        when(leagueService.findAll()).thenReturn(List.of());
        when(matchFollowSupport.subscribedMatchIds(any())).thenReturn(Set.of());
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getQueryString()).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/matches");
        Model model = new ExtendedModelMap();

        assertThat(controller.list(null, null, null, auth, request, model)).isEqualTo("matches/list");
        assertThat(model.getAttribute("today")).isNotNull();
    }

    @Test
    void list_withLiveMatch_populatesLiveData() {
        when(viewerZone.resolve(any())).thenReturn(ZoneId.of("Europe/Sofia"));
        when(viewerZone.today(any())).thenReturn(LocalDate.now());
        when(viewerZone.dateOf(any(), any())).thenReturn(LocalDate.now());

        MatchDto live = new MatchDto();
        live.setId(UUID.randomUUID());
        live.setHomeTeamId(UUID.randomUUID());
        live.setAwayTeamId(UUID.randomUUID());
        live.setPlayedAt(LocalDateTime.now().minusMinutes(30));
        GoalDto g = new GoalDto();
        g.setMinute(5);
        g.setHalf(Half.FIRST);
        g.setHomeGoal(true);
        g.setRunningHomeScore(1);
        g.setRunningAwayScore(0);
        live.getGoalTimeline().add(g);

        when(matchService.findAll(any(Sort.class))).thenReturn(List.of(live));
        when(matchService.findAllMatchUtcIsos()).thenReturn(List.of());
        when(leagueService.findAll()).thenReturn(List.of());
        when(matchFollowSupport.subscribedMatchIds(any())).thenReturn(Set.of());
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getQueryString()).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/matches");
        Model model = new ExtendedModelMap();

        assertThat(controller.list(null, null, null, auth, request, model)).isEqualTo("matches/list");
        assertThat(model.getAttribute("liveMatchesForJs")).isNotNull();
    }

    @Test
    void create_valid_notExecuted_redirectsWithApprovalMessage() {
        MatchDto dto = new MatchDto();
        BindingResult br = new BeanPropertyBindingResult(dto, "matchDto");
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(false);
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.create(dto, br, null, model, auth, ra);

        assertThat(view).isEqualTo("redirect:/matches");
        assertThat(ra.getFlashAttributes().get("statusMessage")).isEqualTo("Submitted for admin approval.");
    }

    @Test
    void list_withLiveMatch_goalWithNullFields_appliesDefaults() {
        when(viewerZone.resolve(any())).thenReturn(ZoneId.of("Europe/Sofia"));
        when(viewerZone.today(any())).thenReturn(LocalDate.now());
        when(viewerZone.dateOf(any(), any())).thenReturn(LocalDate.now());

        MatchDto live = new MatchDto();
        live.setId(UUID.randomUUID());
        live.setHomeTeamId(UUID.randomUUID());
        live.setAwayTeamId(UUID.randomUUID());
        live.setPlayedAt(LocalDateTime.now().minusMinutes(20));
        live.getGoalTimeline().add(new GoalDto());

        when(matchService.findAll(any(Sort.class))).thenReturn(List.of(live));
        when(matchService.findAllMatchUtcIsos()).thenReturn(List.of());
        when(leagueService.findAll()).thenReturn(List.of());
        when(matchFollowSupport.subscribedMatchIds(any())).thenReturn(Set.of());
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getQueryString()).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/matches");
        Model model = new ExtendedModelMap();

        assertThat(controller.list(null, null, null, auth, request, model)).isEqualTo("matches/list");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> liveMatchesForJs = (List<Map<String, Object>>) model.getAttribute("liveMatchesForJs");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> goals = (List<Map<String, Object>>) liveMatchesForJs.get(0).get("goals");
        assertThat(goals.get(0).get("minute")).isEqualTo(0);
        assertThat(goals.get(0).get("half")).isEqualTo("FIRST");
        assertThat(goals.get(0).get("rh")).isEqualTo(0);
        assertThat(goals.get(0).get("ra")).isEqualTo(0);
    }

    @Test
    void createForm_new_returnsForm() {
        when(teamService.findAll()).thenReturn(List.of());
        Model model = new ExtendedModelMap();

        assertThat(controller.createForm(null, model, auth)).isEqualTo("matches/form");
        assertThat(model.getAttribute("matchDto")).isNotNull();
    }

    @Test
    void create_sameTeams_returnsFormWithError() {
        MatchDto dto = new MatchDto();
        UUID same = UUID.randomUUID();
        dto.setHomeTeamId(same);
        dto.setAwayTeamId(same);
        BindingResult br = new BeanPropertyBindingResult(dto, "matchDto");
        when(teamService.findAll()).thenReturn(List.of());
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.create(dto, br, null, model, auth, ra);

        assertThat(view).isEqualTo("matches/form");
        assertThat(br.hasErrors()).isTrue();
    }

    @Test
    void create_sameTeams_withFromRequest_addsFromRequestToModel() {
        MatchDto dto = new MatchDto();
        UUID same = UUID.randomUUID();
        UUID reqId = UUID.randomUUID();
        dto.setHomeTeamId(same);
        dto.setAwayTeamId(same);
        BindingResult br = new BeanPropertyBindingResult(dto, "matchDto");
        when(teamService.findAll()).thenReturn(List.of());
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.create(dto, br, reqId, model, auth, ra);

        assertThat(view).isEqualTo("matches/form");
        assertThat(model.getAttribute("fromRequest")).isEqualTo(reqId);
    }

    @Test
    void create_fromRequest_cancelsPending() {
        MatchDto dto = new MatchDto();
        UUID reqId = UUID.randomUUID();
        BindingResult br = new BeanPropertyBindingResult(dto, "matchDto");
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(true);
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.create(dto, br, reqId, model, auth, ra);

        assertThat(view).isEqualTo("redirect:/matches");
        verify(changeRequestService).cancelIfPending(reqId, auth);
    }

    @Test
    void create_valid_executed_redirects() {
        MatchDto dto = new MatchDto();
        BindingResult br = new BeanPropertyBindingResult(dto, "matchDto");
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(true);
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.create(dto, br, null, model, auth, ra);

        assertThat(view).isEqualTo("redirect:/matches");
        assertThat(ra.getFlashAttributes().get("statusMessage")).isEqualTo("Match created.");
    }

    @Test
    void editForm_notFromRequest_returnsForm() {
        UUID id = UUID.randomUUID();
        when(matchService.findById(id)).thenReturn(matchWithTeams());
        when(teamService.findAll()).thenReturn(List.of());
        Model model = new ExtendedModelMap();

        assertThat(controller.editForm(id, null, model, auth)).isEqualTo("matches/form");
        assertThat(model.getAttribute("matchDto")).isNotNull();
    }

    @Test
    void edit_valid_notExecuted_redirectsWithApproval() {
        UUID id = UUID.randomUUID();
        MatchDto dto = new MatchDto();
        BindingResult br = new BeanPropertyBindingResult(dto, "matchDto");
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(false);
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.edit(id, dto, br, null, model, auth, ra);

        assertThat(view).isEqualTo("redirect:/matches");
        assertThat(ra.getFlashAttributes().get("statusMessage")).isEqualTo("Submitted for admin approval.");
    }

    @Test
    void delete_executed_redirects() {
        UUID id = UUID.randomUUID();
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(true);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.delete(id, auth, ra)).isEqualTo("redirect:/matches");
        assertThat(ra.getFlashAttributes().get("statusMessage")).isEqualTo("Match deleted.");
    }

    @Test
    void addGoalForm_returnsView() {
        UUID id = UUID.randomUUID();
        when(matchService.findById(id)).thenReturn(matchWithTeams());
        when(playerService.findAllByTeam(any())).thenReturn(List.of());
        Model model = new ExtendedModelMap();

        assertThat(controller.addGoalForm(id, model)).isEqualTo("matches/goals/new");
        assertThat(model.getAttribute("goalEventDto")).isNotNull();
    }

    @Test
    void addGoal_valid_redirectsToMatch() {
        UUID id = UUID.randomUUID();
        GoalEventDto dto = new GoalEventDto();
        BindingResult br = new BeanPropertyBindingResult(dto, "goalEventDto");
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.addGoal(id, dto, br, model, ra);

        verify(matchService).addGoal(id, dto);
        assertThat(view).isEqualTo("redirect:/matches/" + id);
    }

    @Test
    void addGoal_invalidGoal_returnsFormWithError() {
        UUID id = UUID.randomUUID();
        GoalEventDto dto = new GoalEventDto();
        BindingResult br = new BeanPropertyBindingResult(dto, "goalEventDto");
        when(matchService.findById(id)).thenReturn(matchWithTeams());
        when(playerService.findAllByTeam(any())).thenReturn(List.of());
        doThrow(new InvalidGoalException("bad goal")).when(matchService).addGoal(id, dto);
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.addGoal(id, dto, br, model, ra);

        assertThat(view).isEqualTo("matches/goals/new");
        assertThat(model.getAttribute("errorMessage")).isEqualTo("bad goal");
    }

    @Test
    void editGoalForm_returnsView() {
        UUID id = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        GoalDto goal = new GoalDto();
        goal.setScorerId(UUID.randomUUID());
        goal.setMinute(10);
        goal.setHalf(Half.FIRST);
        when(matchService.findGoalById(goalId)).thenReturn(goal);
        when(matchService.findById(id)).thenReturn(matchWithTeams());
        when(playerService.findAllByTeam(any())).thenReturn(List.of());
        Model model = new ExtendedModelMap();

        assertThat(controller.editGoalForm(id, goalId, model)).isEqualTo("matches/goals/edit");
        assertThat(model.getAttribute("goalId")).isEqualTo(goalId);
    }

    @Test
    void list_withDateFilter_sortsByStatus() {
        when(viewerZone.resolve(any())).thenReturn(ZoneId.of("Europe/Sofia"));
        when(viewerZone.today(any())).thenReturn(LocalDate.now());
        when(viewerZone.dateOf(any(), any())).thenReturn(LocalDate.now());
        MatchDto past = matchWithTeams();
        past.setPlayedAt(LocalDateTime.now().minusDays(1));
        MatchDto live = matchWithTeams();
        live.setPlayedAt(LocalDateTime.now().minusMinutes(30));
        MatchDto upcoming = matchWithTeams();
        upcoming.setPlayedAt(LocalDateTime.now().plusHours(1));
        when(matchService.findAll(any(Sort.class))).thenReturn(List.of(past, live, upcoming));
        when(matchService.findAllMatchUtcIsos()).thenReturn(List.of());
        when(leagueService.findAll()).thenReturn(List.of());
        when(matchFollowSupport.subscribedMatchIds(any())).thenReturn(Set.of());
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getQueryString()).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/matches");
        Model model = new ExtendedModelMap();

        assertThat(controller.list(null, LocalDate.now(), null, auth, request, model)).isEqualTo("matches/list");
        assertThat((List<?>) model.getAttribute("dateMatches")).hasSize(3);
    }

    @Test
    void list_withLeagueAndTeamFilter_addsTeamContext() {
        UUID leagueId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        when(viewerZone.resolve(any())).thenReturn(ZoneId.of("Europe/Sofia"));
        when(viewerZone.today(any())).thenReturn(LocalDate.now());
        when(viewerZone.dateOf(any(), any())).thenReturn(LocalDate.now());
        MatchDto m = matchWithTeams();
        m.setHomeTeamId(teamId);
        when(matchService.findByLeague(leagueId)).thenReturn(List.of(m));
        when(leagueService.findAll()).thenReturn(List.of());
        when(matchFollowSupport.subscribedMatchIds(any())).thenReturn(Set.of());
        TeamDto t = new TeamDto();
        t.setId(teamId);
        t.setName("SofiaFC");
        t.setCity("Sofia");
        t.setLeagueId(leagueId);
        when(teamService.findById(teamId)).thenReturn(t);
        LeagueDetailView detail = mock(LeagueDetailView.class);
        when(detail.getStandings()).thenReturn(List.of());
        when(detail.getName()).thenReturn("Premier");
        when(leagueService.findDetail(leagueId)).thenReturn(detail);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getQueryString()).thenReturn("league=" + leagueId);
        when(request.getRequestURI()).thenReturn("/matches");
        Model model = new ExtendedModelMap();

        assertThat(controller.list(leagueId, null, teamId, auth, request, model)).isEqualTo("matches/list");
        assertThat(model.getAttribute("selectedTeamName")).isEqualTo("SofiaFC (Sofia)");
        assertThat(model.getAttribute("teamLeagueName")).isEqualTo("Premier");
    }

    @Test
    void createForm_fromRequest_prefillsFromPayload() {
        UUID reqId = UUID.randomUUID();
        MatchDto payload = new MatchDto();
        when(changeRequestService.getPayloadForResubmit(reqId, auth)).thenReturn(payload);
        when(teamService.findAll()).thenReturn(List.of());
        Model model = new ExtendedModelMap();

        assertThat(controller.createForm(reqId, model, auth)).isEqualTo("matches/form");
        assertThat(model.getAttribute("fromRequest")).isEqualTo(reqId);
    }

    @Test
    void editForm_fromRequest_usesPayload() {
        UUID id = UUID.randomUUID();
        UUID reqId = UUID.randomUUID();
        when(changeRequestService.getPayloadForResubmit(reqId, auth)).thenReturn(new MatchDto());
        when(teamService.findAll()).thenReturn(List.of());
        Model model = new ExtendedModelMap();

        assertThat(controller.editForm(id, reqId, model, auth)).isEqualTo("matches/form");
        assertThat(model.getAttribute("fromRequest")).isEqualTo(reqId);
    }

    @Test
    void edit_fromRequest_cancelsPending() {
        UUID id = UUID.randomUUID();
        UUID reqId = UUID.randomUUID();
        MatchDto dto = new MatchDto();
        BindingResult br = new BeanPropertyBindingResult(dto, "matchDto");
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(true);
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.edit(id, dto, br, reqId, model, auth, ra)).isEqualTo("redirect:/matches");
        verify(changeRequestService).cancelIfPending(reqId, auth);
    }

    @Test
    void addGoal_bindingErrors_returnsForm() {
        UUID id = UUID.randomUUID();
        GoalEventDto dto = new GoalEventDto();
        BindingResult br = new BeanPropertyBindingResult(dto, "goalEventDto");
        br.reject("err", "bad");
        when(matchService.findById(id)).thenReturn(matchWithTeams());
        when(playerService.findAllByTeam(any())).thenReturn(List.of());
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.addGoal(id, dto, br, model, ra)).isEqualTo("matches/goals/new");
    }

    @Test
    void editGoal_valid_redirectsToMatch() {
        UUID id = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        GoalEventDto dto = new GoalEventDto();
        BindingResult br = new BeanPropertyBindingResult(dto, "goalEventDto");
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.editGoal(id, goalId, dto, br, model, ra);

        verify(matchService).updateGoal(goalId, dto);
        assertThat(view).isEqualTo("redirect:/matches/" + id);
    }

    @Test
    void editGoal_invalidGoal_returnsFormWithError() {
        UUID id = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        GoalEventDto dto = new GoalEventDto();
        BindingResult br = new BeanPropertyBindingResult(dto, "goalEventDto");
        when(matchService.findById(id)).thenReturn(matchWithTeams());
        when(playerService.findAllByTeam(any())).thenReturn(List.of());
        doThrow(new InvalidGoalException("bad")).when(matchService).updateGoal(goalId, dto);
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.editGoal(id, goalId, dto, br, model, ra)).isEqualTo("matches/goals/edit");
        assertThat(model.getAttribute("errorMessage")).isEqualTo("bad");
    }

    @Test
    void editGoal_bindingErrors_returnsForm() {
        UUID id = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        GoalEventDto dto = new GoalEventDto();
        BindingResult br = new BeanPropertyBindingResult(dto, "goalEventDto");
        br.reject("err", "bad");
        when(matchService.findById(id)).thenReturn(matchWithTeams());
        when(playerService.findAllByTeam(any())).thenReturn(List.of());
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.editGoal(id, goalId, dto, br, model, ra)).isEqualTo("matches/goals/edit");
    }

    @Test
    void detail_liveMatch_populatesLiveJs() {
        UUID id = UUID.randomUUID();
        MatchDto m = matchWithTeams();
        m.setPlayedAt(LocalDateTime.now().minusMinutes(30));
        GoalDto g = new GoalDto();
        g.setMinute(5);
        g.setHalf(Half.FIRST);
        g.setRunningHomeScore(1);
        g.setRunningAwayScore(0);
        m.getGoalTimeline().add(g);
        when(matchService.findById(id)).thenReturn(m);
        Model model = new ExtendedModelMap();

        assertThat(controller.detail(id, model)).isEqualTo("matches/detail");
        assertThat(model.getAttribute("liveMatchForJs")).isNotNull();
    }

    @Test
    void deleteGoal_redirectsToMatch() {
        UUID id = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.deleteGoal(id, goalId, ra);

        verify(matchService).deleteGoal(goalId);
        assertThat(view).isEqualTo("redirect:/matches/" + id);
    }

    @Test
    void detail_playedLongAgo_notLive_returnsView() {
        UUID id = UUID.randomUUID();
        MatchDto m = matchWithTeams();
        m.setPlayedAt(LocalDateTime.now().minusHours(2));
        when(matchService.findById(id)).thenReturn(m);
        Model model = new ExtendedModelMap();

        assertThat(controller.detail(id, model)).isEqualTo("matches/detail");
        assertThat(model.getAttribute("liveMatchForJs")).isNull();
    }

    @Test
    void detail_liveMatch_goalWithNullFields_appliesDefaults() {
        UUID id = UUID.randomUUID();
        MatchDto m = matchWithTeams();
        m.setPlayedAt(LocalDateTime.now().minusMinutes(20));
        m.getGoalTimeline().add(new GoalDto());
        when(matchService.findById(id)).thenReturn(m);
        Model model = new ExtendedModelMap();

        assertThat(controller.detail(id, model)).isEqualTo("matches/detail");
        @SuppressWarnings("unchecked")
        Map<String, Object> live = (Map<String, Object>) model.getAttribute("liveMatchForJs");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> goals = (List<Map<String, Object>>) live.get("goals");
        assertThat(goals.get(0).get("minute")).isEqualTo(0);
        assertThat(goals.get(0).get("half")).isEqualTo("FIRST");
        assertThat(goals.get(0).get("rh")).isEqualTo(0);
        assertThat(goals.get(0).get("ra")).isEqualTo(0);
    }

    @Test
    void list_teamFilter_matchesViaHomeOrAwayExcludesNeither() {
        UUID team = UUID.randomUUID();
        when(viewerZone.resolve(any())).thenReturn(ZoneId.of("Europe/Sofia"));
        when(viewerZone.today(any())).thenReturn(LocalDate.now());
        when(viewerZone.dateOf(any(), any()))
                .thenAnswer(inv -> ((LocalDateTime) inv.getArgument(0)).toLocalDate());

        MatchDto mHome = matchWithTeams();
        mHome.setHomeTeamId(team);
        mHome.setPlayedAt(LocalDateTime.now().plusDays(10));

        MatchDto mAway = matchWithTeams();
        mAway.setAwayTeamId(team);
        mAway.setPlayedAt(LocalDateTime.now().plusDays(11));

        MatchDto mNeither = matchWithTeams();
        mNeither.setPlayedAt(LocalDateTime.now().plusDays(12));

        when(matchService.findAll(any(Sort.class))).thenReturn(List.of(mHome, mAway, mNeither));
        when(leagueService.findAll()).thenReturn(List.of());
        when(matchFollowSupport.subscribedMatchIds(any())).thenReturn(Set.of());
        TeamDto t = new TeamDto();
        t.setId(team);
        t.setName("Filtered");
        when(teamService.findById(team)).thenReturn(t);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getQueryString()).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/matches");
        Model model = new ExtendedModelMap();

        assertThat(controller.list(null, null, team, auth, request, model)).isEqualTo("matches/list");
        assertThat((List<?>) model.getAttribute("matchDates")).hasSize(2);
    }

    @Test
    void list_teamOnlyFilter_noCityNoLeague_computesUtcIsosFromFiltered() {
        UUID team = UUID.randomUUID();
        when(viewerZone.resolve(any())).thenReturn(ZoneId.of("Europe/Sofia"));
        when(viewerZone.today(any())).thenReturn(LocalDate.now());
        when(viewerZone.dateOf(any(), any())).thenReturn(LocalDate.now());
        MatchDto m = matchWithTeams();
        m.setHomeTeamId(team);
        when(matchService.findAll(any(Sort.class))).thenReturn(List.of(m));
        when(leagueService.findAll()).thenReturn(List.of());
        when(matchFollowSupport.subscribedMatchIds(any())).thenReturn(Set.of());
        TeamDto t = new TeamDto();
        t.setId(team);
        t.setName("NoCity");
        t.setCity(null);
        t.setLeagueId(null);
        when(teamService.findById(team)).thenReturn(t);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getQueryString()).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/matches");
        Model model = new ExtendedModelMap();

        assertThat(controller.list(null, null, team, auth, request, model)).isEqualTo("matches/list");
        assertThat(model.getAttribute("selectedTeamName")).isEqualTo("NoCity");
        assertThat(model.getAttribute("teamStandings")).isNull();
        assertThat((List<?>) model.getAttribute("allMatchUtcIsos")).hasSize(1);
    }

    @Test
    void list_noDateFilter_mixedMatches_populatesRecentUpcomingAndHasTodayResults() {
        when(viewerZone.resolve(any())).thenReturn(ZoneId.of("Europe/Sofia"));
        when(viewerZone.today(any())).thenReturn(LocalDate.now());
        when(viewerZone.dateOf(any(), any()))
                .thenAnswer(inv -> ((LocalDateTime) inv.getArgument(0)).toLocalDate());

        MatchDto longAgoToday = matchWithTeams();
        longAgoToday.setPlayedAt(LocalDateTime.now().minusHours(2));
        MatchDto liveNow = matchWithTeams();
        liveNow.setPlayedAt(LocalDateTime.now().minusMinutes(20));
        MatchDto future = matchWithTeams();
        future.setPlayedAt(LocalDateTime.now().plusHours(2));
        MatchDto pastOtherDay = matchWithTeams();
        pastOtherDay.setPlayedAt(LocalDateTime.now().minusDays(3));

        when(matchService.findAll(any(Sort.class)))
                .thenReturn(List.of(longAgoToday, liveNow, future, pastOtherDay));
        when(matchService.findAllMatchUtcIsos()).thenReturn(List.of());
        when(leagueService.findAll()).thenReturn(List.of());
        when(matchFollowSupport.subscribedMatchIds(any())).thenReturn(Set.of());
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getQueryString()).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/matches");
        Model model = new ExtendedModelMap();

        assertThat(controller.list(null, null, null, auth, request, model)).isEqualTo("matches/list");
        assertThat((List<?>) model.getAttribute("recentMatches")).hasSize(2);
        assertThat((List<?>) model.getAttribute("upcomingMatches")).hasSize(1);
        assertThat(model.getAttribute("hasTodayResults")).isEqualTo(true);
    }

    @Test
    void list_dateFilter_tiedPriorities_sortsDescendingPastAscendingUpcoming() {
        LocalDateTime anchor = LocalDate.now().atTime(LocalTime.NOON);
        Clock fixedClock = Clock.fixed(anchor.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        MatchController fixedController = new MatchController(matchService, teamService, playerService,
                leagueService, changeRequestService, matchFollowSupport, viewerZone, fixedClock, weatherService);

        when(viewerZone.resolve(any())).thenReturn(ZoneId.of("Europe/Sofia"));
        when(viewerZone.today(any())).thenReturn(anchor.toLocalDate());
        when(viewerZone.dateOf(any(), any()))
                .thenAnswer(inv -> ((LocalDateTime) inv.getArgument(0)).toLocalDate());

        MatchDto live = matchWithTeams();
        live.setPlayedAt(anchor.minusMinutes(20));
        MatchDto recent1 = matchWithTeams();
        recent1.setPlayedAt(anchor.minusHours(3));
        MatchDto recent2 = matchWithTeams();
        recent2.setPlayedAt(anchor.minusHours(4));
        MatchDto upcoming1 = matchWithTeams();
        upcoming1.setPlayedAt(anchor.plusHours(2));
        MatchDto upcoming2 = matchWithTeams();
        upcoming2.setPlayedAt(anchor.plusHours(3));
        MatchDto otherDay = matchWithTeams();
        otherDay.setPlayedAt(anchor.minusDays(5));

        when(matchService.findAll(any(Sort.class)))
                .thenReturn(List.of(live, recent1, recent2, upcoming1, upcoming2, otherDay));
        when(matchService.findAllMatchUtcIsos()).thenReturn(List.of());
        when(leagueService.findAll()).thenReturn(List.of());
        when(matchFollowSupport.subscribedMatchIds(any())).thenReturn(Set.of());
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getQueryString()).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/matches");
        Model model = new ExtendedModelMap();

        assertThat(fixedController.list(null, anchor.toLocalDate(), null, auth, request, model)).isEqualTo("matches/list");
        @SuppressWarnings("unchecked")
        List<MatchDto> dateMatches = (List<MatchDto>) model.getAttribute("dateMatches");
        assertThat(dateMatches).hasSize(5);
        assertThat(dateMatches.stream().map(MatchDto::getId).toList())
                .containsExactly(live.getId(), upcoming1.getId(), upcoming2.getId(),
                        recent1.getId(), recent2.getId());
    }

    @Test
    void create_onlyHomeTeamId_bypassesTeamValidation() {
        MatchDto dto = new MatchDto();
        dto.setHomeTeamId(UUID.randomUUID());
        BindingResult br = new BeanPropertyBindingResult(dto, "matchDto");
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(true);
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.create(dto, br, null, model, auth, ra);

        assertThat(view).isEqualTo("redirect:/matches");
        assertThat(br.hasErrors()).isFalse();
    }

    @Test
    void create_differentTeamsSameLeague_executesSuccessfully() {
        UUID leagueId = UUID.randomUUID();
        UUID homeId = UUID.randomUUID();
        UUID awayId = UUID.randomUUID();
        MatchDto dto = new MatchDto();
        dto.setHomeTeamId(homeId);
        dto.setAwayTeamId(awayId);
        BindingResult br = new BeanPropertyBindingResult(dto, "matchDto");
        TeamDto home = new TeamDto();
        home.setId(homeId);
        home.setLeagueId(leagueId);
        TeamDto away = new TeamDto();
        away.setId(awayId);
        away.setLeagueId(leagueId);
        when(teamService.findById(homeId)).thenReturn(home);
        when(teamService.findById(awayId)).thenReturn(away);
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(true);
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.create(dto, br, null, model, auth, ra);

        assertThat(view).isEqualTo("redirect:/matches");
        assertThat(br.hasErrors()).isFalse();
    }

    @Test
    void create_differentTeamsDifferentLeagues_rejectsError() {
        UUID homeId = UUID.randomUUID();
        UUID awayId = UUID.randomUUID();
        MatchDto dto = new MatchDto();
        dto.setHomeTeamId(homeId);
        dto.setAwayTeamId(awayId);
        BindingResult br = new BeanPropertyBindingResult(dto, "matchDto");
        TeamDto home = new TeamDto();
        home.setId(homeId);
        home.setLeagueId(UUID.randomUUID());
        TeamDto away = new TeamDto();
        away.setId(awayId);
        away.setLeagueId(UUID.randomUUID());
        when(teamService.findById(homeId)).thenReturn(home);
        when(teamService.findById(awayId)).thenReturn(away);
        when(teamService.findAll()).thenReturn(List.of());
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.create(dto, br, null, model, auth, ra);

        assertThat(view).isEqualTo("matches/form");
        assertThat(br.hasFieldErrors("awayTeamId")).isTrue();
    }

    @Test
    void edit_sameTeams_fromRequestNull_returnsFormWithError() {
        UUID id = UUID.randomUUID();
        UUID same = UUID.randomUUID();
        MatchDto dto = new MatchDto();
        dto.setHomeTeamId(same);
        dto.setAwayTeamId(same);
        BindingResult br = new BeanPropertyBindingResult(dto, "matchDto");
        when(teamService.findAll()).thenReturn(List.of());
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.edit(id, dto, br, null, model, auth, ra);

        assertThat(view).isEqualTo("matches/form");
        assertThat(model.getAttribute("fromRequest")).isNull();
    }

    @Test
    void edit_sameTeams_fromRequestSet_addsFromRequestToModel() {
        UUID id = UUID.randomUUID();
        UUID reqId = UUID.randomUUID();
        UUID same = UUID.randomUUID();
        MatchDto dto = new MatchDto();
        dto.setHomeTeamId(same);
        dto.setAwayTeamId(same);
        BindingResult br = new BeanPropertyBindingResult(dto, "matchDto");
        when(teamService.findAll()).thenReturn(List.of());
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.edit(id, dto, br, reqId, model, auth, ra);

        assertThat(view).isEqualTo("matches/form");
        assertThat(model.getAttribute("fromRequest")).isEqualTo(reqId);
    }

    @Test
    void delete_notExecuted_redirectsWithApprovalMessage() {
        UUID id = UUID.randomUUID();
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(false);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.delete(id, auth, ra)).isEqualTo("redirect:/matches");
        assertThat(ra.getFlashAttributes().get("statusMessage")).isEqualTo("Submitted for admin approval.");
    }

    @Test
    void editGoalForm_minuteNull_skipsFullMinuteCalculation() {
        UUID id = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        GoalDto goal = new GoalDto();
        goal.setScorerId(UUID.randomUUID());
        goal.setHalf(Half.FIRST);
        when(matchService.findGoalById(goalId)).thenReturn(goal);
        when(matchService.findById(id)).thenReturn(matchWithTeams());
        when(playerService.findAllByTeam(any())).thenReturn(List.of());
        Model model = new ExtendedModelMap();

        assertThat(controller.editGoalForm(id, goalId, model)).isEqualTo("matches/goals/edit");
        GoalEventDto form = (GoalEventDto) model.getAttribute("goalEventDto");
        assertThat(form.getMinute()).isNull();
    }

    @Test
    void editGoalForm_halfNull_skipsFullMinuteCalculation() {
        UUID id = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        GoalDto goal = new GoalDto();
        goal.setScorerId(UUID.randomUUID());
        goal.setMinute(10);
        when(matchService.findGoalById(goalId)).thenReturn(goal);
        when(matchService.findById(id)).thenReturn(matchWithTeams());
        when(playerService.findAllByTeam(any())).thenReturn(List.of());
        Model model = new ExtendedModelMap();

        assertThat(controller.editGoalForm(id, goalId, model)).isEqualTo("matches/goals/edit");
        GoalEventDto form = (GoalEventDto) model.getAttribute("goalEventDto");
        assertThat(form.getMinute()).isNull();
    }

    @Test
    void liveSummary_noFilters_noLiveMatches_returnsEmpty() {
        when(matchService.findAll(any(Sort.class))).thenReturn(List.of());
        when(matchFollowSupport.subscribedMatchIds(auth)).thenReturn(Set.of());

        Map<String, Object> result = controller.liveSummary(null, null, auth);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = (List<Map<String, Object>>) result.get("matches");
        assertThat(matches).isEmpty();
    }

    @Test
    void liveSummary_leagueFilter_liveMatch_includesLeagueFieldsAndFollowed() {
        UUID leagueId = UUID.randomUUID();
        MatchDto live = matchWithTeams();
        live.setPlayedAt(LocalDateTime.now().minusMinutes(10));
        live.setLeagueId(leagueId);
        live.setLeagueName("Premier");
        live.setRoundNumber(2);
        live.setHomeTeamName("Home");
        live.setHomeTeamCity("HCity");
        live.setAwayTeamName("Away");
        live.setAwayTeamCity("ACity");
        when(matchService.findByLeague(leagueId)).thenReturn(List.of(live));
        when(matchFollowSupport.subscribedMatchIds(auth)).thenReturn(Set.of(live.getId()));

        Map<String, Object> result = controller.liveSummary(leagueId, null, auth);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = (List<Map<String, Object>>) result.get("matches");
        assertThat(matches).hasSize(1);
        Map<String, Object> entry = matches.get(0);
        assertThat(entry.get("leagueId")).isEqualTo(leagueId.toString());
        assertThat(entry.get("leagueName")).isEqualTo("Premier");
        assertThat(entry.get("homeTeamName")).isEqualTo("Home");
        assertThat(entry.get("followed")).isEqualTo(true);
    }

    @Test
    void liveSummary_teamFilter_excludesNonMatchingAndOutOfWindowMatches() {
        UUID team = UUID.randomUUID();
        MatchDto liveHome = matchWithTeams();
        liveHome.setHomeTeamId(team);
        liveHome.setPlayedAt(LocalDateTime.now().minusMinutes(10));
        MatchDto liveAway = matchWithTeams();
        liveAway.setAwayTeamId(team);
        liveAway.setPlayedAt(LocalDateTime.now().minusMinutes(15));
        MatchDto notTeam = matchWithTeams();
        notTeam.setPlayedAt(LocalDateTime.now().minusMinutes(10));
        MatchDto tooOld = matchWithTeams();
        tooOld.setHomeTeamId(team);
        tooOld.setPlayedAt(LocalDateTime.now().minusHours(2));
        MatchDto future = matchWithTeams();
        future.setHomeTeamId(team);
        future.setPlayedAt(LocalDateTime.now().plusHours(1));

        when(matchService.findAll(any(Sort.class)))
                .thenReturn(List.of(liveHome, liveAway, notTeam, tooOld, future));
        when(matchFollowSupport.subscribedMatchIds(auth)).thenReturn(Set.of());

        Map<String, Object> result = controller.liveSummary(null, team, auth);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = (List<Map<String, Object>>) result.get("matches");
        assertThat(matches).extracting(m -> m.get("id"))
                .containsExactlyInAnyOrder(liveHome.getId().toString(), liveAway.getId().toString());
    }

    @Test
    void liveSummary_matchWithNullLeagueId_leagueIdIsNullInEntry() {
        MatchDto live = matchWithTeams();
        live.setPlayedAt(LocalDateTime.now().minusMinutes(10));
        live.setLeagueId(null);
        when(matchService.findAll(any(Sort.class))).thenReturn(List.of(live));
        when(matchFollowSupport.subscribedMatchIds(auth)).thenReturn(Set.of());

        Map<String, Object> result = controller.liveSummary(null, null, auth);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = (List<Map<String, Object>>) result.get("matches");
        assertThat(matches.get(0).get("leagueId")).isNull();
        assertThat(matches.get(0).get("followed")).isEqualTo(false);
    }

    @Test
    void editGoalForm_secondHalf_addsTwentyMinutes() {
        UUID id = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        GoalDto goal = new GoalDto();
        goal.setScorerId(UUID.randomUUID());
        goal.setMinute(10);
        goal.setHalf(Half.SECOND);
        when(matchService.findGoalById(goalId)).thenReturn(goal);
        when(matchService.findById(id)).thenReturn(matchWithTeams());
        when(playerService.findAllByTeam(any())).thenReturn(List.of());
        Model model = new ExtendedModelMap();

        assertThat(controller.editGoalForm(id, goalId, model)).isEqualTo("matches/goals/edit");
        GoalEventDto form = (GoalEventDto) model.getAttribute("goalEventDto");
        assertThat(form.getMinute()).isEqualTo(30);
    }
}
