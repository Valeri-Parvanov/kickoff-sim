package bg.softuni.footballleague.controller;

import bg.softuni.footballleague.dto.GoalDto;
import bg.softuni.footballleague.dto.LeagueDetailView;
import bg.softuni.footballleague.dto.LeagueDto;
import bg.softuni.footballleague.dto.MatchDto;
import bg.softuni.footballleague.dto.ScheduleForm;
import bg.softuni.footballleague.dto.TeamDto;
import bg.softuni.footballleague.exception.InvalidLeagueOperationException;
import bg.softuni.footballleague.model.Half;
import bg.softuni.footballleague.service.ChangeRequestService;
import bg.softuni.footballleague.service.LeagueService;
import bg.softuni.footballleague.service.ScheduleService;
import bg.softuni.footballleague.service.TeamService;
import bg.softuni.footballleague.web.MatchFollowSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeagueControllerTest {

    @Mock private LeagueService leagueService;
    @Mock private TeamService teamService;
    @Mock private ChangeRequestService changeRequestService;
    @Mock private ScheduleService scheduleService;
    @Mock private MatchFollowSupport matchFollowSupport;

    @InjectMocks private LeagueController controller;

    private final Authentication auth = mock(Authentication.class);

    private TeamDto eligibleTeam() {
        TeamDto t = new TeamDto();
        t.setId(UUID.randomUUID());
        t.setName("Team" + UUID.randomUUID());
        t.setPlayerCount(6);
        return t;
    }

    private TeamDto ineligibleTeam() {
        TeamDto t = new TeamDto();
        t.setId(UUID.randomUUID());
        t.setName("Team" + UUID.randomUUID());
        t.setPlayerCount(3);
        return t;
    }

    @Test
    void list_returnsViewWithLeaguesAndCounts() {
        when(leagueService.findAll(any(Sort.class))).thenReturn(List.of());
        when(teamService.findAllFree()).thenReturn(List.of());
        Model model = new ExtendedModelMap();

        assertThat(controller.list(null, null, model)).isEqualTo("leagues/list");
        assertThat(model.getAttribute("leagues")).isNotNull();
        assertThat(model.getAttribute("currentDir")).isEqualTo("asc");
    }

    @Test
    void createForm_notEnoughEligible_redirectsToTeamsForm() {
        when(teamService.findAllFree()).thenReturn(List.of());
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.createForm(null, model, auth, ra)).isEqualTo("redirect:/teams/form");
        assertThat(ra.getFlashAttributes()).containsKey("warnMessage");
    }

    @Test
    void createForm_enoughEligible_showsForm() {
        List<TeamDto> teams = new ArrayList<>();
        for (int i = 0; i < 6; i++) teams.add(eligibleTeam());
        when(teamService.findAllFree()).thenReturn(teams);
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.createForm(null, model, auth, ra)).isEqualTo("leagues/form");
        assertThat(model.getAttribute("availableTeams")).isNotNull();
    }

    @Test
    void create_missingNameAndTeams_returnsFormWithErrors() {
        LeagueDto dto = new LeagueDto();
        BindingResult br = new BeanPropertyBindingResult(dto, "leagueDto");
        when(teamService.findAllFree()).thenReturn(List.of());
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.create(dto, br, null, auth, ra, model);

        assertThat(view).isEqualTo("leagues/form");
        assertThat(br.hasErrors()).isTrue();
    }

    @Test
    void create_valid_notExecuted_redirectsWithApprovalMessage() {
        LeagueDto dto = new LeagueDto();
        dto.setName("Cup");
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 6; i++) ids.add(UUID.randomUUID());
        dto.setTeamIds(ids);
        BindingResult br = new BeanPropertyBindingResult(dto, "leagueDto");
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(false);
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.create(dto, br, null, auth, ra, model);

        assertThat(view).isEqualTo("redirect:/leagues");
        assertThat(ra.getFlashAttributes().get("statusMessage")).isEqualTo("Submitted for admin approval.");
    }

    @Test
    void delete_executed_redirectsWithMessage() {
        UUID id = UUID.randomUUID();
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(true);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.delete(id, auth, ra)).isEqualTo("redirect:/leagues");
        assertThat(ra.getFlashAttributes().get("statusMessage")).isEqualTo("League deleted.");
    }

    @Test
    void generateSchedule_bindingError_redirectsToLeague() {
        UUID id = UUID.randomUUID();
        ScheduleForm form = new ScheduleForm();
        BindingResult br = new BeanPropertyBindingResult(form, "scheduleForm");
        br.reject("err", "Start date is required");
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.generateSchedule(id, form, br, ra)).isEqualTo("redirect:/leagues/" + id);
        assertThat(ra.getFlashAttributes()).containsKey("scheduleError");
    }

    private LeagueDetailView detailWithTeams(int count) {
        LeagueDetailView detail = mock(LeagueDetailView.class);
        List<TeamDto> teams = new ArrayList<>();
        for (int i = 0; i < count; i++) teams.add(eligibleTeam());
        when(detail.getTeams()).thenReturn(teams);
        return detail;
    }

    @Test
    void generateSchedule_valid_generatesAndRedirects() {
        UUID id = UUID.randomUUID();
        LeagueDetailView detail = detailWithTeams(6);
        when(leagueService.findDetail(id)).thenReturn(detail);
        ScheduleForm form = new ScheduleForm();
        form.setStartDate(LocalDate.now());
        form.setStartTime(LocalTime.of(11, 0));
        BindingResult br = new BeanPropertyBindingResult(form, "scheduleForm");
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.generateSchedule(id, form, br, ra)).isEqualTo("redirect:/leagues/" + id);
        verify(scheduleService).generate(id, form.getStartDate(), form.getStartTime());
        assertThat(ra.getFlashAttributes()).containsKey("statusMessage");
    }

    @Test
    void generateSchedule_tooLateStart_setsErrorWithoutGenerating() {
        UUID id = UUID.randomUUID();
        LeagueDetailView detail = detailWithTeams(6);
        when(leagueService.findDetail(id)).thenReturn(detail);
        ScheduleForm form = new ScheduleForm();
        form.setStartDate(LocalDate.now());
        form.setStartTime(LocalTime.of(21, 45));
        BindingResult br = new BeanPropertyBindingResult(form, "scheduleForm");
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.generateSchedule(id, form, br, ra)).isEqualTo("redirect:/leagues/" + id);
        verify(scheduleService, never()).generate(any(), any(), any());
        assertThat(ra.getFlashAttributes()).containsKey("scheduleError");
    }

    @Test
    void generateSchedule_invalidOperation_setsScheduleError() {
        UUID id = UUID.randomUUID();
        LeagueDetailView detail = detailWithTeams(6);
        when(leagueService.findDetail(id)).thenReturn(detail);
        ScheduleForm form = new ScheduleForm();
        form.setStartDate(LocalDate.now());
        form.setStartTime(LocalTime.of(11, 0));
        BindingResult br = new BeanPropertyBindingResult(form, "scheduleForm");
        doThrow(new InvalidLeagueOperationException("exists"))
                .when(scheduleService).generate(any(), any(), any());
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.generateSchedule(id, form, br, ra)).isEqualTo("redirect:/leagues/" + id);
        assertThat(ra.getFlashAttributes().get("scheduleError")).isEqualTo("exists");
    }

    @Test
    void create_executed_generatesScheduleAndRedirectsToLeague() {
        UUID newId = UUID.randomUUID();
        LeagueDto dto = new LeagueDto();
        dto.setName("Cup");
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 6; i++) ids.add(UUID.randomUUID());
        dto.setTeamIds(ids);
        BindingResult br = new BeanPropertyBindingResult(dto, "leagueDto");
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(true);
        LeagueDto saved = new LeagueDto();
        saved.setId(newId);
        saved.setName("Cup");
        when(leagueService.findAll()).thenReturn(List.of(saved));
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.create(dto, br, null, auth, ra, model);

        assertThat(view).isEqualTo("redirect:/leagues/" + newId + "#schedule");
        verify(scheduleService).generate(any(), any(), any());
    }

    @Test
    void create_executed_generateFails_stillRedirects() {
        UUID newId = UUID.randomUUID();
        LeagueDto dto = new LeagueDto();
        dto.setName("Cup");
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 6; i++) ids.add(UUID.randomUUID());
        dto.setTeamIds(ids);
        dto.setScheduleStartDate(LocalDate.now());
        dto.setScheduleStartTime(LocalTime.of(11, 0));
        BindingResult br = new BeanPropertyBindingResult(dto, "leagueDto");
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(true);
        LeagueDto saved = new LeagueDto();
        saved.setId(newId);
        saved.setName("Cup");
        when(leagueService.findAll()).thenReturn(List.of(saved));
        doThrow(new InvalidLeagueOperationException("bad"))
                .when(scheduleService).generate(any(), any(), any());
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.create(dto, br, null, auth, ra, model))
                .isEqualTo("redirect:/leagues/" + newId + "#schedule");
    }

    @Test
    void create_tooLateStartTime_returnsFormWithError() {
        LeagueDto dto = new LeagueDto();
        dto.setName("Cup");
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 6; i++) ids.add(UUID.randomUUID());
        dto.setTeamIds(ids);
        dto.setScheduleStartTime(LocalTime.of(21, 45));
        BindingResult br = new BeanPropertyBindingResult(dto, "leagueDto");
        when(teamService.findAllFree()).thenReturn(List.of());
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.create(dto, br, null, auth, ra, model)).isEqualTo("leagues/form");
        assertThat(br.hasFieldErrors("scheduleStartTime")).isTrue();
    }

    @Test
    void createForm_fromRequest_usesResubmitPayload() {
        UUID reqId = UUID.randomUUID();
        List<TeamDto> teams = new ArrayList<>();
        for (int i = 0; i < 6; i++) teams.add(eligibleTeam());
        when(teamService.findAllFree()).thenReturn(teams);
        when(changeRequestService.getPayloadForResubmit(reqId, auth)).thenReturn(new LeagueDto());
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.createForm(reqId, model, auth, ra)).isEqualTo("leagues/form");
        assertThat(model.getAttribute("fromRequest")).isEqualTo(reqId);
    }

    @Test
    void detail_prefillsScheduleFormFromLeague() {
        UUID id = UUID.randomUUID();
        LeagueDetailView league = mock(LeagueDetailView.class);
        when(league.getMatches()).thenReturn(List.of());
        when(league.getScheduleStartDate()).thenReturn(LocalDate.of(2026, 8, 1));
        when(league.getScheduleStartTime()).thenReturn(LocalTime.of(12, 0));
        when(leagueService.findDetail(id)).thenReturn(league);
        when(matchFollowSupport.subscribedMatchIds(auth)).thenReturn(Set.of());
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getQueryString()).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/leagues/" + id);
        Model model = new ExtendedModelMap();

        assertThat(controller.detail(id, null, auth, request, model)).isEqualTo("leagues/detail");
        ScheduleForm form = (ScheduleForm) model.getAttribute("scheduleForm");
        assertThat(form.getStartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(form.getStartTime()).isEqualTo(LocalTime.of(12, 0));
    }

    @Test
    void create_fromRequest_cancelsPending() {
        UUID reqId = UUID.randomUUID();
        LeagueDto dto = new LeagueDto();
        dto.setName("Cup");
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 6; i++) ids.add(UUID.randomUUID());
        dto.setTeamIds(ids);
        BindingResult br = new BeanPropertyBindingResult(dto, "leagueDto");
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(false);
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.create(dto, br, reqId, auth, ra, model)).isEqualTo("redirect:/leagues");
        verify(changeRequestService).cancelIfPending(reqId, auth);
    }

    @Test
    void create_executed_newIdNotFound_redirectsToList() {
        LeagueDto dto = new LeagueDto();
        dto.setName("Cup");
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 6; i++) ids.add(UUID.randomUUID());
        dto.setTeamIds(ids);
        BindingResult br = new BeanPropertyBindingResult(dto, "leagueDto");
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(true);
        when(leagueService.findAll()).thenReturn(List.of());
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.create(dto, br, null, auth, ra, model)).isEqualTo("redirect:/leagues");
        assertThat(ra.getFlashAttributes().get("statusMessage")).isEqualTo("League created.");
    }

    @Test
    void detail_noMatches_returnsView() {
        UUID id = UUID.randomUUID();
        LeagueDetailView league = mock(LeagueDetailView.class);
        when(league.getMatches()).thenReturn(List.of());
        when(league.getScheduleStartDate()).thenReturn(null);
        when(league.getScheduleStartTime()).thenReturn(null);
        when(leagueService.findDetail(id)).thenReturn(league);
        when(matchFollowSupport.subscribedMatchIds(auth)).thenReturn(Set.of());

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getQueryString()).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/leagues/" + id);
        Model model = new ExtendedModelMap();

        assertThat(controller.detail(id, null, auth, request, model)).isEqualTo("leagues/detail");
        assertThat(model.getAttribute("league")).isSameAs(league);
    }

    @Test
    void detail_withLiveMatch_populatesRoundsAndLiveData() {
        UUID id = UUID.randomUUID();
        LeagueDetailView league = mock(LeagueDetailView.class);

        MatchDto m = new MatchDto();
        m.setId(UUID.randomUUID());
        m.setHomeTeamId(UUID.randomUUID());
        m.setAwayTeamId(UUID.randomUUID());
        m.setRoundNumber(1);
        m.setPlayedAt(LocalDateTime.now().minusMinutes(30));
        GoalDto g = new GoalDto();
        g.setMinute(10);
        g.setHalf(Half.FIRST);
        g.setHomeGoal(true);
        g.setRunningHomeScore(1);
        g.setRunningAwayScore(0);
        m.getGoalTimeline().add(g);

        when(league.getMatches()).thenReturn(List.of(m));
        when(league.getScheduleStartDate()).thenReturn(null);
        when(league.getScheduleStartTime()).thenReturn(null);
        when(leagueService.findDetail(id)).thenReturn(league);
        when(matchFollowSupport.subscribedMatchIds(auth)).thenReturn(Set.of());

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getQueryString()).thenReturn("round=1");
        when(request.getRequestURI()).thenReturn("/leagues/" + id);
        Model model = new ExtendedModelMap();

        assertThat(controller.detail(id, 1, auth, request, model)).isEqualTo("leagues/detail");
        assertThat(model.getAttribute("roundMatches")).isNotNull();
        assertThat(model.getAttribute("liveMatchesForJs")).isNotNull();
    }

    @Test
    void create_invalidTeamCount_rejectsWithCountError() {
        LeagueDto dto = new LeagueDto();
        dto.setName("Cup");
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 7; i++) ids.add(UUID.randomUUID());
        dto.setTeamIds(ids);
        BindingResult br = new BeanPropertyBindingResult(dto, "leagueDto");
        when(teamService.findAllFree()).thenReturn(List.of(eligibleTeam(), ineligibleTeam()));
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.create(dto, br, null, auth, ra, model)).isEqualTo("leagues/form");
        assertThat(br.hasErrors()).isTrue();
        assertThat(model.getAttribute("eligibleCount")).isEqualTo(1L);
        verify(changeRequestService, never()).submitOrExecute(any(), any(), any(), any(), any());
    }

    @Test
    void create_duplicateName_returnsFormWithNameError() {
        LeagueDto dto = new LeagueDto();
        dto.setName("Cup");
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 6; i++) ids.add(UUID.randomUUID());
        dto.setTeamIds(ids);
        BindingResult br = new BeanPropertyBindingResult(dto, "leagueDto");
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        when(teamService.findAllFree()).thenReturn(List.of(eligibleTeam(), ineligibleTeam()));
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.create(dto, br, null, auth, ra, model)).isEqualTo("leagues/form");
        assertThat(br.hasFieldErrors("name")).isTrue();
    }

    @Test
    void generateSchedule_singleMatchPerRound_skipsLateCheck() {
        UUID id = UUID.randomUUID();
        LeagueDetailView detail = detailWithTeams(2);
        when(leagueService.findDetail(id)).thenReturn(detail);
        ScheduleForm form = new ScheduleForm();
        form.setStartDate(LocalDate.now());
        form.setStartTime(LocalTime.of(21, 45));
        BindingResult br = new BeanPropertyBindingResult(form, "scheduleForm");
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.generateSchedule(id, form, br, ra)).isEqualTo("redirect:/leagues/" + id);
        verify(scheduleService).generate(id, form.getStartDate(), form.getStartTime());
        assertThat(ra.getFlashAttributes()).doesNotContainKey("scheduleError");
    }

    @Test
    void detail_allFutureMatches_fallsBackToAvailableRound() {
        UUID id = UUID.randomUUID();
        LeagueDetailView league = mock(LeagueDetailView.class);

        MatchDto m = new MatchDto();
        m.setId(UUID.randomUUID());
        m.setHomeTeamId(UUID.randomUUID());
        m.setAwayTeamId(UUID.randomUUID());
        m.setRoundNumber(1);
        m.setPlayedAt(LocalDateTime.now().plusDays(2));

        when(league.getMatches()).thenReturn(List.of(m));
        when(league.getScheduleStartDate()).thenReturn(null);
        when(league.getScheduleStartTime()).thenReturn(null);
        when(leagueService.findDetail(id)).thenReturn(league);
        when(matchFollowSupport.subscribedMatchIds(auth)).thenReturn(Set.of());

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getQueryString()).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/leagues/" + id);
        Model model = new ExtendedModelMap();

        assertThat(controller.detail(id, null, auth, request, model)).isEqualTo("leagues/detail");
        assertThat(model.getAttribute("selectedRound")).isEqualTo(1);
        assertThat(model.getAttribute("liveMatchesForJs")).isEqualTo(List.of());
    }

    @Test
    void detail_scheduleFormAlreadyInModel_doesNotOverwrite() {
        UUID id = UUID.randomUUID();
        LeagueDetailView league = mock(LeagueDetailView.class);
        when(league.getMatches()).thenReturn(List.of());
        when(leagueService.findDetail(id)).thenReturn(league);
        when(matchFollowSupport.subscribedMatchIds(auth)).thenReturn(Set.of());
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getQueryString()).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/leagues/" + id);
        Model model = new ExtendedModelMap();
        ScheduleForm existing = new ScheduleForm();
        model.addAttribute("scheduleForm", existing);

        assertThat(controller.detail(id, null, auth, request, model)).isEqualTo("leagues/detail");
        assertThat(model.getAttribute("scheduleForm")).isSameAs(existing);
    }

    @Test
    void detail_pastMatchNotToday_usesMaxRoundFallbackAndCountsPlayed() {
        UUID id = UUID.randomUUID();
        LeagueDetailView league = mock(LeagueDetailView.class);

        MatchDto m = new MatchDto();
        m.setId(UUID.randomUUID());
        m.setHomeTeamId(UUID.randomUUID());
        m.setAwayTeamId(UUID.randomUUID());
        m.setRoundNumber(1);
        m.setPlayedAt(LocalDateTime.now().minusDays(1));

        when(league.getMatches()).thenReturn(List.of(m));
        when(league.getScheduleStartDate()).thenReturn(null);
        when(league.getScheduleStartTime()).thenReturn(null);
        when(leagueService.findDetail(id)).thenReturn(league);
        when(matchFollowSupport.subscribedMatchIds(auth)).thenReturn(Set.of());

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getQueryString()).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/leagues/" + id);
        Model model = new ExtendedModelMap();

        assertThat(controller.detail(id, null, auth, request, model)).isEqualTo("leagues/detail");
        assertThat(model.getAttribute("selectedRound")).isEqualTo(1);
        assertThat(model.getAttribute("liveMatchesForJs")).isEqualTo(List.of());
        assertThat(model.getAttribute("playedMatchCount")).isEqualTo(1L);
    }

    @Test
    void detail_nullRoundNumberMatch_excludedFromAvailableRounds() {
        UUID id = UUID.randomUUID();
        LeagueDetailView league = mock(LeagueDetailView.class);

        MatchDto noRound = new MatchDto();
        noRound.setId(UUID.randomUUID());
        noRound.setHomeTeamId(UUID.randomUUID());
        noRound.setAwayTeamId(UUID.randomUUID());
        noRound.setRoundNumber(null);
        noRound.setPlayedAt(LocalDateTime.now().minusMinutes(30));

        MatchDto withRound = new MatchDto();
        withRound.setId(UUID.randomUUID());
        withRound.setHomeTeamId(UUID.randomUUID());
        withRound.setAwayTeamId(UUID.randomUUID());
        withRound.setRoundNumber(2);
        withRound.setPlayedAt(LocalDateTime.now().minusMinutes(30));

        when(league.getMatches()).thenReturn(List.of(noRound, withRound));
        when(league.getScheduleStartDate()).thenReturn(null);
        when(league.getScheduleStartTime()).thenReturn(null);
        when(leagueService.findDetail(id)).thenReturn(league);
        when(matchFollowSupport.subscribedMatchIds(auth)).thenReturn(Set.of());

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getQueryString()).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/leagues/" + id);
        Model model = new ExtendedModelMap();

        assertThat(controller.detail(id, null, auth, request, model)).isEqualTo("leagues/detail");
        assertThat(model.getAttribute("availableRounds")).isEqualTo(List.of(2));
        assertThat(model.getAttribute("selectedRound")).isEqualTo(2);
    }

    @Test
    void detail_goalWithNullFields_appliesDefaults() {
        UUID id = UUID.randomUUID();
        LeagueDetailView league = mock(LeagueDetailView.class);

        MatchDto m = new MatchDto();
        m.setId(UUID.randomUUID());
        m.setHomeTeamId(UUID.randomUUID());
        m.setAwayTeamId(UUID.randomUUID());
        m.setRoundNumber(1);
        m.setPlayedAt(LocalDateTime.now().minusMinutes(30));
        m.getGoalTimeline().add(new GoalDto());

        when(league.getMatches()).thenReturn(List.of(m));
        when(league.getScheduleStartDate()).thenReturn(null);
        when(league.getScheduleStartTime()).thenReturn(null);
        when(leagueService.findDetail(id)).thenReturn(league);
        when(matchFollowSupport.subscribedMatchIds(auth)).thenReturn(Set.of());

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getQueryString()).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/leagues/" + id);
        Model model = new ExtendedModelMap();

        assertThat(controller.detail(id, null, auth, request, model)).isEqualTo("leagues/detail");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> liveMatches = (List<Map<String, Object>>) model.getAttribute("liveMatchesForJs");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> goals = (List<Map<String, Object>>) liveMatches.get(0).get("goals");
        assertThat(goals.get(0).get("minute")).isEqualTo(0);
        assertThat(goals.get(0).get("half")).isEqualTo("FIRST");
        assertThat(goals.get(0).get("rh")).isEqualTo(0);
        assertThat(goals.get(0).get("ra")).isEqualTo(0);
    }

    @Test
    void list_withDirParam_usesGivenDir() {
        when(leagueService.findAll(any(Sort.class))).thenReturn(List.of());
        when(teamService.findAllFree()).thenReturn(List.of());
        Model model = new ExtendedModelMap();

        assertThat(controller.list("name", "desc", model)).isEqualTo("leagues/list");
        assertThat(model.getAttribute("currentDir")).isEqualTo("desc");
    }

    @Test
    void create_blankName_rejectsNotBlank() {
        LeagueDto dto = new LeagueDto();
        dto.setName("   ");
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 6; i++) ids.add(UUID.randomUUID());
        dto.setTeamIds(ids);
        BindingResult br = new BeanPropertyBindingResult(dto, "leagueDto");
        when(teamService.findAllFree()).thenReturn(List.of());
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.create(dto, br, null, auth, ra, model)).isEqualTo("leagues/form");
        assertThat(br.hasFieldErrors("name")).isTrue();
    }

    @Test
    void create_emptyTeamIds_rejectsTeamsRequired() {
        LeagueDto dto = new LeagueDto();
        dto.setName("Cup");
        dto.setTeamIds(new ArrayList<>());
        BindingResult br = new BeanPropertyBindingResult(dto, "leagueDto");
        when(teamService.findAllFree()).thenReturn(List.of());
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.create(dto, br, null, auth, ra, model)).isEqualTo("leagues/form");
        assertThat(br.hasErrors()).isTrue();
        verify(changeRequestService, never()).submitOrExecute(any(), any(), any(), any(), any());
    }

    @Test
    void delete_notExecuted_redirectsWithApprovalMessage() {
        UUID id = UUID.randomUUID();
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(false);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.delete(id, auth, ra)).isEqualTo("redirect:/leagues");
        assertThat(ra.getFlashAttributes().get("statusMessage")).isEqualTo("Submitted for admin approval.");
    }

    @Test
    void list_mixedEligibility_countsOnlyEligibleTeams() {
        when(leagueService.findAll(any(Sort.class))).thenReturn(List.of());
        when(teamService.findAllFree()).thenReturn(List.of(eligibleTeam(), ineligibleTeam()));
        Model model = new ExtendedModelMap();

        assertThat(controller.list(null, null, model)).isEqualTo("leagues/list");
        assertThat(model.getAttribute("leagueEligibleCount")).isEqualTo(1L);
        assertThat(model.getAttribute("leagueFreeCount")).isEqualTo(2);
    }

    @Test
    void createForm_mixedEligibility_countsOnlyEligibleTeams() {
        when(teamService.findAllFree()).thenReturn(List.of(eligibleTeam(), ineligibleTeam()));
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.createForm(null, model, auth, ra)).isEqualTo("redirect:/teams/form");
        assertThat(ra.getFlashAttributes()).containsKey("warnMessage");
    }

    @Test
    void create_scheduleTimeSet_teamIdsNull_skipsTimeValidation() {
        LeagueDto dto = new LeagueDto();
        dto.setName("Cup");
        dto.setTeamIds(null);
        dto.setScheduleStartTime(LocalTime.of(21, 45));
        BindingResult br = new BeanPropertyBindingResult(dto, "leagueDto");
        when(teamService.findAllFree()).thenReturn(List.of());
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.create(dto, br, null, auth, ra, model)).isEqualTo("leagues/form");
        assertThat(br.hasFieldErrors("scheduleStartTime")).isFalse();
    }

    @Test
    void create_scheduleTimeSet_teamIdsEmpty_skipsTimeValidation() {
        LeagueDto dto = new LeagueDto();
        dto.setName("Cup");
        dto.setTeamIds(new ArrayList<>());
        dto.setScheduleStartTime(LocalTime.of(21, 45));
        BindingResult br = new BeanPropertyBindingResult(dto, "leagueDto");
        when(teamService.findAllFree()).thenReturn(List.of());
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.create(dto, br, null, auth, ra, model)).isEqualTo("leagues/form");
        assertThat(br.hasFieldErrors("scheduleStartTime")).isFalse();
    }

    @Test
    void create_scheduleTimeSet_invalidTeamCount_skipsTimeValidation() {
        LeagueDto dto = new LeagueDto();
        dto.setName("Cup");
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 7; i++) ids.add(UUID.randomUUID());
        dto.setTeamIds(ids);
        dto.setScheduleStartTime(LocalTime.of(21, 45));
        BindingResult br = new BeanPropertyBindingResult(dto, "leagueDto");
        when(teamService.findAllFree()).thenReturn(List.of());
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.create(dto, br, null, auth, ra, model)).isEqualTo("leagues/form");
        assertThat(br.hasFieldErrors("scheduleStartTime")).isFalse();
    }

    @Test
    void create_bindingErrors_withFromRequest_addsFromRequestToModel() {
        UUID reqId = UUID.randomUUID();
        LeagueDto dto = new LeagueDto();
        BindingResult br = new BeanPropertyBindingResult(dto, "leagueDto");
        when(teamService.findAllFree()).thenReturn(List.of());
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.create(dto, br, reqId, auth, ra, model)).isEqualTo("leagues/form");
        assertThat(model.getAttribute("fromRequest")).isEqualTo(reqId);
    }

    @Test
    void create_duplicateName_withFromRequest_addsFromRequestToModel() {
        UUID reqId = UUID.randomUUID();
        LeagueDto dto = new LeagueDto();
        dto.setName("Cup");
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 6; i++) ids.add(UUID.randomUUID());
        dto.setTeamIds(ids);
        BindingResult br = new BeanPropertyBindingResult(dto, "leagueDto");
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        when(teamService.findAllFree()).thenReturn(List.of());
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.create(dto, br, reqId, auth, ra, model)).isEqualTo("leagues/form");
        assertThat(model.getAttribute("fromRequest")).isEqualTo(reqId);
    }

    @Test
    void create_executed_newIdNameMismatch_redirectsToList() {
        LeagueDto dto = new LeagueDto();
        dto.setName("Cup");
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 6; i++) ids.add(UUID.randomUUID());
        dto.setTeamIds(ids);
        BindingResult br = new BeanPropertyBindingResult(dto, "leagueDto");
        when(changeRequestService.submitOrExecute(any(), any(), any(), any(), any())).thenReturn(true);
        LeagueDto other = new LeagueDto();
        other.setId(UUID.randomUUID());
        other.setName("Different League");
        when(leagueService.findAll()).thenReturn(List.of(other));
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        assertThat(controller.create(dto, br, null, auth, ra, model)).isEqualTo("redirect:/leagues");
        assertThat(ra.getFlashAttributes().get("statusMessage")).isEqualTo("League created.");
        verify(scheduleService, never()).generate(any(), any(), any());
    }

    @Test
    void detail_fallbackFilter_excludesNullRoundNumberMatch() {
        UUID id = UUID.randomUUID();
        LeagueDetailView league = mock(LeagueDetailView.class);

        MatchDto noRound = new MatchDto();
        noRound.setId(UUID.randomUUID());
        noRound.setHomeTeamId(UUID.randomUUID());
        noRound.setAwayTeamId(UUID.randomUUID());
        noRound.setRoundNumber(null);
        noRound.setPlayedAt(LocalDateTime.now().minusDays(1));

        MatchDto withRound = new MatchDto();
        withRound.setId(UUID.randomUUID());
        withRound.setHomeTeamId(UUID.randomUUID());
        withRound.setAwayTeamId(UUID.randomUUID());
        withRound.setRoundNumber(1);
        withRound.setPlayedAt(LocalDateTime.now().minusDays(2));

        when(league.getMatches()).thenReturn(List.of(noRound, withRound));
        when(league.getScheduleStartDate()).thenReturn(null);
        when(league.getScheduleStartTime()).thenReturn(null);
        when(leagueService.findDetail(id)).thenReturn(league);
        when(matchFollowSupport.subscribedMatchIds(auth)).thenReturn(Set.of());

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getQueryString()).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/leagues/" + id);
        Model model = new ExtendedModelMap();

        assertThat(controller.detail(id, null, auth, request, model)).isEqualTo("leagues/detail");
        assertThat(model.getAttribute("selectedRound")).isEqualTo(1);
    }

    @Test
    void detail_allMatchesNullRound_fallsBackToRoundOne() {
        UUID id = UUID.randomUUID();
        LeagueDetailView league = mock(LeagueDetailView.class);

        MatchDto noRound = new MatchDto();
        noRound.setId(UUID.randomUUID());
        noRound.setHomeTeamId(UUID.randomUUID());
        noRound.setAwayTeamId(UUID.randomUUID());
        noRound.setRoundNumber(null);
        noRound.setPlayedAt(LocalDateTime.now().minusMinutes(30));

        when(league.getMatches()).thenReturn(List.of(noRound));
        when(league.getScheduleStartDate()).thenReturn(null);
        when(league.getScheduleStartTime()).thenReturn(null);
        when(leagueService.findDetail(id)).thenReturn(league);
        when(matchFollowSupport.subscribedMatchIds(auth)).thenReturn(Set.of());

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getQueryString()).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/leagues/" + id);
        Model model = new ExtendedModelMap();

        assertThat(controller.detail(id, null, auth, request, model)).isEqualTo("leagues/detail");
        assertThat(model.getAttribute("availableRounds")).isEqualTo(List.of());
        assertThat(model.getAttribute("selectedRound")).isEqualTo(1);
    }
}
