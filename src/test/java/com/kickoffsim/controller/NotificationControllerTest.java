package com.kickoffsim.controller;

import com.kickoffsim.client.NotificationClient;
import com.kickoffsim.client.NotificationDto;
import com.kickoffsim.client.NotifyRequest;
import com.kickoffsim.client.SubscriptionDto;
import com.kickoffsim.client.SubscriptionRequest;
import com.kickoffsim.dto.GoalDto;
import com.kickoffsim.dto.LeagueDetailView;
import com.kickoffsim.dto.MatchDto;
import com.kickoffsim.dto.StandingRow;
import com.kickoffsim.dto.TeamDto;
import com.kickoffsim.model.Half;
import com.kickoffsim.model.User;
import com.kickoffsim.service.LeagueService;
import com.kickoffsim.service.MatchService;
import com.kickoffsim.service.TeamService;
import com.kickoffsim.service.UserService;
import com.kickoffsim.web.MatchFollowSupport;
import com.kickoffsim.web.SseEmitterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationControllerTest {

    @Mock private NotificationClient notificationClient;
    @Mock private UserService userService;
    @Mock private TeamService teamService;
    @Mock private LeagueService leagueService;
    @Mock private MatchService matchService;
    @Mock private MatchFollowSupport matchFollowSupport;
    @Mock private SseEmitterRegistry sseEmitterRegistry;

    @InjectMocks private NotificationController controller;

    @Test
    void stream_authenticated_registersAndReturnsEmitter() {
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                new org.springframework.web.servlet.mvc.method.annotation.SseEmitter();
        when(sseEmitterRegistry.register(userId)).thenReturn(emitter);

        assertThat(controller.stream(auth)).isSameAs(emitter);
    }

    @Test
    void stream_anonymous_returnsCompletedEmitter() {
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter result = controller.stream(null);

        assertThat(result).isNotNull();
        verify(sseEmitterRegistry, never()).register(any());
    }

    @Test
    void toggleMatchFollow_notFollowing_subscribesAndSendsInstantStatus() {
        UUID matchId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);

        when(notificationClient.getSubscriptions(userId)).thenReturn(List.of());

        MatchDto match = new MatchDto();
        match.setId(matchId);
        match.setHomeTeamName("Home");
        match.setAwayTeamName("Away");
        match.setPlayedAt(LocalDateTime.now().plusHours(2));
        when(matchService.findById(matchId)).thenReturn(match);

        ResponseEntity<Map<String, Object>> response = controller.toggleMatchFollow(matchId, auth);

        verify(notificationClient).subscribe(any(SubscriptionRequest.class));
        verify(notificationClient).notifyUser(any(NotifyRequest.class));
        verify(notificationClient, never()).unsubscribe(any());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("following", true);
    }

    @Test
    void toggleMatchFollow_alreadyFollowing_unsubscribesWithoutInstantStatus() {
        UUID matchId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);

        SubscriptionDto sub = new SubscriptionDto();
        sub.setId(subId);
        sub.setEntityType("MATCH");
        sub.setEntityId(matchId);
        when(notificationClient.getSubscriptions(userId)).thenReturn(List.of(sub));

        ResponseEntity<Map<String, Object>> response = controller.toggleMatchFollow(matchId, auth);

        verify(notificationClient).unsubscribe(subId);
        verify(notificationClient, never()).subscribe(any());
        verify(notificationClient, never()).notifyUser(any());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("following", false);
    }

    @Test
    void feedLiveSummary_nullAuth_returnsEmpty() {
        Map<String, Object> result = controller.feedLiveSummary(null);

        assertThat((List<?>) result.get("matches")).isEmpty();
    }

    @Test
    void feedLiveSummary_outerLookupFails_returnsEmpty() {
        Authentication auth = authFor("ghost", UUID.randomUUID());
        when(notificationClient.getSubscriptions(any())).thenThrow(new RuntimeException("down"));

        Map<String, Object> result = controller.feedLiveSummary(auth);

        assertThat((List<?>) result.get("matches")).isEmpty();
    }

    @Test
    void feedLiveSummary_teamLookupThrows_ignored() {
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        UUID teamId = UUID.randomUUID();
        SubscriptionDto teamSub = new SubscriptionDto();
        teamSub.setEntityType("TEAM");
        teamSub.setEntityId(teamId);
        when(notificationClient.getSubscriptions(userId)).thenReturn(List.of(teamSub));
        when(teamService.findById(teamId)).thenThrow(new RuntimeException("down"));

        Map<String, Object> result = controller.feedLiveSummary(auth);

        assertThat((List<?>) result.get("matches")).isEmpty();
    }

    @Test
    void feedLiveSummary_teamWithoutLeague_notAddedToLeagueIds() {
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        UUID teamId = UUID.randomUUID();
        SubscriptionDto teamSub = new SubscriptionDto();
        teamSub.setEntityType("TEAM");
        teamSub.setEntityId(teamId);
        when(notificationClient.getSubscriptions(userId)).thenReturn(List.of(teamSub));
        TeamDto team = new TeamDto();
        team.setId(teamId);
        team.setLeagueId(null);
        when(teamService.findById(teamId)).thenReturn(team);

        Map<String, Object> result = controller.feedLiveSummary(auth);

        assertThat((List<?>) result.get("matches")).isEmpty();
        verify(leagueService, never()).findDetail(any());
    }

    @Test
    void feedLiveSummary_leagueSub_addsLeagueIdDirectly() {
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        UUID leagueId = UUID.randomUUID();
        SubscriptionDto leagueSub = new SubscriptionDto();
        leagueSub.setEntityType("LEAGUE");
        leagueSub.setEntityId(leagueId);
        when(notificationClient.getSubscriptions(userId)).thenReturn(List.of(leagueSub));
        MatchDto live = feedMatch(UUID.randomUUID(), LocalDateTime.now().minusMinutes(5));
        LeagueDetailView league = org.mockito.Mockito.mock(LeagueDetailView.class);
        when(league.getMatches()).thenReturn(List.of(live));
        when(leagueService.findDetail(leagueId)).thenReturn(league);

        Map<String, Object> result = controller.feedLiveSummary(auth);

        verify(leagueService).findDetail(leagueId);
        assertThat((List<?>) result.get("matches")).isEmpty();
    }

    @Test
    void feedLiveSummary_findDetailThrows_ignored() {
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        UUID leagueId = UUID.randomUUID();
        SubscriptionDto leagueSub = new SubscriptionDto();
        leagueSub.setEntityType("LEAGUE");
        leagueSub.setEntityId(leagueId);
        when(notificationClient.getSubscriptions(userId)).thenReturn(List.of(leagueSub));
        when(leagueService.findDetail(leagueId)).thenThrow(new RuntimeException("down"));

        Map<String, Object> result = controller.feedLiveSummary(auth);

        assertThat((List<?>) result.get("matches")).isEmpty();
    }

    @Test
    void feedLiveSummary_followedMatchLookupThrows_ignored() {
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        UUID matchId = UUID.randomUUID();
        SubscriptionDto matchSub = new SubscriptionDto();
        matchSub.setEntityType("MATCH");
        matchSub.setEntityId(matchId);
        when(notificationClient.getSubscriptions(userId)).thenReturn(List.of(matchSub));
        when(matchService.findById(matchId)).thenThrow(new RuntimeException("down"));

        Map<String, Object> result = controller.feedLiveSummary(auth);

        assertThat((List<?>) result.get("matches")).isEmpty();
    }

    @Test
    void feedLiveSummary_followedMatchAlreadyInLeagueMatches_skipsRedundantFetch() {
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        UUID leagueId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        SubscriptionDto leagueSub = new SubscriptionDto();
        leagueSub.setEntityType("LEAGUE");
        leagueSub.setEntityId(leagueId);
        SubscriptionDto matchSub = new SubscriptionDto();
        matchSub.setEntityType("MATCH");
        matchSub.setEntityId(matchId);
        when(notificationClient.getSubscriptions(userId)).thenReturn(List.of(leagueSub, matchSub));

        MatchDto match = feedMatch(UUID.randomUUID(), LocalDateTime.now().minusMinutes(5));
        match.setId(matchId);
        LeagueDetailView league = org.mockito.Mockito.mock(LeagueDetailView.class);
        when(league.getMatches()).thenReturn(List.of(match));
        when(leagueService.findDetail(leagueId)).thenReturn(league);

        Map<String, Object> result = controller.feedLiveSummary(auth);

        verify(matchService, never()).findById(any());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = (List<Map<String, Object>>) result.get("matches");
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).get("followed")).isEqualTo(true);
    }

    @Test
    void feedLiveSummary_followedTeamIsAwaySide_isIncluded() {
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        UUID teamId = UUID.randomUUID();
        UUID leagueId = UUID.randomUUID();

        SubscriptionDto teamSub = new SubscriptionDto();
        teamSub.setEntityType("TEAM");
        teamSub.setEntityId(teamId);
        when(notificationClient.getSubscriptions(userId)).thenReturn(List.of(teamSub));

        TeamDto team = new TeamDto();
        team.setId(teamId);
        team.setLeagueId(leagueId);
        when(teamService.findById(teamId)).thenReturn(team);

        MatchDto liveAwayMatch = otherMatch(teamId, LocalDateTime.now().minusMinutes(10), false);
        LeagueDetailView league = org.mockito.Mockito.mock(LeagueDetailView.class);
        when(league.getMatches()).thenReturn(List.of(liveAwayMatch));
        when(leagueService.findDetail(leagueId)).thenReturn(league);

        Map<String, Object> result = controller.feedLiveSummary(auth);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = (List<Map<String, Object>>) result.get("matches");
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).get("id")).isEqualTo(liveAwayMatch.getId().toString());
    }

    @Test
    void feedLiveSummary_authenticated_returnsFollowedLiveMatchesWithFieldsAndExcludesUnrelated() {
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        UUID teamId = UUID.randomUUID();
        UUID leagueId = UUID.randomUUID();
        UUID starredMatchId = UUID.randomUUID();

        SubscriptionDto teamSub = new SubscriptionDto();
        teamSub.setEntityType("TEAM");
        teamSub.setEntityId(teamId);
        SubscriptionDto matchSub = new SubscriptionDto();
        matchSub.setEntityType("MATCH");
        matchSub.setEntityId(starredMatchId);
        when(notificationClient.getSubscriptions(userId)).thenReturn(List.of(teamSub, matchSub));

        TeamDto team = new TeamDto();
        team.setId(teamId);
        team.setLeagueId(leagueId);
        when(teamService.findById(teamId)).thenReturn(team);

        MatchDto liveTeamMatch = feedMatch(teamId, LocalDateTime.now().minusMinutes(10));
        liveTeamMatch.setLeagueId(leagueId);
        liveTeamMatch.setLeagueName("Premier");
        liveTeamMatch.setRoundNumber(3);
        liveTeamMatch.setHomeTeamName("Home");
        liveTeamMatch.setHomeTeamCity("HCity");
        liveTeamMatch.setAwayTeamName("Away");
        liveTeamMatch.setAwayTeamCity("ACity");

        MatchDto futureLeagueMatch = feedMatch(teamId, LocalDateTime.now().plusDays(1));
        MatchDto tooOldLeagueMatch = feedMatch(teamId, LocalDateTime.now().minusDays(1));
        MatchDto unrelatedLiveMatch = feedMatch(UUID.randomUUID(), LocalDateTime.now().minusMinutes(8));

        LeagueDetailView league = org.mockito.Mockito.mock(LeagueDetailView.class);
        when(league.getMatches()).thenReturn(
                List.of(liveTeamMatch, futureLeagueMatch, tooOldLeagueMatch, unrelatedLiveMatch));
        when(leagueService.findDetail(leagueId)).thenReturn(league);

        MatchDto starredLiveMatch = new MatchDto();
        starredLiveMatch.setId(starredMatchId);
        starredLiveMatch.setHomeTeamId(UUID.randomUUID());
        starredLiveMatch.setAwayTeamId(UUID.randomUUID());
        starredLiveMatch.setPlayedAt(LocalDateTime.now().minusMinutes(5));
        starredLiveMatch.setLeagueId(null);
        when(matchService.findById(starredMatchId)).thenReturn(starredLiveMatch);

        Map<String, Object> result = controller.feedLiveSummary(auth);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = (List<Map<String, Object>>) result.get("matches");
        assertThat(matches).hasSize(2);
        Map<String, Object> teamEntry = matches.stream()
                .filter(m -> m.get("id").equals(liveTeamMatch.getId().toString())).findFirst().orElseThrow();
        assertThat(teamEntry.get("leagueId")).isEqualTo(leagueId.toString());
        assertThat(teamEntry.get("leagueName")).isEqualTo("Premier");
        assertThat(teamEntry.get("followed")).isEqualTo(false);
        Map<String, Object> starredEntry = matches.stream()
                .filter(m -> m.get("id").equals(starredMatchId.toString())).findFirst().orElseThrow();
        assertThat(starredEntry.get("leagueId")).isNull();
        assertThat(starredEntry.get("followed")).isEqualTo(true);
    }

    @Test
    void liveToasts_includesGoalKickoffHalftimeAndFulltimeButNotOtherTypes() {
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);

        when(notificationClient.getNotifications(userId)).thenReturn(List.of(
                recentNotification("GOAL", "GOAL for Home!"),
                recentNotification("MATCH_HALFTIME", "HALF TIME 1-0"),
                recentNotification("MATCH_FULLTIME", "FULL TIME 2-1"),
                recentNotification("MATCH_KICKOFF", "KICK OFF!"),
                recentNotification("MATCH_UPDATE", "LIVE: Home 1:0 Away")));

        List<Map<String, Object>> toasts = controller.liveToasts(auth);

        assertThat(toasts).extracting(t -> t.get("type"))
                .containsExactlyInAnyOrder("GOAL", "MATCH_HALFTIME", "MATCH_FULLTIME", "MATCH_KICKOFF");
    }

    private NotificationDto recentNotification(String type, String message) {
        NotificationDto n = new NotificationDto();
        n.setId(UUID.randomUUID());
        n.setType(type);
        n.setMessage(message);
        n.setCreatedAt(LocalDateTime.now());
        return n;
    }

    @Test
    void subscribe_success_setsStatusAndRedirectsToReturnUrl() {
        UUID userId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.subscribe(entityId, "TEAM", "/teams/" + entityId, auth, ra);

        verify(notificationClient).subscribe(any(SubscriptionRequest.class));
        assertThat(view).isEqualTo("redirect:/teams/" + entityId);
        assertThat(ra.getFlashAttributes()).containsKey("statusMessage");
    }

    @Test
    void subscribe_serviceDown_setsWarnAndRedirectsDefault() {
        UUID userId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        doThrow(new RuntimeException("down")).when(notificationClient).subscribe(any());

        String view = controller.subscribe(entityId, "LEAGUE", null, auth, ra);

        assertThat(view).isEqualTo("redirect:/feed");
        assertThat(ra.getFlashAttributes()).containsKey("warnMessage");
    }

    @Test
    void subscribe_team_backfillsMatchSubscriptionsForThatTeamOnly() {
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID leagueId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        TeamDto team = new TeamDto();
        team.setId(teamId);
        team.setLeagueId(leagueId);
        when(teamService.findById(teamId)).thenReturn(team);

        MatchDto teamMatch = feedMatch(teamId, LocalDateTime.now().plusDays(1));
        MatchDto otherMatch = feedMatch(UUID.randomUUID(), LocalDateTime.now().plusDays(2));
        LeagueDetailView league = org.mockito.Mockito.mock(LeagueDetailView.class);
        when(league.getMatches()).thenReturn(List.of(teamMatch, otherMatch));
        when(leagueService.findDetail(leagueId)).thenReturn(league);

        when(notificationClient.getSubscriptions(userId)).thenReturn(List.of());

        controller.subscribe(teamId, "TEAM", null, auth, ra);

        verify(notificationClient).subscribe(subReq(userId, "TEAM", teamId));
        verify(notificationClient).subscribe(subReq(userId, "MATCH", teamMatch.getId()));
        verify(notificationClient, never()).subscribe(subReq(userId, "MATCH", otherMatch.getId()));
    }

    @Test
    void subscribe_team_backfillsMatchWhereTeamIsAwaySide() {
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID leagueId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        TeamDto team = new TeamDto();
        team.setId(teamId);
        team.setLeagueId(leagueId);
        when(teamService.findById(teamId)).thenReturn(team);

        MatchDto awayMatch = feedMatch(UUID.randomUUID(), LocalDateTime.now().plusDays(1));
        awayMatch.setAwayTeamId(teamId);
        LeagueDetailView league = org.mockito.Mockito.mock(LeagueDetailView.class);
        when(league.getMatches()).thenReturn(List.of(awayMatch));
        when(leagueService.findDetail(leagueId)).thenReturn(league);

        when(notificationClient.getSubscriptions(userId)).thenReturn(List.of());

        controller.subscribe(teamId, "TEAM", null, auth, ra);

        verify(notificationClient).subscribe(subReq(userId, "MATCH", awayMatch.getId()));
    }

    @Test
    void subscribe_otherEntityType_noBackfillAttempted() {
        UUID userId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        controller.subscribe(entityId, "MATCH", null, auth, ra);

        verify(notificationClient, never()).getSubscriptions(any());
    }

    @Test
    void subscribe_backfillIndividualMatchSubscribeFails_continuesAndStillSucceeds() {
        UUID userId = UUID.randomUUID();
        UUID leagueId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        MatchDto m1 = feedMatch(UUID.randomUUID(), LocalDateTime.now().plusDays(1));
        LeagueDetailView league = org.mockito.Mockito.mock(LeagueDetailView.class);
        when(league.getMatches()).thenReturn(List.of(m1));
        when(leagueService.findDetail(leagueId)).thenReturn(league);
        when(notificationClient.getSubscriptions(userId)).thenReturn(List.of());
        doThrow(new RuntimeException("down"))
                .when(notificationClient).subscribe(subReq(userId, "MATCH", m1.getId()));

        String view = controller.subscribe(leagueId, "LEAGUE", null, auth, ra);

        assertThat(view).isEqualTo("redirect:/feed");
        assertThat(ra.getFlashAttributes()).containsKey("statusMessage");
    }

    @Test
    void subscribe_league_backfillsAllLeagueMatchesSkippingAlreadyFollowed() {
        UUID userId = UUID.randomUUID();
        UUID leagueId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        MatchDto m1 = feedMatch(UUID.randomUUID(), LocalDateTime.now().plusDays(1));
        MatchDto m2 = feedMatch(UUID.randomUUID(), LocalDateTime.now().plusDays(2));
        LeagueDetailView league = org.mockito.Mockito.mock(LeagueDetailView.class);
        when(league.getMatches()).thenReturn(List.of(m1, m2));
        when(leagueService.findDetail(leagueId)).thenReturn(league);

        SubscriptionDto alreadyFollowedMatch = new SubscriptionDto();
        alreadyFollowedMatch.setEntityType("MATCH");
        alreadyFollowedMatch.setEntityId(m1.getId());
        when(notificationClient.getSubscriptions(userId)).thenReturn(List.of(alreadyFollowedMatch));

        controller.subscribe(leagueId, "LEAGUE", null, auth, ra);

        verify(notificationClient, never()).subscribe(subReq(userId, "MATCH", m1.getId()));
        verify(notificationClient).subscribe(subReq(userId, "MATCH", m2.getId()));
    }

    @Test
    void subscribe_backfillServiceDown_stillShowsSuccess() {
        UUID userId = UUID.randomUUID();
        UUID leagueId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        when(leagueService.findDetail(leagueId)).thenThrow(new RuntimeException("down"));

        String view = controller.subscribe(leagueId, "LEAGUE", null, auth, ra);

        assertThat(view).isEqualTo("redirect:/feed");
        assertThat(ra.getFlashAttributes()).containsKey("statusMessage");
        assertThat(ra.getFlashAttributes()).doesNotContainKey("warnMessage");
    }

    @Test
    void subscribe_teamWithoutLeague_noBackfillAttempted() {
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        TeamDto team = new TeamDto();
        team.setId(teamId);
        team.setLeagueId(null);
        when(teamService.findById(teamId)).thenReturn(team);

        controller.subscribe(teamId, "TEAM", null, auth, ra);

        verify(notificationClient, never()).getSubscriptions(userId);
    }

    @Test
    void unsubscribe_success_setsStatusMessage() {
        UUID id = UUID.randomUUID();
        Authentication auth = authFor("alice", UUID.randomUUID());
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.unsubscribe(id, "/feed", auth, ra);

        verify(notificationClient).unsubscribe(id);
        assertThat(view).isEqualTo("redirect:/feed");
        assertThat(ra.getFlashAttributes()).containsKey("statusMessage");
    }

    @Test
    void unsubscribe_serviceDown_setsWarn() {
        UUID id = UUID.randomUUID();
        Authentication auth = authFor("alice", UUID.randomUUID());
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        doThrow(new RuntimeException("down")).when(notificationClient).unsubscribe(id);

        String view = controller.unsubscribe(id, null, auth, ra);

        assertThat(view).isEqualTo("redirect:/feed");
        assertThat(ra.getFlashAttributes()).containsKey("warnMessage");
    }

    @Test
    void feedPage_serviceDown_setsWarn() {
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        Model model = new ExtendedModelMap();
        when(notificationClient.getSubscriptions(userId)).thenThrow(new RuntimeException("down"));

        assertThat(controller.feedPage(auth, model)).isEqualTo("feed");
        assertThat(model.getAttribute("warnMessage")).isNotNull();
    }

    @Test
    void feedPage_noSubscriptions_rendersEmpty() {
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        Model model = new ExtendedModelMap();
        when(notificationClient.getSubscriptions(userId)).thenReturn(List.of());
        when(matchFollowSupport.subscribedMatchIds(auth)).thenReturn(Set.of());

        assertThat(controller.feedPage(auth, model)).isEqualTo("feed");
        assertThat((List<?>) model.getAttribute("teamViews")).isEmpty();
    }

    @Test
    void feedPage_withSubscriptions_buildsAllSections() {
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID leagueId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);

        SubscriptionDto teamSub = new SubscriptionDto();
        teamSub.setId(UUID.randomUUID());
        teamSub.setEntityType("TEAM");
        teamSub.setEntityId(teamId);
        SubscriptionDto leagueSub = new SubscriptionDto();
        leagueSub.setId(UUID.randomUUID());
        leagueSub.setEntityType("LEAGUE");
        leagueSub.setEntityId(leagueId);
        when(notificationClient.getSubscriptions(userId)).thenReturn(List.of(teamSub, leagueSub));

        TeamDto team = new TeamDto();
        team.setId(teamId);
        team.setName("SofiaFC");
        team.setCity("Sofia");
        team.setLeagueId(leagueId);
        when(teamService.findById(teamId)).thenReturn(team);

        LeagueDetailView league = org.mockito.Mockito.mock(LeagueDetailView.class);
        StandingRow row = new StandingRow();
        row.setTeamId(teamId);
        when(league.getName()).thenReturn("Premier");
        when(league.getStandings()).thenReturn(List.of(row));
        when(league.getTeams()).thenReturn(List.of(team));

        MatchDto live = feedMatch(teamId, LocalDateTime.now().minusMinutes(30));
        live.getGoalTimeline().add(liveGoal(5, Half.FIRST, 1, 0));
        MatchDto upcoming = feedMatch(teamId, LocalDateTime.now().plusDays(1));
        MatchDto recent = feedMatch(teamId, LocalDateTime.now().minusDays(2));
        when(league.getMatches()).thenReturn(List.of(live, upcoming, recent));
        when(leagueService.findDetail(leagueId)).thenReturn(league);
        when(matchFollowSupport.subscribedMatchIds(auth)).thenReturn(Set.of());

        Model model = new ExtendedModelMap();
        assertThat(controller.feedPage(auth, model)).isEqualTo("feed");
        assertThat((List<?>) model.getAttribute("teamViews")).hasSize(1);
        assertThat((List<?>) model.getAttribute("leagueViews")).hasSize(1);
        assertThat((List<?>) model.getAttribute("liveMatches")).hasSize(1);
        assertThat((List<?>) model.getAttribute("upcomingMatches")).hasSize(1);
        assertThat((List<?>) model.getAttribute("recentMatches")).hasSize(1);
    }

    @Test
    void feedPage_teamStandingsWithMultipleRows_findsCorrectPosition() {
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID otherTeamId = UUID.randomUUID();
        UUID leagueId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);

        SubscriptionDto teamSub = new SubscriptionDto();
        teamSub.setId(UUID.randomUUID());
        teamSub.setEntityType("TEAM");
        teamSub.setEntityId(teamId);
        when(notificationClient.getSubscriptions(userId)).thenReturn(List.of(teamSub));

        TeamDto team = new TeamDto();
        team.setId(teamId);
        team.setName("SofiaFC");
        team.setLeagueId(leagueId);
        when(teamService.findById(teamId)).thenReturn(team);

        LeagueDetailView league = org.mockito.Mockito.mock(LeagueDetailView.class);
        StandingRow other = new StandingRow();
        other.setTeamId(otherTeamId);
        StandingRow mine = new StandingRow();
        mine.setTeamId(teamId);
        when(league.getName()).thenReturn("Premier");
        when(league.getStandings()).thenReturn(List.of(other, mine));
        when(league.getMatches()).thenReturn(List.of());
        when(leagueService.findDetail(leagueId)).thenReturn(league);
        when(matchFollowSupport.subscribedMatchIds(auth)).thenReturn(Set.of());

        Model model = new ExtendedModelMap();
        controller.feedPage(auth, model);

        @SuppressWarnings("unchecked")
        List<NotificationController.SubscriptionView> teamViews =
                (List<NotificationController.SubscriptionView>) model.getAttribute("teamViews");
        assertThat(teamViews).hasSize(1);
        assertThat(teamViews.get(0).standingPosition()).isEqualTo(2);
    }

    @Test
    void feedPage_followedMatchAlreadyInFollowedLeague_skipsRedundantLookup() {
        UUID userId = UUID.randomUUID();
        UUID leagueId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);

        SubscriptionDto leagueSub = new SubscriptionDto();
        leagueSub.setId(UUID.randomUUID());
        leagueSub.setEntityType("LEAGUE");
        leagueSub.setEntityId(leagueId);
        SubscriptionDto matchSub = new SubscriptionDto();
        matchSub.setId(UUID.randomUUID());
        matchSub.setEntityType("MATCH");
        matchSub.setEntityId(matchId);
        when(notificationClient.getSubscriptions(userId)).thenReturn(List.of(leagueSub, matchSub));

        MatchDto match = feedMatch(UUID.randomUUID(), LocalDateTime.now().plusDays(1));
        match.setId(matchId);
        LeagueDetailView league = org.mockito.Mockito.mock(LeagueDetailView.class);
        when(league.getName()).thenReturn("Premier");
        when(league.getStandings()).thenReturn(List.of());
        when(league.getTeams()).thenReturn(List.of());
        when(league.getMatches()).thenReturn(List.of(match));
        when(leagueService.findDetail(leagueId)).thenReturn(league);
        when(matchFollowSupport.subscribedMatchIds(auth)).thenReturn(Set.of(matchId));

        Model model = new ExtendedModelMap();
        controller.feedPage(auth, model);

        verify(matchService, never()).findById(any());
        assertThat((List<?>) model.getAttribute("upcomingMatches")).hasSize(1);
    }

    @Test
    void feedPage_starredMatchWithoutTeamFollow_includedInUpcoming() {
        UUID userId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);

        SubscriptionDto matchSub = new SubscriptionDto();
        matchSub.setId(UUID.randomUUID());
        matchSub.setEntityType("MATCH");
        matchSub.setEntityId(matchId);
        when(notificationClient.getSubscriptions(userId)).thenReturn(List.of(matchSub));

        MatchDto starredUpcoming = new MatchDto();
        starredUpcoming.setId(matchId);
        starredUpcoming.setHomeTeamId(UUID.randomUUID());
        starredUpcoming.setAwayTeamId(UUID.randomUUID());
        starredUpcoming.setPlayedAt(LocalDateTime.now().plusDays(1));
        when(matchService.findById(matchId)).thenReturn(starredUpcoming);
        when(matchFollowSupport.subscribedMatchIds(auth)).thenReturn(Set.of(matchId));

        Model model = new ExtendedModelMap();
        controller.feedPage(auth, model);

        assertThat((List<?>) model.getAttribute("upcomingMatches")).hasSize(1);
    }

    @Test
    void feedPage_starredMatchWithoutTeamFollow_includedInRecent() {
        UUID userId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);

        SubscriptionDto matchSub = new SubscriptionDto();
        matchSub.setId(UUID.randomUUID());
        matchSub.setEntityType("MATCH");
        matchSub.setEntityId(matchId);
        when(notificationClient.getSubscriptions(userId)).thenReturn(List.of(matchSub));

        MatchDto starredRecent = new MatchDto();
        starredRecent.setId(matchId);
        starredRecent.setHomeTeamId(UUID.randomUUID());
        starredRecent.setAwayTeamId(UUID.randomUUID());
        starredRecent.setPlayedAt(LocalDateTime.now().minusDays(2));
        when(matchService.findById(matchId)).thenReturn(starredRecent);
        when(matchFollowSupport.subscribedMatchIds(auth)).thenReturn(Set.of(matchId));

        Model model = new ExtendedModelMap();
        controller.feedPage(auth, model);

        assertThat((List<?>) model.getAttribute("recentMatches")).hasSize(1);
    }

    @Test
    void feedPage_leagueLookupFails_usesFallbackView() {
        UUID userId = UUID.randomUUID();
        UUID leagueId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        SubscriptionDto sub = new SubscriptionDto();
        sub.setId(UUID.randomUUID());
        sub.setEntityType("LEAGUE");
        sub.setEntityId(leagueId);
        when(notificationClient.getSubscriptions(userId)).thenReturn(List.of(sub));
        when(leagueService.findDetail(leagueId)).thenThrow(new RuntimeException("gone"));
        when(matchFollowSupport.subscribedMatchIds(auth)).thenReturn(Set.of());
        Model model = new ExtendedModelMap();

        assertThat(controller.feedPage(auth, model)).isEqualTo("feed");
        assertThat((List<?>) model.getAttribute("leagueViews")).hasSize(1);
    }

    @Test
    void feedPage_starredMatchWithoutTeamFollow_includedInAllWindows() {
        UUID userId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);

        SubscriptionDto matchSub = new SubscriptionDto();
        matchSub.setId(UUID.randomUUID());
        matchSub.setEntityType("MATCH");
        matchSub.setEntityId(matchId);
        when(notificationClient.getSubscriptions(userId)).thenReturn(List.of(matchSub));

        MatchDto starredLive = new MatchDto();
        starredLive.setId(matchId);
        starredLive.setHomeTeamId(UUID.randomUUID());
        starredLive.setAwayTeamId(UUID.randomUUID());
        starredLive.setPlayedAt(LocalDateTime.now().minusMinutes(10));
        when(matchService.findById(matchId)).thenReturn(starredLive);
        when(matchFollowSupport.subscribedMatchIds(auth)).thenReturn(Set.of(matchId));

        Model model = new ExtendedModelMap();
        assertThat(controller.feedPage(auth, model)).isEqualTo("feed");
        assertThat((List<?>) model.getAttribute("teamViews")).isEmpty();
        assertThat((List<?>) model.getAttribute("liveMatches")).hasSize(1);
    }

    @Test
    void feedPage_starredMatchLookupFails_isSkipped() {
        UUID userId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);

        SubscriptionDto matchSub = new SubscriptionDto();
        matchSub.setId(UUID.randomUUID());
        matchSub.setEntityType("MATCH");
        matchSub.setEntityId(matchId);
        when(notificationClient.getSubscriptions(userId)).thenReturn(List.of(matchSub));
        when(matchService.findById(matchId)).thenThrow(new RuntimeException("gone"));
        when(matchFollowSupport.subscribedMatchIds(auth)).thenReturn(Set.of(matchId));

        Model model = new ExtendedModelMap();
        assertThat(controller.feedPage(auth, model)).isEqualTo("feed");
        assertThat((List<?>) model.getAttribute("liveMatches")).isEmpty();
    }

    @Test
    void toggleMatchFollow_livePhases_sendInstantStatus() {
        assertThat(toggleLive(10, liveGoal(5, Half.FIRST, 1, 0)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(toggleLive(22).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(toggleLive(30, liveGoal(5, Half.FIRST, 1, 0), liveGoal(3, Half.SECOND, 1, 1))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(toggleLive(50).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void toggleMatchFollow_instantStatusFails_stillReturnsOk() {
        UUID matchId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        when(notificationClient.getSubscriptions(userId)).thenReturn(List.of());
        when(matchService.findById(matchId)).thenThrow(new RuntimeException("gone"));

        assertThat(controller.toggleMatchFollow(matchId, auth).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<Map<String, Object>> toggleLive(long minutesAgo, GoalDto... goals) {
        UUID matchId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        when(notificationClient.getSubscriptions(userId)).thenReturn(List.of());
        MatchDto match = new MatchDto();
        match.setId(matchId);
        match.setHomeTeamName("Home");
        match.setAwayTeamName("Away");
        match.setPlayedAt(LocalDateTime.now().minusMinutes(minutesAgo));
        for (GoalDto g : goals) match.getGoalTimeline().add(g);
        when(matchService.findById(matchId)).thenReturn(match);
        return controller.toggleMatchFollow(matchId, auth);
    }

    private GoalDto liveGoal(int minute, Half half, int rh, int ra) {
        GoalDto g = new GoalDto();
        g.setMinute(minute);
        g.setHalf(half);
        g.setRunningHomeScore(rh);
        g.setRunningAwayScore(ra);
        return g;
    }

    private MatchDto feedMatch(UUID homeTeamId, LocalDateTime playedAt) {
        MatchDto m = new MatchDto();
        m.setId(UUID.randomUUID());
        m.setHomeTeamId(homeTeamId);
        m.setAwayTeamId(UUID.randomUUID());
        m.setPlayedAt(playedAt);
        return m;
    }

    @Test
    void liveToasts_nullAuth_returnsEmpty() {
        assertThat(controller.liveToasts(null)).isEmpty();
    }

    @Test
    void liveToasts_serviceDown_returnsEmpty() {
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        when(notificationClient.getNotifications(userId)).thenThrow(new RuntimeException("down"));

        assertThat(controller.liveToasts(auth)).isEmpty();
    }

    @Test
    void liveToasts_excludesNullAndStaleCreatedAt() {
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        NotificationDto nullCreated = recentNotification("GOAL", "x");
        nullCreated.setCreatedAt(null);
        NotificationDto stale = recentNotification("GOAL", "y");
        stale.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        when(notificationClient.getNotifications(userId)).thenReturn(List.of(nullCreated, stale));

        assertThat(controller.liveToasts(auth)).isEmpty();
    }

    @Test
    void toggleMatchFollow_existingSubsDontMatch_stillSubscribes() {
        UUID matchId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);

        SubscriptionDto differentType = new SubscriptionDto();
        differentType.setId(UUID.randomUUID());
        differentType.setEntityType("TEAM");
        differentType.setEntityId(matchId);
        SubscriptionDto differentId = new SubscriptionDto();
        differentId.setId(UUID.randomUUID());
        differentId.setEntityType("MATCH");
        differentId.setEntityId(UUID.randomUUID());
        when(notificationClient.getSubscriptions(userId)).thenReturn(List.of(differentType, differentId));

        MatchDto match = new MatchDto();
        match.setId(matchId);
        match.setHomeTeamName("Home");
        match.setAwayTeamName("Away");
        match.setPlayedAt(LocalDateTime.now().plusHours(1));
        when(matchService.findById(matchId)).thenReturn(match);

        ResponseEntity<Map<String, Object>> response = controller.toggleMatchFollow(matchId, auth);

        verify(notificationClient).subscribe(any(SubscriptionRequest.class));
        assertThat(response.getBody()).containsEntry("following", true);
    }

    @Test
    void toggleMatchFollow_serviceDown_returns503() {
        UUID matchId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        when(notificationClient.getSubscriptions(userId)).thenThrow(new RuntimeException("down"));

        ResponseEntity<Map<String, Object>> response = controller.toggleMatchFollow(matchId, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsKey("error");
    }

    @Test
    void feedPage_mixedSubscriptionTypes_handlesEdgeCases() {
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);

        SubscriptionDto teamNoLeague = new SubscriptionDto();
        teamNoLeague.setId(UUID.randomUUID());
        teamNoLeague.setEntityType("TEAM");
        teamNoLeague.setEntityId(teamId);
        SubscriptionDto unknownType = new SubscriptionDto();
        unknownType.setId(UUID.randomUUID());
        unknownType.setEntityType("PLAYER");
        unknownType.setEntityId(UUID.randomUUID());
        when(notificationClient.getSubscriptions(userId)).thenReturn(List.of(teamNoLeague, unknownType));

        TeamDto team = new TeamDto();
        team.setId(teamId);
        team.setName("NoLeagueFC");
        team.setLeagueId(null);
        when(teamService.findById(teamId)).thenReturn(team);
        when(matchFollowSupport.subscribedMatchIds(auth)).thenReturn(Set.of());

        Model model = new ExtendedModelMap();
        assertThat(controller.feedPage(auth, model)).isEqualTo("feed");
        assertThat((List<?>) model.getAttribute("teamViews")).hasSize(1);
        assertThat((List<?>) model.getAttribute("leagueViews")).isEmpty();
    }

    @Test
    void feedPage_liveGoalWithNullFields_appliesDefaults() {
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID leagueId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);

        SubscriptionDto teamSub = new SubscriptionDto();
        teamSub.setId(UUID.randomUUID());
        teamSub.setEntityType("TEAM");
        teamSub.setEntityId(teamId);
        when(notificationClient.getSubscriptions(userId)).thenReturn(List.of(teamSub));

        TeamDto team = new TeamDto();
        team.setId(teamId);
        team.setName("SofiaFC");
        team.setLeagueId(leagueId);
        when(teamService.findById(teamId)).thenReturn(team);

        LeagueDetailView league = org.mockito.Mockito.mock(LeagueDetailView.class);
        when(league.getName()).thenReturn("Premier");
        when(league.getStandings()).thenReturn(List.of());
        when(league.getTeams()).thenReturn(List.of(team));
        MatchDto live = feedMatch(teamId, LocalDateTime.now().minusMinutes(30));
        live.getGoalTimeline().add(new GoalDto());
        when(league.getMatches()).thenReturn(List.of(live));
        when(leagueService.findDetail(leagueId)).thenReturn(league);
        when(matchFollowSupport.subscribedMatchIds(auth)).thenReturn(Set.of());

        Model model = new ExtendedModelMap();
        assertThat(controller.feedPage(auth, model)).isEqualTo("feed");
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
    void feedPage_matchFiltering_homeAwayNeither_allWindows() {
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID leagueId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);

        SubscriptionDto teamSub = new SubscriptionDto();
        teamSub.setId(UUID.randomUUID());
        teamSub.setEntityType("TEAM");
        teamSub.setEntityId(teamId);
        when(notificationClient.getSubscriptions(userId)).thenReturn(List.of(teamSub));

        TeamDto team = new TeamDto();
        team.setId(teamId);
        team.setName("SofiaFC");
        team.setLeagueId(leagueId);
        when(teamService.findById(teamId)).thenReturn(team);

        MatchDto liveHome = feedMatch(teamId, LocalDateTime.now().minusMinutes(30));
        MatchDto liveAway = otherMatch(teamId, LocalDateTime.now().minusMinutes(35), false);
        MatchDto liveNeither = otherMatch(teamId, LocalDateTime.now().minusMinutes(40), true);
        MatchDto upcomingHome = feedMatch(teamId, LocalDateTime.now().plusDays(1));
        MatchDto upcomingAway = otherMatch(teamId, LocalDateTime.now().plusDays(2), false);
        MatchDto upcomingNeither = otherMatch(teamId, LocalDateTime.now().plusDays(3), true);
        MatchDto recentHome = feedMatch(teamId, LocalDateTime.now().minusDays(2));
        MatchDto recentAway = otherMatch(teamId, LocalDateTime.now().minusDays(3), false);
        MatchDto recentNeither = otherMatch(teamId, LocalDateTime.now().minusDays(4), true);
        MatchDto tooOld = feedMatch(teamId, LocalDateTime.now().minusDays(20));

        LeagueDetailView league = org.mockito.Mockito.mock(LeagueDetailView.class);
        when(league.getName()).thenReturn("Premier");
        when(league.getStandings()).thenReturn(List.of());
        when(league.getTeams()).thenReturn(List.of(team));
        when(league.getMatches()).thenReturn(List.of(liveHome, liveAway, liveNeither,
                upcomingHome, upcomingAway, upcomingNeither, recentHome, recentAway, recentNeither, tooOld));
        when(leagueService.findDetail(leagueId)).thenReturn(league);
        when(matchFollowSupport.subscribedMatchIds(auth)).thenReturn(Set.of());

        Model model = new ExtendedModelMap();
        assertThat(controller.feedPage(auth, model)).isEqualTo("feed");
        assertThat((List<?>) model.getAttribute("liveMatches")).hasSize(2);
        assertThat((List<?>) model.getAttribute("upcomingMatches")).hasSize(2);
        assertThat((List<?>) model.getAttribute("recentMatches")).hasSize(2);

        @SuppressWarnings("unchecked")
        List<NotificationController.SubscriptionView> teamViews =
                (List<NotificationController.SubscriptionView>) model.getAttribute("teamViews");
        assertThat(teamViews.get(0).remainingMatches()).isEqualTo(2);
    }

    @Test
    void toggleMatchFollow_liveStatusEdgeCases_coversEnrichmentBranches() {
        GoalDto allNull = new GoalDto();
        allNull.setHalf(Half.FIRST);
        assertThat(toggleLive(10, allNull).getStatusCode()).isEqualTo(HttpStatus.OK);

        GoalDto halfNull = new GoalDto();
        halfNull.setMinute(5);
        assertThat(toggleLive(10, halfNull).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(toggleLive(10, liveGoal(15, Half.FIRST, 5, 5)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(toggleLive(50, liveGoal(10, Half.SECOND, 4, 3)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(toggleLive(30, liveGoal(20, Half.SECOND, 1, 1)).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private MatchDto otherMatch(UUID followedTeamId, LocalDateTime playedAt, boolean neitherFollowed) {
        MatchDto m = new MatchDto();
        m.setId(UUID.randomUUID());
        if (neitherFollowed) {
            m.setHomeTeamId(UUID.randomUUID());
            m.setAwayTeamId(UUID.randomUUID());
        } else {
            m.setHomeTeamId(UUID.randomUUID());
            m.setAwayTeamId(followedTeamId);
        }
        m.setPlayedAt(playedAt);
        return m;
    }

    private SubscriptionRequest subReq(UUID userId, String entityType, UUID entityId) {
        return argThat(r -> r != null
                && userId.equals(r.getUserId())
                && entityType.equals(r.getEntityType())
                && entityId.equals(r.getEntityId()));
    }

    private Authentication authFor(String username, UUID userId) {
        User user = new User();
        user.setId(userId);
        when(userService.findByUsername(username)).thenReturn(user);
        Authentication auth = org.mockito.Mockito.mock(Authentication.class);
        when(auth.getName()).thenReturn(username);
        return auth;
    }
}
