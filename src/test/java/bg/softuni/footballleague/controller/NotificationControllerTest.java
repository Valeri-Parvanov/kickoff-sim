package bg.softuni.footballleague.controller;

import bg.softuni.footballleague.client.NotificationClient;
import bg.softuni.footballleague.client.NotificationDto;
import bg.softuni.footballleague.client.NotifyRequest;
import bg.softuni.footballleague.client.SubscriptionDto;
import bg.softuni.footballleague.client.SubscriptionRequest;
import bg.softuni.footballleague.dto.GoalDto;
import bg.softuni.footballleague.dto.LeagueDetailView;
import bg.softuni.footballleague.dto.MatchDto;
import bg.softuni.footballleague.dto.StandingRow;
import bg.softuni.footballleague.dto.TeamDto;
import bg.softuni.footballleague.model.Half;
import bg.softuni.footballleague.model.User;
import bg.softuni.footballleague.service.LeagueService;
import bg.softuni.footballleague.service.MatchService;
import bg.softuni.footballleague.service.TeamService;
import bg.softuni.footballleague.service.UserService;
import bg.softuni.footballleague.web.MatchFollowSupport;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks private NotificationController controller;

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
    @SuppressWarnings("unchecked")
    void notificationsPage_showsTenPerPage() {
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);

        List<NotificationDto> many = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            NotificationDto n = new NotificationDto();
            n.setId(UUID.randomUUID());
            n.setMessage("msg " + i);
            n.setType("GOAL");
            n.setCreatedAt(LocalDateTime.now().minusMinutes(i));
            many.add(n);
        }
        when(notificationClient.getNotifications(userId)).thenReturn(many);

        Model model = new ExtendedModelMap();
        controller.notificationsPage(auth, model, "all", 0);

        assertThat((List<Object>) model.getAttribute("notifications")).hasSize(10);
        assertThat(model.getAttribute("totalPages")).isEqualTo(3);
        assertThat(model.getAttribute("filteredCount")).isEqualTo(25);
    }

    @Test
    @SuppressWarnings("unchecked")
    void notificationsPage_enrichesNotificationWithTeamAndLeagueLinks() {
        UUID userId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);

        NotificationDto n = new NotificationDto();
        n.setId(UUID.randomUUID());
        n.setMatchId(matchId);
        n.setMessage("GOAL for Home! Ivan Petrov 5'");
        n.setType("GOAL");
        n.setCreatedAt(LocalDateTime.now());
        when(notificationClient.getNotifications(userId)).thenReturn(List.of(n));

        MatchDto match = new MatchDto();
        match.setId(matchId);
        match.setHomeTeamId(UUID.randomUUID());
        match.setHomeTeamName("Home");
        match.setHomeTeamCity("Sofia");
        match.setAwayTeamId(UUID.randomUUID());
        match.setAwayTeamName("Away");
        match.setLeagueId(UUID.randomUUID());
        match.setLeagueName("Premier Cup");
        when(matchService.findById(matchId)).thenReturn(match);

        Model model = new ExtendedModelMap();
        controller.notificationsPage(auth, model, "all", 0);

        List<NotificationController.NotificationView> views =
                (List<NotificationController.NotificationView>) model.getAttribute("notifications");
        assertThat(views).hasSize(1);
        assertThat(views.get(0).homeTeamName()).isEqualTo("Home (Sofia)");
        assertThat(views.get(0).awayTeamName()).isEqualTo("Away");
        assertThat(views.get(0).leagueName()).isEqualTo("Premier Cup");
        assertThat(views.get(0).matchId()).isEqualTo(matchId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void notificationsPage_matchLookupFails_notificationWithoutMatchDetails() {
        UUID userId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);

        NotificationDto n = new NotificationDto();
        n.setId(UUID.randomUUID());
        n.setMatchId(matchId);
        n.setMessage("GOAL for Home!");
        n.setType("GOAL");
        n.setCreatedAt(LocalDateTime.now());
        when(notificationClient.getNotifications(userId)).thenReturn(List.of(n));
        when(matchService.findById(matchId)).thenThrow(new RuntimeException("gone"));

        Model model = new ExtendedModelMap();
        controller.notificationsPage(auth, model, "all", 0);

        List<NotificationController.NotificationView> views =
                (List<NotificationController.NotificationView>) model.getAttribute("notifications");
        assertThat(views.get(0).homeTeamName()).isNull();
    }

    @Test
    void liveToasts_includesGoalHalftimeAndFulltimeButNotOtherTypes() {
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);

        when(notificationClient.getNotifications(userId)).thenReturn(List.of(
                unreadNotification("GOAL", "GOAL for Home!"),
                unreadNotification("MATCH_HALFTIME", "HALF TIME 1-0"),
                unreadNotification("MATCH_FULLTIME", "FULL TIME 2-1"),
                unreadNotification("MATCH_KICKOFF", "KICK OFF!"),
                unreadNotification("MATCH_UPDATE", "LIVE: Home 1:0 Away")));

        List<Map<String, Object>> toasts = controller.liveToasts(auth);

        assertThat(toasts).extracting(t -> t.get("type"))
                .containsExactlyInAnyOrder("GOAL", "MATCH_HALFTIME", "MATCH_FULLTIME");
    }

    @Test
    void liveToasts_skipsAlreadyReadNotifications() {
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);

        NotificationDto read = unreadNotification("GOAL", "GOAL for Home!");
        read.setRead(true);
        when(notificationClient.getNotifications(userId)).thenReturn(List.of(read));

        assertThat(controller.liveToasts(auth)).isEmpty();
    }

    private NotificationDto unreadNotification(String type, String message) {
        NotificationDto n = new NotificationDto();
        n.setId(UUID.randomUUID());
        n.setType(type);
        n.setMessage(message);
        n.setRead(false);
        n.setCreatedAt(LocalDateTime.now());
        return n;
    }

    @Test
    void markAllRead_callsClientAndKeepsStatusTab() {
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);

        String view = controller.markAllRead("unread", auth, new RedirectAttributesModelMap());

        verify(notificationClient).markAllRead(userId);
        assertThat(view).isEqualTo("redirect:/notifications?status=unread");
    }

    @Test
    void unreadCount_nullAuth_returnsZero() {
        assertThat(controller.unreadCount(null)).isZero();
    }

    @Test
    void unreadCount_returnsClientValue() {
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        when(notificationClient.getUnreadCount(userId)).thenReturn(7L);

        assertThat(controller.unreadCount(auth)).isEqualTo(7L);
    }

    @Test
    void unreadCount_serviceDown_returnsZero() {
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        when(notificationClient.getUnreadCount(userId)).thenThrow(new RuntimeException("down"));

        assertThat(controller.unreadCount(auth)).isZero();
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

        assertThat(view).isEqualTo("redirect:/notifications");
        assertThat(ra.getFlashAttributes()).containsKey("warnMessage");
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

        assertThat(view).isEqualTo("redirect:/notifications");
        assertThat(ra.getFlashAttributes()).containsKey("warnMessage");
    }

    @Test
    void markRead_buildsRedirectWithStatusAndPage() {
        UUID id = UUID.randomUUID();
        Authentication auth = authFor("alice", UUID.randomUUID());

        String view = controller.markRead(id, "unread", 2, auth);

        verify(notificationClient).markRead(id);
        assertThat(view).isEqualTo("redirect:/notifications?status=unread&page=2");
    }

    @Test
    void markRead_serviceDown_stillRedirects() {
        UUID id = UUID.randomUUID();
        Authentication auth = authFor("alice", UUID.randomUUID());
        doThrow(new RuntimeException("down")).when(notificationClient).markRead(id);

        String view = controller.markRead(id, null, null, auth);

        assertThat(view).isEqualTo("redirect:/notifications");
    }

    @Test
    void clearAll_success_setsStatusMessage() {
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String view = controller.clearAll(auth, ra);

        verify(notificationClient).clearAll(userId);
        assertThat(view).isEqualTo("redirect:/notifications");
        assertThat(ra.getFlashAttributes()).containsKey("statusMessage");
    }

    @Test
    void clearAll_serviceDown_setsWarn() {
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        doThrow(new RuntimeException("down")).when(notificationClient).clearAll(userId);

        controller.clearAll(auth, ra);

        assertThat(ra.getFlashAttributes()).containsKey("warnMessage");
    }

    @Test
    void subscriptionsPage_serviceDown_setsWarn() {
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        Model model = new ExtendedModelMap();
        when(notificationClient.getSubscriptions(userId)).thenThrow(new RuntimeException("down"));

        assertThat(controller.subscriptionsPage(auth, model)).isEqualTo("subscriptions");
        assertThat(model.getAttribute("warnMessage")).isNotNull();
    }

    @Test
    void subscriptionsPage_teamSubscription_buildsView() {
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        Model model = new ExtendedModelMap();

        SubscriptionDto sub = new SubscriptionDto();
        sub.setId(UUID.randomUUID());
        sub.setEntityType("TEAM");
        sub.setEntityId(teamId);
        when(notificationClient.getSubscriptions(userId)).thenReturn(List.of(sub));

        TeamDto team = new TeamDto();
        team.setId(teamId);
        team.setName("SofiaFC");
        team.setLeagueId(null);
        when(teamService.findById(teamId)).thenReturn(team);

        assertThat(controller.subscriptionsPage(auth, model)).isEqualTo("subscriptions");
        assertThat((List<?>) model.getAttribute("subscriptions")).hasSize(1);
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
    void subscriptionsPage_enrichFails_usesFallbackView() {
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        SubscriptionDto sub = new SubscriptionDto();
        sub.setId(UUID.randomUUID());
        sub.setEntityType("TEAM");
        sub.setEntityId(teamId);
        when(notificationClient.getSubscriptions(userId)).thenReturn(List.of(sub));
        when(teamService.findById(teamId)).thenThrow(new RuntimeException("gone"));

        Model model = new ExtendedModelMap();
        assertThat(controller.subscriptionsPage(auth, model)).isEqualTo("subscriptions");
        assertThat((List<?>) model.getAttribute("subscriptions")).hasSize(1);
    }

    @Test
    void notificationsPage_statusFilters_countCorrectly() {
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        NotificationDto unread = unreadNotification("GOAL", "a");
        NotificationDto read = unreadNotification("GOAL", "b");
        read.setRead(true);
        when(notificationClient.getNotifications(userId)).thenReturn(List.of(unread, read));

        Model m1 = new ExtendedModelMap();
        controller.notificationsPage(auth, m1, "unread", 0);
        assertThat(m1.getAttribute("filteredCount")).isEqualTo(1);

        Model m2 = new ExtendedModelMap();
        controller.notificationsPage(auth, m2, "read", 0);
        assertThat(m2.getAttribute("filteredCount")).isEqualTo(1);
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
    void notificationsPage_pageBeyondRange_clampsToLastPage() {
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        when(notificationClient.getNotifications(userId))
                .thenReturn(List.of(unreadNotification("GOAL", "one")));
        Model model = new ExtendedModelMap();

        controller.notificationsPage(auth, model, "all", 7);

        assertThat(model.getAttribute("currentPage")).isEqualTo(0);
    }

    @Test
    void markAllRead_nullStatus_redirectsPlain() {
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);

        assertThat(controller.markAllRead(null, auth, new RedirectAttributesModelMap()))
                .isEqualTo("redirect:/notifications");
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
    void notificationsPage_serviceDown_setsWarn() {
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        when(notificationClient.getNotifications(userId)).thenThrow(new RuntimeException("down"));
        Model model = new ExtendedModelMap();

        controller.notificationsPage(auth, model, "all", 0);

        assertThat(model.getAttribute("warnMessage")).isNotNull();
        assertThat(model.getAttribute("countAll")).isEqualTo(0L);
    }

    @Test
    void markAllRead_serviceDown_setsWarn() {
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        doThrow(new RuntimeException("down")).when(notificationClient).markAllRead(userId);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        controller.markAllRead("unread", auth, ra);

        assertThat(ra.getFlashAttributes()).containsKey("warnMessage");
    }

    @Test
    void markAllRead_blankStatus_redirectsPlain() {
        UUID userId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);

        assertThat(controller.markAllRead("   ", auth, new RedirectAttributesModelMap()))
                .isEqualTo("redirect:/notifications");
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
        NotificationDto nullCreated = unreadNotification("GOAL", "x");
        nullCreated.setCreatedAt(null);
        NotificationDto stale = unreadNotification("GOAL", "y");
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
    void markRead_blankStatusWithPage_usesQuestionMarkForPage() {
        UUID id = UUID.randomUUID();
        Authentication auth = authFor("alice", UUID.randomUUID());

        String view = controller.markRead(id, "", 3, auth);

        assertThat(view).isEqualTo("redirect:/notifications?page=3");
    }

    @Test
    void markRead_pageZero_omittedFromUrl() {
        UUID id = UUID.randomUUID();
        Authentication auth = authFor("alice", UUID.randomUUID());

        String view = controller.markRead(id, "unread", 0, auth);

        assertThat(view).isEqualTo("redirect:/notifications?status=unread");
    }

    @Test
    @SuppressWarnings("unchecked")
    void subscriptionsPage_teamNotInStandings_positionNull() {
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID leagueId = UUID.randomUUID();
        Authentication auth = authFor("alice", userId);
        SubscriptionDto sub = new SubscriptionDto();
        sub.setId(UUID.randomUUID());
        sub.setEntityType("TEAM");
        sub.setEntityId(teamId);
        when(notificationClient.getSubscriptions(userId)).thenReturn(List.of(sub));

        TeamDto team = new TeamDto();
        team.setId(teamId);
        team.setName("SofiaFC");
        team.setLeagueId(leagueId);
        when(teamService.findById(teamId)).thenReturn(team);

        LeagueDetailView league = org.mockito.Mockito.mock(LeagueDetailView.class);
        StandingRow otherRow = new StandingRow();
        otherRow.setTeamId(UUID.randomUUID());
        when(league.getStandings()).thenReturn(List.of(otherRow));
        when(league.getMatches()).thenReturn(List.of());
        when(league.getName()).thenReturn("Premier");
        when(leagueService.findDetail(leagueId)).thenReturn(league);

        Model model = new ExtendedModelMap();
        controller.subscriptionsPage(auth, model);

        List<NotificationController.SubscriptionView> views =
                (List<NotificationController.SubscriptionView>) model.getAttribute("subscriptions");
        assertThat(views.get(0).standingPosition()).isNull();
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

        LeagueDetailView league = org.mockito.Mockito.mock(LeagueDetailView.class);
        when(league.getName()).thenReturn("Premier");
        when(league.getStandings()).thenReturn(List.of());
        when(league.getTeams()).thenReturn(List.of(team));
        when(league.getMatches()).thenReturn(List.of(liveHome, liveAway, liveNeither,
                upcomingHome, upcomingAway, upcomingNeither, recentHome, recentAway, recentNeither));
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

    private Authentication authFor(String username, UUID userId) {
        User user = new User();
        user.setId(userId);
        when(userService.findByUsername(username)).thenReturn(user);
        Authentication auth = org.mockito.Mockito.mock(Authentication.class);
        when(auth.getName()).thenReturn(username);
        return auth;
    }
}
