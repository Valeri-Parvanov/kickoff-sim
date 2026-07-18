package com.kickoffsim.service.impl;

import com.kickoffsim.dto.*;
import com.kickoffsim.exception.ChangeRequestApprovalException;
import com.kickoffsim.exception.EntityNotFoundException;
import com.kickoffsim.model.*;
import com.kickoffsim.repository.ChangeRequestRepository;
import com.kickoffsim.repository.TeamRepository;
import com.kickoffsim.service.LeagueService;
import com.kickoffsim.service.MatchService;
import com.kickoffsim.service.PlayerService;
import com.kickoffsim.service.ScheduleService;
import com.kickoffsim.service.TeamService;
import com.kickoffsim.service.UserService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChangeRequestServiceImplTest {

    @Mock private ChangeRequestRepository changeRequestRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private UserService userService;
    @Mock private LeagueService leagueService;
    @Mock private TeamService teamService;
    @Mock private PlayerService playerService;
    @Mock private MatchService matchService;
    @Mock private ScheduleService scheduleService;
    @Mock private Validator validator;
    @Mock private Authentication authentication;

    private ChangeRequestServiceImpl changeRequestService;

    private User admin;
    private User regularUser;
    private ChangeRequest approvedRequest;
    private ChangeRequest rejectedRequest;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        changeRequestService = new ChangeRequestServiceImpl(
                changeRequestRepository, teamRepository, userService, leagueService, teamService,
                playerService, matchService, scheduleService, objectMapper, validator);

        admin = new User();
        admin.setId(UUID.randomUUID());
        admin.setUsername("admin");
        admin.setRole(Role.ADMIN);

        regularUser = new User();
        regularUser.setId(UUID.randomUUID());
        regularUser.setUsername("user");
        regularUser.setRole(Role.USER);

        approvedRequest = changeRequest(ChangeRequestStatus.APPROVED, EntityType.LEAGUE, ChangeAction.CREATE, "{}", admin);
        rejectedRequest = changeRequest(ChangeRequestStatus.REJECTED, EntityType.LEAGUE, ChangeAction.CREATE, "{}", admin);

        when(authentication.getName()).thenReturn("admin");
        when(userService.findByUsername("admin")).thenReturn(admin);
        when(validator.validate(any())).thenReturn(Set.of());
    }

    // ---- approve/reject not-found & already-resolved (pre-existing) ----

    @Test
    void approve_alreadyApproved_throwsChangeRequestApprovalException() {
        when(changeRequestRepository.findById(approvedRequest.getId()))
                .thenReturn(Optional.of(approvedRequest));

        assertThatThrownBy(() -> changeRequestService.approve(approvedRequest.getId(), authentication))
                .isInstanceOf(ChangeRequestApprovalException.class)
                .hasMessageContaining("already approved");
    }

    @Test
    void approve_alreadyRejected_throwsChangeRequestApprovalException() {
        when(changeRequestRepository.findById(rejectedRequest.getId()))
                .thenReturn(Optional.of(rejectedRequest));

        assertThatThrownBy(() -> changeRequestService.approve(rejectedRequest.getId(), authentication))
                .isInstanceOf(ChangeRequestApprovalException.class)
                .hasMessageContaining("already rejected");
    }

    @Test
    void reject_alreadyApproved_throwsChangeRequestApprovalException() {
        when(changeRequestRepository.findById(approvedRequest.getId()))
                .thenReturn(Optional.of(approvedRequest));

        assertThatThrownBy(() -> changeRequestService.reject(approvedRequest.getId(), authentication, "reason"))
                .isInstanceOf(ChangeRequestApprovalException.class)
                .hasMessageContaining("already approved");
    }

    @Test
    void reject_alreadyRejected_throwsChangeRequestApprovalException() {
        when(changeRequestRepository.findById(rejectedRequest.getId()))
                .thenReturn(Optional.of(rejectedRequest));

        assertThatThrownBy(() -> changeRequestService.reject(rejectedRequest.getId(), authentication, "reason"))
                .isInstanceOf(ChangeRequestApprovalException.class)
                .hasMessageContaining("already rejected");
    }

    @Test
    void approve_notFound_throwsEntityNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        when(changeRequestRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> changeRequestService.approve(unknownId, authentication))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void reject_notFound_throwsEntityNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        when(changeRequestRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> changeRequestService.reject(unknownId, authentication, null))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void reject_pending_setsRejectedStatusAndReason() {
        ChangeRequest pending = changeRequest(ChangeRequestStatus.PENDING, EntityType.TEAM, ChangeAction.CREATE, "{}", regularUser);
        when(changeRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

        changeRequestService.reject(pending.getId(), authentication, "not good");

        assertThat(pending.getStatus()).isEqualTo(ChangeRequestStatus.REJECTED);
        assertThat(pending.getReviewedBy()).isEqualTo(admin);
        assertThat(pending.getReviewedAt()).isNotNull();
        assertThat(pending.getRejectionReason()).isEqualTo("not good");
        verify(changeRequestRepository).save(pending);
    }

    // ---- submitOrExecute ----

    @Test
    void submitOrExecute_adminUser_appliesDirectlyAndReturnsTrue() {
        TeamDto dto = teamDto("Alpha", "Sofia", null);
        boolean result = changeRequestService.submitOrExecute(
                EntityType.TEAM, ChangeAction.CREATE, dto, null, authentication);

        assertThat(result).isTrue();
        verify(teamService).create(dto);
    }

    @Test
    void submitOrExecute_regularUser_savesChangeRequestAndReturnsFalse() {
        when(authentication.getName()).thenReturn("user");
        when(userService.findByUsername("user")).thenReturn(regularUser);

        TeamDto dto = teamDto("Alpha", "Sofia", null);
        boolean result = changeRequestService.submitOrExecute(
                EntityType.TEAM, ChangeAction.CREATE, dto, null, authentication);

        assertThat(result).isFalse();
        verify(changeRequestRepository).save(any(ChangeRequest.class));
    }

    @Test
    void submitOrExecute_regularUser_deleteAction_usesCurrentDtoAndSkipsEnrich() {
        when(authentication.getName()).thenReturn("user");
        when(userService.findByUsername("user")).thenReturn(regularUser);
        UUID teamId = UUID.randomUUID();
        when(teamService.findById(teamId)).thenReturn(teamDto("Alpha", "Sofia", null));

        boolean result = changeRequestService.submitOrExecute(
                EntityType.TEAM, ChangeAction.DELETE, null, teamId, authentication);

        assertThat(result).isFalse();
        verify(teamService).findById(teamId);
        ArgumentCaptor<ChangeRequest> captor = ArgumentCaptor.forClass(ChangeRequest.class);
        verify(changeRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getPayload()).contains("Alpha");
    }

    @Test
    void submitOrExecute_regularUser_teamSquadDelete_throwsChangeRequestApprovalException() {
        when(authentication.getName()).thenReturn("user");
        when(userService.findByUsername("user")).thenReturn(regularUser);

        assertThatThrownBy(() -> changeRequestService.submitOrExecute(
                EntityType.TEAM_SQUAD, ChangeAction.DELETE, null, UUID.randomUUID(), authentication))
                .isInstanceOf(ChangeRequestApprovalException.class)
                .hasMessageContaining("cannot be deleted");
    }

    @Test
    void submitOrExecute_regularUser_findCurrentDto_league_team_player_match() {
        when(authentication.getName()).thenReturn("user");
        when(userService.findByUsername("user")).thenReturn(regularUser);
        UUID id = UUID.randomUUID();
        when(leagueService.findById(id)).thenReturn(leagueDto("L", null, null, List.of()));
        when(teamService.findById(id)).thenReturn(teamDto("T", "C", null));
        when(playerService.findById(id)).thenReturn(playerDto("F", "L", 9, null));
        when(matchService.findById(id)).thenReturn(new MatchDto());

        changeRequestService.submitOrExecute(EntityType.LEAGUE, ChangeAction.DELETE, null, id, authentication);
        changeRequestService.submitOrExecute(EntityType.TEAM, ChangeAction.DELETE, null, id, authentication);
        changeRequestService.submitOrExecute(EntityType.PLAYER, ChangeAction.DELETE, null, id, authentication);
        changeRequestService.submitOrExecute(EntityType.MATCH, ChangeAction.DELETE, null, id, authentication);

        verify(leagueService).findById(id);
        verify(teamService).findById(id);
        verify(playerService).findById(id);
        verify(matchService).findById(id);
    }

    @Test
    void submitOrExecute_regularUser_enrichTeam_withLeague_setsLeagueName() {
        when(authentication.getName()).thenReturn("user");
        when(userService.findByUsername("user")).thenReturn(regularUser);
        UUID leagueId = UUID.randomUUID();
        when(leagueService.findById(leagueId)).thenReturn(leagueDto("Premier", null, null, List.of()));

        TeamDto dto = teamDto("Alpha", "Sofia", leagueId);
        changeRequestService.submitOrExecute(EntityType.TEAM, ChangeAction.CREATE, dto, null, authentication);

        assertThat(dto.getLeagueName()).isEqualTo("Premier");
    }

    @Test
    void submitOrExecute_regularUser_enrichTeam_withoutLeague_skipsLookup() {
        when(authentication.getName()).thenReturn("user");
        when(userService.findByUsername("user")).thenReturn(regularUser);

        TeamDto dto = teamDto("Alpha", "Sofia", null);
        changeRequestService.submitOrExecute(EntityType.TEAM, ChangeAction.CREATE, dto, null, authentication);

        verify(leagueService, never()).findById(any());
        assertThat(dto.getLeagueName()).isNull();
    }

    @Test
    void submitOrExecute_regularUser_enrichPlayer_withTeam_setsTeamName() {
        when(authentication.getName()).thenReturn("user");
        when(userService.findByUsername("user")).thenReturn(regularUser);
        UUID teamId = UUID.randomUUID();
        when(teamService.findById(teamId)).thenReturn(teamDto("Alpha", "Sofia", null));

        PlayerDto dto = playerDto("First", "Last", 7, teamId);
        changeRequestService.submitOrExecute(EntityType.PLAYER, ChangeAction.CREATE, dto, null, authentication);

        assertThat(dto.getTeamName()).isEqualTo("Alpha");
    }

    @Test
    void submitOrExecute_regularUser_enrichPlayer_withoutTeam_skipsLookup() {
        when(authentication.getName()).thenReturn("user");
        when(userService.findByUsername("user")).thenReturn(regularUser);

        PlayerDto dto = playerDto("First", "Last", 7, null);
        changeRequestService.submitOrExecute(EntityType.PLAYER, ChangeAction.CREATE, dto, null, authentication);

        verify(teamService, never()).findById(any());
        assertThat(dto.getTeamName()).isNull();
    }

    @Test
    void submitOrExecute_regularUser_enrichMatch_bothTeamsSet_setsBothNames() {
        when(authentication.getName()).thenReturn("user");
        when(userService.findByUsername("user")).thenReturn(regularUser);
        UUID homeId = UUID.randomUUID();
        UUID awayId = UUID.randomUUID();
        when(teamService.findById(homeId)).thenReturn(teamDto("Home", "H-City", null));
        when(teamService.findById(awayId)).thenReturn(teamDto("Away", "A-City", null));

        MatchDto dto = new MatchDto();
        dto.setHomeTeamId(homeId);
        dto.setAwayTeamId(awayId);
        changeRequestService.submitOrExecute(EntityType.MATCH, ChangeAction.CREATE, dto, null, authentication);

        assertThat(dto.getHomeTeamName()).isEqualTo("Home");
        assertThat(dto.getAwayTeamName()).isEqualTo("Away");
    }

    @Test
    void submitOrExecute_regularUser_enrichMatch_onlyHomeSet_setsOnlyHomeName() {
        when(authentication.getName()).thenReturn("user");
        when(userService.findByUsername("user")).thenReturn(regularUser);
        UUID homeId = UUID.randomUUID();
        when(teamService.findById(homeId)).thenReturn(teamDto("Home", "H-City", null));

        MatchDto dto = new MatchDto();
        dto.setHomeTeamId(homeId);
        changeRequestService.submitOrExecute(EntityType.MATCH, ChangeAction.CREATE, dto, null, authentication);

        assertThat(dto.getHomeTeamName()).isEqualTo("Home");
        assertThat(dto.getAwayTeamName()).isNull();
    }

    @Test
    void submitOrExecute_regularUser_enrichMatch_onlyAwaySet_setsOnlyAwayName() {
        when(authentication.getName()).thenReturn("user");
        when(userService.findByUsername("user")).thenReturn(regularUser);
        UUID awayId = UUID.randomUUID();
        when(teamService.findById(awayId)).thenReturn(teamDto("Away", "A-City", null));

        MatchDto dto = new MatchDto();
        dto.setAwayTeamId(awayId);
        changeRequestService.submitOrExecute(EntityType.MATCH, ChangeAction.CREATE, dto, null, authentication);

        assertThat(dto.getHomeTeamName()).isNull();
        assertThat(dto.getAwayTeamName()).isEqualTo("Away");
    }

    @Test
    void submitOrExecute_regularUser_enrichMatch_neitherSet_skipsBothLookups() {
        when(authentication.getName()).thenReturn("user");
        when(userService.findByUsername("user")).thenReturn(regularUser);

        MatchDto dto = new MatchDto();
        changeRequestService.submitOrExecute(EntityType.MATCH, ChangeAction.CREATE, dto, null, authentication);

        verify(teamService, never()).findById(any());
    }

    @Test
    void submitOrExecute_regularUser_enrichLeague_isNoOp() {
        when(authentication.getName()).thenReturn("user");
        when(userService.findByUsername("user")).thenReturn(regularUser);

        LeagueDto dto = leagueDto("Premier", null, null, List.of());
        boolean result = changeRequestService.submitOrExecute(EntityType.LEAGUE, ChangeAction.CREATE, dto, null, authentication);

        assertThat(result).isFalse();
    }

    @Test
    void submitOrExecute_regularUser_enrichTeamSquad_newTeam_setsLeagueName() {
        when(authentication.getName()).thenReturn("user");
        when(userService.findByUsername("user")).thenReturn(regularUser);
        UUID leagueId = UUID.randomUUID();
        when(leagueService.findById(leagueId)).thenReturn(leagueDto("Premier", null, null, List.of()));

        TeamDto team = teamDto("Alpha", "Sofia", leagueId);
        TeamSquadPayload payload = squadPayload(team, List.of(playerDto("F", "L", 1, null)));

        changeRequestService.submitOrExecute(EntityType.TEAM_SQUAD, ChangeAction.CREATE, payload, null, authentication);

        assertThat(team.getLeagueName()).isEqualTo("Premier");
    }

    @Test
    void submitOrExecute_regularUser_enrichTeamSquad_leagueNameAlreadySet_skipsLookup() {
        when(authentication.getName()).thenReturn("user");
        when(userService.findByUsername("user")).thenReturn(regularUser);
        UUID leagueId = UUID.randomUUID();

        TeamDto team = teamDto("Alpha", "Sofia", leagueId);
        team.setLeagueName("Already Set");
        TeamSquadPayload payload = squadPayload(team, List.of());

        changeRequestService.submitOrExecute(EntityType.TEAM_SQUAD, ChangeAction.CREATE, payload, null, authentication);

        verify(leagueService, never()).findById(any());
        assertThat(team.getLeagueName()).isEqualTo("Already Set");
    }

    @Test
    void submitOrExecute_regularUser_enrichTeamSquad_noLeagueId_skipsLookup() {
        when(authentication.getName()).thenReturn("user");
        when(userService.findByUsername("user")).thenReturn(regularUser);

        TeamDto team = teamDto("Alpha", "Sofia", null);
        TeamSquadPayload payload = squadPayload(team, List.of());

        changeRequestService.submitOrExecute(EntityType.TEAM_SQUAD, ChangeAction.CREATE, payload, null, authentication);

        verify(leagueService, never()).findById(any());
    }

    @Test
    void submitOrExecute_regularUser_toJsonFailure_wrapsAsChangeRequestApprovalException() {
        when(authentication.getName()).thenReturn("user");
        when(userService.findByUsername("user")).thenReturn(regularUser);

        Object unserializable = new BrokenDto();

        assertThatThrownBy(() -> changeRequestService.submitOrExecute(
                EntityType.LEAGUE, ChangeAction.CREATE, unserializable, null, authentication))
                .isInstanceOf(ChangeRequestApprovalException.class)
                .hasMessageContaining("Failed to serialize");
    }

    // ---- countPending / countMyPending ----

    @Test
    void countPending_delegatesToRepository() {
        when(changeRequestRepository.countByStatus(ChangeRequestStatus.PENDING)).thenReturn(7L);

        assertThat(changeRequestService.countPending()).isEqualTo(7L);
    }

    @Test
    void countMyPending_delegatesToRepository() {
        when(changeRequestRepository.countByStatusAndRequestedBy(ChangeRequestStatus.PENDING, admin)).thenReturn(3L);

        assertThat(changeRequestService.countMyPending(authentication)).isEqualTo(3L);
    }

    // ---- findPending / findMine / toView / buildDetails ----

    @Test
    void findPending_mapsToViewWithLeagueDetails() throws Exception {
        LeagueDto league = leagueDto("Premier", LocalDate.of(2026, 8, 1), LocalTime.of(10, 0), List.of());
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.LEAGUE, ChangeAction.CREATE, json(league), regularUser);
        when(changeRequestRepository.findAllByStatusOrderByRequestedAtAsc(ChangeRequestStatus.PENDING))
                .thenReturn(List.of(cr));

        List<ChangeRequestView> views = changeRequestService.findPending();

        assertThat(views).hasSize(1);
        ChangeRequestView view = views.get(0);
        assertThat(view.getId()).isEqualTo(cr.getId());
        assertThat(view.getEntityType()).isEqualTo(EntityType.LEAGUE);
        assertThat(view.getAction()).isEqualTo(ChangeAction.CREATE);
        assertThat(view.getStatus()).isEqualTo(ChangeRequestStatus.PENDING);
        assertThat(view.getRequestedByUsername()).isEqualTo("user");
        assertThat(view.getDetails()).contains("==League", "Name: Premier", "==Schedule",
                "Round 1 date: 2026-08-01", "First kick-off: 10:00");
    }

    @Test
    void findMine_delegatesToRepositoryAndMaps() throws Exception {
        TeamDto team = teamDto("Alpha", "Sofia", null);
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.TEAM, ChangeAction.CREATE, json(team), admin);
        when(changeRequestRepository.findAllByRequestedByOrderByRequestedAtDesc(admin)).thenReturn(List.of(cr));

        List<ChangeRequestView> views = changeRequestService.findMine(authentication);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).getDetails()).contains("==Team", "Name: Alpha", "City: Sofia");
    }

    @Test
    void buildDetails_league_withoutScheduleAndTeams_omitsOptionalSections() throws Exception {
        LeagueDto league = leagueDto("Premier", null, null, List.of());
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.LEAGUE, ChangeAction.CREATE, json(league), admin);
        when(changeRequestRepository.findAllByStatusOrderByRequestedAtAsc(ChangeRequestStatus.PENDING)).thenReturn(List.of(cr));

        List<String> details = changeRequestService.findPending().get(0).getDetails();

        assertThat(details).containsExactly("==League", "Name: Premier");
    }

    @Test
    void buildDetails_league_onlyDateSet_showsOnlyDateLine() throws Exception {
        LeagueDto league = leagueDto("Premier", LocalDate.of(2026, 9, 1), null, List.of());
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.LEAGUE, ChangeAction.CREATE, json(league), admin);
        when(changeRequestRepository.findAllByStatusOrderByRequestedAtAsc(ChangeRequestStatus.PENDING)).thenReturn(List.of(cr));

        List<String> details = changeRequestService.findPending().get(0).getDetails();

        assertThat(details).contains("Round 1 date: 2026-09-01");
        assertThat(details).doesNotContain("First kick-off: null");
    }

    @Test
    void buildDetails_league_onlyTimeSet_showsOnlyKickoffLine() throws Exception {
        LeagueDto league = leagueDto("Premier", null, LocalTime.of(11, 30), List.of());
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.LEAGUE, ChangeAction.CREATE, json(league), admin);
        when(changeRequestRepository.findAllByStatusOrderByRequestedAtAsc(ChangeRequestStatus.PENDING)).thenReturn(List.of(cr));

        List<String> details = changeRequestService.findPending().get(0).getDetails();

        assertThat(details).contains("==Schedule", "First kick-off: 11:30");
        assertThat(details).noneMatch(line -> line.startsWith("Round 1 date:"));
    }

    @Test
    void buildDetails_league_nullTeamIds_omitsTeamsSection() throws Exception {
        LeagueDto league = leagueDto("Premier", null, null, List.of());
        league.setTeamIds(null);
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.LEAGUE, ChangeAction.CREATE, json(league), admin);
        when(changeRequestRepository.findAllByStatusOrderByRequestedAtAsc(ChangeRequestStatus.PENDING)).thenReturn(List.of(cr));

        List<String> details = changeRequestService.findPending().get(0).getDetails();

        assertThat(details).containsExactly("==League", "Name: Premier");
    }

    @Test
    void buildDetails_league_withKnownAndUnknownTeams() throws Exception {
        UUID knownId = UUID.randomUUID();
        UUID unknownId = UUID.randomUUID();
        Team known = new Team();
        known.setId(knownId);
        known.setName("Alpha");
        known.setCity("Sofia");
        when(teamRepository.findById(knownId)).thenReturn(Optional.of(known));
        when(teamRepository.findById(unknownId)).thenReturn(Optional.empty());

        LeagueDto league = leagueDto("Premier", null, null, List.of(knownId, unknownId));
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.LEAGUE, ChangeAction.CREATE, json(league), admin);
        when(changeRequestRepository.findAllByStatusOrderByRequestedAtAsc(ChangeRequestStatus.PENDING)).thenReturn(List.of(cr));

        List<String> details = changeRequestService.findPending().get(0).getDetails();

        assertThat(details).contains("==Teams (2)", "· Alpha (Sofia)", "· [unknown team]");
    }

    @Test
    void buildDetails_team_withCityAndLeague() throws Exception {
        TeamDto team = teamDto("Alpha", "Sofia", null);
        team.setLeagueName("Premier");
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.TEAM, ChangeAction.CREATE, json(team), admin);
        when(changeRequestRepository.findAllByStatusOrderByRequestedAtAsc(ChangeRequestStatus.PENDING)).thenReturn(List.of(cr));

        List<String> details = changeRequestService.findPending().get(0).getDetails();

        assertThat(details).containsExactly("==Team", "Name: Alpha", "City: Sofia", "League: Premier");
    }

    @Test
    void buildDetails_league_knownTeamWithoutCity_omitsCitySuffix() throws Exception {
        UUID knownId = UUID.randomUUID();
        Team known = new Team();
        known.setId(knownId);
        known.setName("Alpha");
        when(teamRepository.findById(knownId)).thenReturn(Optional.of(known));

        LeagueDto league = leagueDto("Premier", null, null, List.of(knownId));
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.LEAGUE, ChangeAction.CREATE, json(league), admin);
        when(changeRequestRepository.findAllByStatusOrderByRequestedAtAsc(ChangeRequestStatus.PENDING)).thenReturn(List.of(cr));

        List<String> details = changeRequestService.findPending().get(0).getDetails();

        assertThat(details).contains("· Alpha");
        assertThat(details).noneMatch(line -> line.contains("Alpha ("));
    }

    @Test
    void buildDetails_team_blankCity_omitsCityLine() throws Exception {
        TeamDto team = teamDto("Alpha", "   ", null);
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.TEAM, ChangeAction.CREATE, json(team), admin);
        when(changeRequestRepository.findAllByStatusOrderByRequestedAtAsc(ChangeRequestStatus.PENDING)).thenReturn(List.of(cr));

        List<String> details = changeRequestService.findPending().get(0).getDetails();

        assertThat(details).containsExactly("==Team", "Name: Alpha");
    }

    @Test
    void buildDetails_team_withoutCityAndLeague() throws Exception {
        TeamDto team = new TeamDto();
        team.setName("Alpha");
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.TEAM, ChangeAction.CREATE, json(team), admin);
        when(changeRequestRepository.findAllByStatusOrderByRequestedAtAsc(ChangeRequestStatus.PENDING)).thenReturn(List.of(cr));

        List<String> details = changeRequestService.findPending().get(0).getDetails();

        assertThat(details).containsExactly("==Team", "Name: Alpha");
    }

    @Test
    void buildDetails_player_withAndWithoutTeam() throws Exception {
        PlayerDto withTeam = playerDto("First", "Last", 9, null);
        withTeam.setTeamName("Alpha");
        ChangeRequest crWith = changeRequest(ChangeRequestStatus.PENDING, EntityType.PLAYER, ChangeAction.CREATE, json(withTeam), admin);

        PlayerDto withoutTeam = playerDto("First", "Last", 9, null);
        ChangeRequest crWithout = changeRequest(ChangeRequestStatus.PENDING, EntityType.PLAYER, ChangeAction.CREATE, json(withoutTeam), admin);

        when(changeRequestRepository.findAllByStatusOrderByRequestedAtAsc(ChangeRequestStatus.PENDING))
                .thenReturn(List.of(crWith, crWithout));

        List<ChangeRequestView> views = changeRequestService.findPending();

        assertThat(views.get(0).getDetails()).contains("Team: Alpha");
        assertThat(views.get(1).getDetails()).doesNotContain("Team: Alpha");
    }

    @Test
    void buildDetails_match_containsHomeAwayAndKickoff() throws Exception {
        MatchDto match = new MatchDto();
        match.setHomeTeamName("Home");
        match.setAwayTeamName("Away");
        match.setPlayedAt(LocalDateTime.of(2026, 8, 1, 18, 0));
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.MATCH, ChangeAction.CREATE, json(match), admin);
        when(changeRequestRepository.findAllByStatusOrderByRequestedAtAsc(ChangeRequestStatus.PENDING)).thenReturn(List.of(cr));

        List<String> details = changeRequestService.findPending().get(0).getDetails();

        assertThat(details).containsExactly("==Match", "Home: Home", "Away: Away", "Kick-off: 2026-08-01T18:00");
    }

    @Test
    void buildDetails_teamSquad_newTeam_withLeague() throws Exception {
        TeamDto team = teamDto("Alpha", "Sofia", null);
        team.setLeagueName("Premier");
        TeamSquadPayload payload = squadPayload(team, List.of(playerDto("F", "L", 9, null)));
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.TEAM_SQUAD, ChangeAction.CREATE, json(payload), admin);
        when(changeRequestRepository.findAllByStatusOrderByRequestedAtAsc(ChangeRequestStatus.PENDING)).thenReturn(List.of(cr));

        List<String> details = changeRequestService.findPending().get(0).getDetails();

        assertThat(details).contains("==Team", "Name: Alpha (Sofia)", "League: Premier", "==Players (1)", "· #9 F L");
    }

    @Test
    void buildDetails_teamSquad_newTeam_blankCity_omitsCitySuffix() throws Exception {
        TeamDto team = teamDto("Alpha", "   ", null);
        TeamSquadPayload payload = squadPayload(team, List.of());
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.TEAM_SQUAD, ChangeAction.CREATE, json(payload), admin);
        when(changeRequestRepository.findAllByStatusOrderByRequestedAtAsc(ChangeRequestStatus.PENDING)).thenReturn(List.of(cr));

        List<String> details = changeRequestService.findPending().get(0).getDetails();

        assertThat(details).contains("Name: Alpha");
        assertThat(details).noneMatch(line -> line.contains("Alpha ("));
    }

    @Test
    void buildDetails_teamSquad_newTeam_withoutCityAndLeague() throws Exception {
        TeamDto team = teamDto("Alpha", null, null);
        TeamSquadPayload payload = squadPayload(team, List.of());
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.TEAM_SQUAD, ChangeAction.CREATE, json(payload), admin);
        when(changeRequestRepository.findAllByStatusOrderByRequestedAtAsc(ChangeRequestStatus.PENDING)).thenReturn(List.of(cr));

        List<String> details = changeRequestService.findPending().get(0).getDetails();

        assertThat(details).contains("Name: Alpha");
        assertThat(details).noneMatch(line -> line.startsWith("League:"));
    }

    @Test
    void buildDetails_teamSquad_addToExistingTeam() throws Exception {
        TeamDto team = teamDto("Alpha", "Sofia", null);
        team.setId(UUID.randomUUID());
        TeamSquadPayload payload = squadPayload(team, List.of(playerDto("F", "L", 9, null)));
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.TEAM_SQUAD, ChangeAction.CREATE, json(payload), admin);
        when(changeRequestRepository.findAllByStatusOrderByRequestedAtAsc(ChangeRequestStatus.PENDING)).thenReturn(List.of(cr));

        List<String> details = changeRequestService.findPending().get(0).getDetails();

        assertThat(details).contains("Add players to: Alpha");
    }

    @Test
    void buildDetails_unreadablePayload_returnsFallbackMessage() {
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.LEAGUE, ChangeAction.CREATE, "not-json", admin);
        when(changeRequestRepository.findAllByStatusOrderByRequestedAtAsc(ChangeRequestStatus.PENDING)).thenReturn(List.of(cr));

        List<String> details = changeRequestService.findPending().get(0).getDetails();

        assertThat(details).containsExactly("(unable to read change details)");
    }

    // ---- getPayloadForResubmit ----

    @Test
    void getPayloadForResubmit_pendingOwnedNonDelete_returnsPayload() throws Exception {
        when(authentication.getName()).thenReturn("user");
        when(userService.findByUsername("user")).thenReturn(regularUser);
        TeamDto team = teamDto("Alpha", "Sofia", null);
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.TEAM, ChangeAction.CREATE, json(team), regularUser);
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Optional.of(cr));

        Object result = changeRequestService.getPayloadForResubmit(cr.getId(), authentication);

        assertThat(result).isInstanceOf(TeamDto.class);
        assertThat(((TeamDto) result).getName()).isEqualTo("Alpha");
    }

    @Test
    void getPayloadForResubmit_rejectedOwnedNonDelete_returnsPayload() throws Exception {
        when(authentication.getName()).thenReturn("user");
        when(userService.findByUsername("user")).thenReturn(regularUser);
        TeamDto team = teamDto("Alpha", "Sofia", null);
        ChangeRequest cr = changeRequest(ChangeRequestStatus.REJECTED, EntityType.TEAM, ChangeAction.CREATE, json(team), regularUser);
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Optional.of(cr));

        Object result = changeRequestService.getPayloadForResubmit(cr.getId(), authentication);

        assertThat(result).isInstanceOf(TeamDto.class);
    }

    @Test
    void getPayloadForResubmit_notOwner_throws() {
        when(authentication.getName()).thenReturn("user");
        when(userService.findByUsername("user")).thenReturn(regularUser);
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.TEAM, ChangeAction.CREATE, "{}", admin);
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Optional.of(cr));

        assertThatThrownBy(() -> changeRequestService.getPayloadForResubmit(cr.getId(), authentication))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getPayloadForResubmit_deleteAction_throws() {
        when(authentication.getName()).thenReturn("user");
        when(userService.findByUsername("user")).thenReturn(regularUser);
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.TEAM, ChangeAction.DELETE, "{}", regularUser);
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Optional.of(cr));

        assertThatThrownBy(() -> changeRequestService.getPayloadForResubmit(cr.getId(), authentication))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getPayloadForResubmit_approvedStatus_throws() {
        when(authentication.getName()).thenReturn("user");
        when(userService.findByUsername("user")).thenReturn(regularUser);
        ChangeRequest cr = changeRequest(ChangeRequestStatus.APPROVED, EntityType.TEAM, ChangeAction.CREATE, "{}", regularUser);
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Optional.of(cr));

        assertThatThrownBy(() -> changeRequestService.getPayloadForResubmit(cr.getId(), authentication))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getPayloadForResubmit_notFound_throws() {
        UUID unknownId = UUID.randomUUID();
        when(changeRequestRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> changeRequestService.getPayloadForResubmit(unknownId, authentication))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ---- approve: happy paths per entity type ----

    @Test
    void approve_league_create_withScheduleDates_generatesSchedule() throws Exception {
        UUID newLeagueId = UUID.randomUUID();
        LeagueDto payload = leagueDto("Premier", LocalDate.of(2026, 8, 1), LocalTime.of(10, 0), List.of());
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.LEAGUE, ChangeAction.CREATE, json(payload), regularUser);
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Optional.of(cr));
        LeagueDto withId = leagueDto("Premier", null, null, List.of());
        withId.setId(newLeagueId);
        when(leagueService.findAll()).thenReturn(List.of(withId));

        changeRequestService.approve(cr.getId(), authentication);

        verify(leagueService).create(any(LeagueDto.class));
        verify(scheduleService).generate(eq(newLeagueId), eq(LocalDate.of(2026, 8, 1)), eq(LocalTime.of(10, 0)));
        assertThat(cr.getStatus()).isEqualTo(ChangeRequestStatus.APPROVED);
        assertThat(cr.getReviewedBy()).isEqualTo(admin);
        verify(changeRequestRepository).save(cr);
    }

    @Test
    void approve_league_create_withoutScheduleDates_usesDefaults() throws Exception {
        UUID newLeagueId = UUID.randomUUID();
        LeagueDto payload = leagueDto("Premier", null, null, List.of());
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.LEAGUE, ChangeAction.CREATE, json(payload), regularUser);
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Optional.of(cr));
        LeagueDto withId = leagueDto("Premier", null, null, List.of());
        withId.setId(newLeagueId);
        when(leagueService.findAll()).thenReturn(List.of(withId));

        changeRequestService.approve(cr.getId(), authentication);

        verify(scheduleService).generate(eq(newLeagueId), eq(LocalDate.now()), eq(LocalTime.of(11, 0)));
    }

    @Test
    void approve_league_create_noMatchingLeagueFound_skipsScheduleGeneration() throws Exception {
        LeagueDto payload = leagueDto("Premier", null, null, List.of());
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.LEAGUE, ChangeAction.CREATE, json(payload), regularUser);
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Optional.of(cr));
        when(leagueService.findAll()).thenReturn(List.of());

        changeRequestService.approve(cr.getId(), authentication);

        verify(scheduleService, never()).generate(any(), any(), any());
        assertThat(cr.getStatus()).isEqualTo(ChangeRequestStatus.APPROVED);
    }

    @Test
    void approve_league_create_scheduleGenerationThrows_stillApproves() throws Exception {
        UUID newLeagueId = UUID.randomUUID();
        LeagueDto payload = leagueDto("Premier", null, null, List.of());
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.LEAGUE, ChangeAction.CREATE, json(payload), regularUser);
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Optional.of(cr));
        LeagueDto withId = leagueDto("Premier", null, null, List.of());
        withId.setId(newLeagueId);
        when(leagueService.findAll()).thenReturn(List.of(withId));
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(scheduleService).generate(any(), any(), any());

        changeRequestService.approve(cr.getId(), authentication);

        assertThat(cr.getStatus()).isEqualTo(ChangeRequestStatus.APPROVED);
    }

    @Test
    void approve_league_update_delegatesToLeagueService() throws Exception {
        UUID targetId = UUID.randomUUID();
        LeagueDto payload = leagueDto("Premier2", null, null, List.of());
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.LEAGUE, ChangeAction.UPDATE, json(payload), regularUser);
        cr.setTargetId(targetId);
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Optional.of(cr));

        changeRequestService.approve(cr.getId(), authentication);

        verify(leagueService).update(eq(targetId), any(LeagueDto.class));
    }

    @Test
    void approve_league_delete_delegatesToLeagueService() {
        UUID targetId = UUID.randomUUID();
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.LEAGUE, ChangeAction.DELETE, "{}", regularUser);
        cr.setTargetId(targetId);
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Optional.of(cr));

        changeRequestService.approve(cr.getId(), authentication);

        verify(leagueService).delete(targetId);
    }

    @Test
    void approve_team_create_update_delete() throws Exception {
        TeamDto payload = teamDto("Alpha", "Sofia", null);
        ChangeRequest create = changeRequest(ChangeRequestStatus.PENDING, EntityType.TEAM, ChangeAction.CREATE, json(payload), regularUser);
        when(changeRequestRepository.findById(create.getId())).thenReturn(Optional.of(create));
        changeRequestService.approve(create.getId(), authentication);
        verify(teamService).create(any(TeamDto.class));

        UUID targetId = UUID.randomUUID();
        ChangeRequest update = changeRequest(ChangeRequestStatus.PENDING, EntityType.TEAM, ChangeAction.UPDATE, json(payload), regularUser);
        update.setTargetId(targetId);
        when(changeRequestRepository.findById(update.getId())).thenReturn(Optional.of(update));
        changeRequestService.approve(update.getId(), authentication);
        verify(teamService).update(eq(targetId), any(TeamDto.class));

        ChangeRequest delete = changeRequest(ChangeRequestStatus.PENDING, EntityType.TEAM, ChangeAction.DELETE, "{}", regularUser);
        delete.setTargetId(targetId);
        when(changeRequestRepository.findById(delete.getId())).thenReturn(Optional.of(delete));
        changeRequestService.approve(delete.getId(), authentication);
        verify(teamService).delete(targetId);
    }

    @Test
    void approve_player_create_update_delete() throws Exception {
        PlayerDto payload = playerDto("First", "Last", 9, null);
        ChangeRequest create = changeRequest(ChangeRequestStatus.PENDING, EntityType.PLAYER, ChangeAction.CREATE, json(payload), regularUser);
        when(changeRequestRepository.findById(create.getId())).thenReturn(Optional.of(create));
        changeRequestService.approve(create.getId(), authentication);
        verify(playerService).create(any(PlayerDto.class));

        UUID targetId = UUID.randomUUID();
        ChangeRequest update = changeRequest(ChangeRequestStatus.PENDING, EntityType.PLAYER, ChangeAction.UPDATE, json(payload), regularUser);
        update.setTargetId(targetId);
        when(changeRequestRepository.findById(update.getId())).thenReturn(Optional.of(update));
        changeRequestService.approve(update.getId(), authentication);
        verify(playerService).update(eq(targetId), any(PlayerDto.class));

        ChangeRequest delete = changeRequest(ChangeRequestStatus.PENDING, EntityType.PLAYER, ChangeAction.DELETE, "{}", regularUser);
        delete.setTargetId(targetId);
        when(changeRequestRepository.findById(delete.getId())).thenReturn(Optional.of(delete));
        changeRequestService.approve(delete.getId(), authentication);
        verify(playerService).delete(targetId);
    }

    @Test
    void approve_match_create_update_delete() throws Exception {
        MatchDto payload = new MatchDto();
        payload.setHomeTeamName("H");
        payload.setAwayTeamName("A");
        ChangeRequest create = changeRequest(ChangeRequestStatus.PENDING, EntityType.MATCH, ChangeAction.CREATE, json(payload), regularUser);
        when(changeRequestRepository.findById(create.getId())).thenReturn(Optional.of(create));
        changeRequestService.approve(create.getId(), authentication);
        verify(matchService).create(any(MatchDto.class));

        UUID targetId = UUID.randomUUID();
        ChangeRequest update = changeRequest(ChangeRequestStatus.PENDING, EntityType.MATCH, ChangeAction.UPDATE, json(payload), regularUser);
        update.setTargetId(targetId);
        when(changeRequestRepository.findById(update.getId())).thenReturn(Optional.of(update));
        changeRequestService.approve(update.getId(), authentication);
        verify(matchService).update(eq(targetId), any(MatchDto.class));

        ChangeRequest delete = changeRequest(ChangeRequestStatus.PENDING, EntityType.MATCH, ChangeAction.DELETE, "{}", regularUser);
        delete.setTargetId(targetId);
        when(changeRequestRepository.findById(delete.getId())).thenReturn(Optional.of(delete));
        changeRequestService.approve(delete.getId(), authentication);
        verify(matchService).delete(targetId);
    }

    @Test
    void approve_teamSquad_newTeam_createsTeamThenPlayersWithTeamId() throws Exception {
        UUID newTeamId = UUID.randomUUID();
        TeamDto team = teamDto("Alpha", "Sofia", null);
        TeamSquadPayload payload = squadPayload(team, List.of(playerDto("F1", "L1", 1, null), playerDto("F2", "L2", 2, null)));
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.TEAM_SQUAD, ChangeAction.CREATE, json(payload), regularUser);
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Optional.of(cr));
        TeamDto created = teamDto("Alpha", "Sofia", null);
        created.setId(newTeamId);
        when(teamService.create(any(TeamDto.class))).thenReturn(created);

        changeRequestService.approve(cr.getId(), authentication);

        verify(teamService).create(any(TeamDto.class));
        ArgumentCaptor<PlayerDto> captor = ArgumentCaptor.forClass(PlayerDto.class);
        verify(playerService, times(2)).create(captor.capture());
        assertThat(captor.getAllValues()).allMatch(p -> p.getTeamId().equals(newTeamId));
    }

    @Test
    void approve_teamSquad_existingTeam_skipsTeamCreate() throws Exception {
        UUID existingTeamId = UUID.randomUUID();
        TeamDto team = teamDto("Alpha", "Sofia", null);
        team.setId(existingTeamId);
        TeamSquadPayload payload = squadPayload(team, List.of(playerDto("F1", "L1", 1, null)));
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.TEAM_SQUAD, ChangeAction.CREATE, json(payload), regularUser);
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Optional.of(cr));

        changeRequestService.approve(cr.getId(), authentication);

        verify(teamService, never()).create(any());
        ArgumentCaptor<PlayerDto> captor = ArgumentCaptor.forClass(PlayerDto.class);
        verify(playerService).create(captor.capture());
        assertThat(captor.getValue().getTeamId()).isEqualTo(existingTeamId);
    }

    // ---- approve: violation / exception branches ----

    @Test
    void approve_validationViolation_throwsWithJoinedMessages() throws Exception {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> v1 = org.mockito.Mockito.mock(ConstraintViolation.class);
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> v2 = org.mockito.Mockito.mock(ConstraintViolation.class);
        when(v1.getMessage()).thenReturn("msg1");
        when(v2.getMessage()).thenReturn("msg2");
        when(validator.validate(any())).thenReturn(Set.of(v1, v2));

        TeamDto payload = teamDto("Alpha", "Sofia", null);
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.TEAM, ChangeAction.CREATE, json(payload), regularUser);
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Optional.of(cr));

        assertThatThrownBy(() -> changeRequestService.approve(cr.getId(), authentication))
                .isInstanceOf(ChangeRequestApprovalException.class)
                .hasMessageContaining("msg1")
                .hasMessageContaining("msg2");
    }

    @Test
    void approve_dataIntegrityViolation_team_throwsDuplicateTeamMessage() throws Exception {
        TeamDto payload = teamDto("Dup", "Sofia", null);
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.TEAM, ChangeAction.CREATE, json(payload), regularUser);
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Optional.of(cr));
        when(teamService.create(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> changeRequestService.approve(cr.getId(), authentication))
                .isInstanceOf(ChangeRequestApprovalException.class)
                .hasMessage("A team named 'Dup' already exists. Choose a different name.");
    }

    @Test
    void approve_dataIntegrityViolation_league_throwsDuplicateLeagueMessage() throws Exception {
        LeagueDto payload = leagueDto("Dup League", null, null, List.of());
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.LEAGUE, ChangeAction.CREATE, json(payload), regularUser);
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Optional.of(cr));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("dup"))
                .when(leagueService).create(any());

        assertThatThrownBy(() -> changeRequestService.approve(cr.getId(), authentication))
                .isInstanceOf(ChangeRequestApprovalException.class)
                .hasMessage("A league named 'Dup League' already exists. Choose a different name.");
    }

    @Test
    void approve_dataIntegrityViolation_teamSquad_throwsDuplicateMessage() throws Exception {
        TeamDto team = teamDto("Dup Squad", "Sofia", null);
        TeamSquadPayload payload = squadPayload(team, List.of());
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.TEAM_SQUAD, ChangeAction.CREATE, json(payload), regularUser);
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Optional.of(cr));
        when(teamService.create(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> changeRequestService.approve(cr.getId(), authentication))
                .isInstanceOf(ChangeRequestApprovalException.class)
                .hasMessage("A team named 'Dup Squad' already exists. Choose a different name.");
    }

    @Test
    void approve_dataIntegrityViolation_player_throwsGenericMessage() throws Exception {
        PlayerDto payload = playerDto("First", "Last", 9, null);
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.PLAYER, ChangeAction.CREATE, json(payload), regularUser);
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Optional.of(cr));
        when(playerService.create(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> changeRequestService.approve(cr.getId(), authentication))
                .isInstanceOf(ChangeRequestApprovalException.class)
                .hasMessage("That name is already taken. Please choose a different one.");
    }

    @Test
    void approve_dataIntegrityViolation_match_throwsGenericMessage() throws Exception {
        MatchDto payload = new MatchDto();
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.MATCH, ChangeAction.CREATE, json(payload), regularUser);
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Optional.of(cr));
        when(matchService.create(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> changeRequestService.approve(cr.getId(), authentication))
                .isInstanceOf(ChangeRequestApprovalException.class)
                .hasMessage("That name is already taken. Please choose a different one.");
    }

    @Test
    void approve_dataIntegrityViolation_deleteAction_dtoNull_genericMessage() {
        UUID targetId = UUID.randomUUID();
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.TEAM, ChangeAction.DELETE, "{}", regularUser);
        cr.setTargetId(targetId);
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Optional.of(cr));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("dup"))
                .when(teamService).delete(targetId);

        assertThatThrownBy(() -> changeRequestService.approve(cr.getId(), authentication))
                .isInstanceOf(ChangeRequestApprovalException.class)
                .hasMessage("That name is already taken. Please choose a different one.");
    }

    @Test
    void approve_runtimeException_wrapsAsChangeRequestApprovalException() throws Exception {
        UUID targetId = UUID.randomUUID();
        TeamDto payload = teamDto("Alpha", "Sofia", null);
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.TEAM, ChangeAction.UPDATE, json(payload), regularUser);
        cr.setTargetId(targetId);
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Optional.of(cr));
        when(teamService.update(any(), any())).thenThrow(new IllegalStateException("oops"));

        assertThatThrownBy(() -> changeRequestService.approve(cr.getId(), authentication))
                .isInstanceOf(ChangeRequestApprovalException.class)
                .hasMessage("oops");
    }

    @Test
    void approve_changeRequestApprovalException_rethrownAsIs() throws Exception {
        TeamDto payload = teamDto("Alpha", "Sofia", null);
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.TEAM, ChangeAction.CREATE, json(payload), regularUser);
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Optional.of(cr));
        when(teamService.create(any())).thenThrow(new ChangeRequestApprovalException("custom"));

        assertThatThrownBy(() -> changeRequestService.approve(cr.getId(), authentication))
                .isInstanceOf(ChangeRequestApprovalException.class)
                .hasMessage("custom");
    }

    @Test
    void approve_unreadablePayload_throwsChangeRequestApprovalException() {
        ChangeRequest cr = changeRequest(ChangeRequestStatus.PENDING, EntityType.TEAM, ChangeAction.CREATE, "not-json", regularUser);
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Optional.of(cr));

        assertThatThrownBy(() -> changeRequestService.approve(cr.getId(), authentication))
                .isInstanceOf(ChangeRequestApprovalException.class)
                .hasMessageContaining("Failed to read");
    }

    // ---- cancelMine / cancelIfPending (pre-existing) ----

    @Test
    void cancelMine_ownPendingRequest_deletesIt() {
        ChangeRequest pending = changeRequest(ChangeRequestStatus.PENDING, EntityType.LEAGUE, ChangeAction.CREATE, "{}", admin);
        when(changeRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

        changeRequestService.cancelMine(pending.getId(), authentication);

        verify(changeRequestRepository).deleteById(pending.getId());
    }

    @Test
    void cancelMine_notOwner_throwsChangeRequestApprovalException() {
        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        otherUser.setUsername("other");
        otherUser.setRole(Role.USER);
        ChangeRequest pending = changeRequest(ChangeRequestStatus.PENDING, EntityType.LEAGUE, ChangeAction.CREATE, "{}", otherUser);
        when(changeRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> changeRequestService.cancelMine(pending.getId(), authentication))
                .isInstanceOf(ChangeRequestApprovalException.class)
                .hasMessageContaining("own requests");
    }

    @Test
    void cancelMine_notPending_throwsChangeRequestApprovalException() {
        when(changeRequestRepository.findById(approvedRequest.getId())).thenReturn(Optional.of(approvedRequest));

        assertThatThrownBy(() -> changeRequestService.cancelMine(approvedRequest.getId(), authentication))
                .isInstanceOf(ChangeRequestApprovalException.class)
                .hasMessageContaining("pending");
    }

    @Test
    void cancelMine_notFound_throwsEntityNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        when(changeRequestRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> changeRequestService.cancelMine(unknownId, authentication))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void cancelIfPending_ownPendingRequest_deletesIt() {
        ChangeRequest pending = changeRequest(ChangeRequestStatus.PENDING, EntityType.LEAGUE, ChangeAction.CREATE, "{}", admin);
        when(changeRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

        changeRequestService.cancelIfPending(pending.getId(), authentication);

        verify(changeRequestRepository).deleteById(pending.getId());
    }

    @Test
    void cancelIfPending_notOwner_doesNotDelete() {
        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        otherUser.setUsername("other");
        ChangeRequest pending = changeRequest(ChangeRequestStatus.PENDING, EntityType.LEAGUE, ChangeAction.CREATE, "{}", otherUser);
        when(changeRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

        changeRequestService.cancelIfPending(pending.getId(), authentication);

        verify(changeRequestRepository, never()).deleteById(any());
    }

    @Test
    void cancelIfPending_notPending_doesNotDelete() {
        when(changeRequestRepository.findById(approvedRequest.getId())).thenReturn(Optional.of(approvedRequest));

        changeRequestService.cancelIfPending(approvedRequest.getId(), authentication);

        verify(changeRequestRepository, never()).deleteById(any());
    }

    @Test
    void cancelIfPending_notFound_doesNothing() {
        UUID unknownId = UUID.randomUUID();
        when(changeRequestRepository.findById(unknownId)).thenReturn(Optional.empty());

        changeRequestService.cancelIfPending(unknownId, authentication);

        verify(changeRequestRepository, never()).deleteById(any());
    }

    // ---- expireStalePending / purgeResolvedOlderThan ----

    @Test
    void expireStalePending_withStaleRequests_expiresThem() {
        ChangeRequest stale = changeRequest(ChangeRequestStatus.PENDING, EntityType.LEAGUE, ChangeAction.CREATE, "{}", admin);
        when(changeRequestRepository.findAllByStatusAndRequestedAtBefore(eq(ChangeRequestStatus.PENDING), any()))
                .thenReturn(List.of(stale));

        int count = changeRequestService.expireStalePending(7);

        assertThat(count).isEqualTo(1);
        assertThat(stale.getStatus()).isEqualTo(ChangeRequestStatus.REJECTED);
        assertThat(stale.getRejectionReason()).contains("Automatically expired after 7 days");
        verify(changeRequestRepository).saveAll(List.of(stale));
    }

    @Test
    void expireStalePending_noStaleRequests_returnsZero() {
        when(changeRequestRepository.findAllByStatusAndRequestedAtBefore(eq(ChangeRequestStatus.PENDING), any()))
                .thenReturn(List.of());

        int count = changeRequestService.expireStalePending(7);

        assertThat(count).isZero();
        verify(changeRequestRepository).saveAll(List.of());
    }

    @Test
    void purgeResolvedOlderThan_withRemovedRows_returnsCount() {
        when(changeRequestRepository.deleteByStatusInAndReviewedAtBefore(any(), any())).thenReturn(5L);

        long removed = changeRequestService.purgeResolvedOlderThan(30);

        assertThat(removed).isEqualTo(5L);
    }

    @Test
    void purgeResolvedOlderThan_noRemovedRows_returnsZero() {
        when(changeRequestRepository.deleteByStatusInAndReviewedAtBefore(any(), any())).thenReturn(0L);

        long removed = changeRequestService.purgeResolvedOlderThan(30);

        assertThat(removed).isZero();
    }

    // ---- helpers ----

    private ChangeRequest changeRequest(ChangeRequestStatus status, EntityType entityType, ChangeAction action,
                                         String payload, User requestedBy) {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(UUID.randomUUID());
        cr.setEntityType(entityType);
        cr.setAction(action);
        cr.setStatus(status);
        cr.setRequestedBy(requestedBy);
        cr.setRequestedAt(LocalDateTime.now().minusHours(1));
        cr.setPayload(payload);
        return cr;
    }

    private String json(Object dto) throws Exception {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .writeValueAsString(dto);
    }

    private LeagueDto leagueDto(String name, LocalDate scheduleStartDate, LocalTime scheduleStartTime, List<UUID> teamIds) {
        LeagueDto dto = new LeagueDto();
        dto.setName(name);
        dto.setScheduleStartDate(scheduleStartDate);
        dto.setScheduleStartTime(scheduleStartTime);
        dto.setTeamIds(new java.util.ArrayList<>(teamIds));
        return dto;
    }

    private TeamDto teamDto(String name, String city, UUID leagueId) {
        TeamDto dto = new TeamDto();
        dto.setName(name);
        dto.setCity(city);
        dto.setLeagueId(leagueId);
        return dto;
    }

    private PlayerDto playerDto(String firstName, String lastName, int shirtNumber, UUID teamId) {
        PlayerDto dto = new PlayerDto();
        dto.setFirstName(firstName);
        dto.setLastName(lastName);
        dto.setShirtNumber(shirtNumber);
        dto.setTeamId(teamId);
        return dto;
    }

    private TeamSquadPayload squadPayload(TeamDto team, List<PlayerDto> players) {
        TeamSquadPayload payload = new TeamSquadPayload();
        payload.setTeam(team);
        payload.setPlayers(new java.util.ArrayList<>(players));
        return payload;
    }

    public static class BrokenDto {
        public String getValue() {
            throw new IllegalStateException("cannot serialize");
        }
    }
}
