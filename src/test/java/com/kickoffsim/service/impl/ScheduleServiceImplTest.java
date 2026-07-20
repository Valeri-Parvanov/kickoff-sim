package com.kickoffsim.service.impl;

import com.kickoffsim.client.BroadcastRequest;
import com.kickoffsim.client.NotificationClient;
import com.kickoffsim.client.SubscriptionDto;
import com.kickoffsim.client.SubscriptionRequest;
import com.kickoffsim.exception.EntityNotFoundException;
import com.kickoffsim.exception.InvalidLeagueOperationException;
import com.kickoffsim.model.Goal;
import com.kickoffsim.model.Half;
import com.kickoffsim.model.League;
import com.kickoffsim.model.Match;
import com.kickoffsim.model.Player;
import com.kickoffsim.model.Team;
import com.kickoffsim.repository.GoalRepository;
import com.kickoffsim.repository.LeagueRepository;
import com.kickoffsim.repository.MatchRepository;
import com.kickoffsim.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScheduleServiceImplTest {

    @Mock private LeagueRepository leagueRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private GoalRepository goalRepository;
    @Mock private NotificationClient notificationClient;

    @InjectMocks
    private ScheduleServiceImpl scheduleService;

    private static final LocalDate START_DATE = LocalDate.of(2026, 1, 1);
    private static final LocalTime VALID_TIME = LocalTime.of(11, 0);

    @Test
    void generate_leagueNotFound_throwsEntityNotFoundException() {
        UUID id = UUID.randomUUID();
        when(leagueRepository.findByIdWithTeams(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.generate(id, START_DATE, VALID_TIME))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void generate_invalidTeamCount_throwsInvalidLeagueOperationException() {
        UUID id = UUID.randomUUID();
        when(leagueRepository.findByIdWithTeams(id)).thenReturn(Optional.of(leagueWith(id, 5)));

        assertThatThrownBy(() -> scheduleService.generate(id, START_DATE, VALID_TIME))
                .isInstanceOf(InvalidLeagueOperationException.class)
                .hasMessageContaining("6, 8, 10, or 16");
    }

    @Test
    void generate_startTimeNotOnQuarterMark_throwsInvalidLeagueOperationException() {
        UUID id = UUID.randomUUID();
        when(leagueRepository.findByIdWithTeams(id)).thenReturn(Optional.of(leagueWith(id, 6)));

        assertThatThrownBy(() -> scheduleService.generate(id, START_DATE, LocalTime.of(11, 7)))
                .isInstanceOf(InvalidLeagueOperationException.class)
                .hasMessageContaining("15-minute");
    }

    @Test
    void generate_startTimeBeforeEight_throwsInvalidLeagueOperationException() {
        UUID id = UUID.randomUUID();
        when(leagueRepository.findByIdWithTeams(id)).thenReturn(Optional.of(leagueWith(id, 6)));

        assertThatThrownBy(() -> scheduleService.generate(id, START_DATE, LocalTime.of(7, 0)))
                .isInstanceOf(InvalidLeagueOperationException.class)
                .hasMessageContaining("08:00");
    }

    @Test
    void generate_startTimeTooLate_throwsInvalidLeagueOperationException() {
        UUID id = UUID.randomUUID();
        when(leagueRepository.findByIdWithTeams(id)).thenReturn(Optional.of(leagueWith(id, 6)));

        assertThatThrownBy(() -> scheduleService.generate(id, START_DATE, LocalTime.of(22, 0)))
                .isInstanceOf(InvalidLeagueOperationException.class)
                .hasMessageContaining("too late");
    }

    @Test
    void generate_scheduleAlreadyExists_throwsInvalidLeagueOperationException() {
        UUID id = UUID.randomUUID();
        when(leagueRepository.findByIdWithTeams(id)).thenReturn(Optional.of(leagueWith(id, 6)));
        when(matchRepository.existsByLeagueId(id)).thenReturn(true);

        assertThatThrownBy(() -> scheduleService.generate(id, START_DATE, VALID_TIME))
                .isInstanceOf(InvalidLeagueOperationException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @SuppressWarnings("unchecked")
    void generate_validSixTeamLeague_saves45Matches() {
        UUID id = UUID.randomUUID();
        when(leagueRepository.findByIdWithTeams(id)).thenReturn(Optional.of(leagueWith(id, 6)));
        when(matchRepository.existsByLeagueId(id)).thenReturn(false);
        when(playerRepository.findAllByTeam(any())).thenReturn(List.of());
        when(matchRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduleService.generate(id, START_DATE, VALID_TIME);

        ArgumentCaptor<List<Match>> captor = ArgumentCaptor.forClass(List.class);
        verify(matchRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(45);
    }

    @Test
    @SuppressWarnings("unchecked")
    void generate_withRealSquads_simulatesGoalsAcrossManyMatches() {
        UUID id = UUID.randomUUID();
        League league = leagueWith(id, 6);
        when(leagueRepository.findByIdWithTeams(id)).thenReturn(Optional.of(league));
        when(matchRepository.existsByLeagueId(id)).thenReturn(false);
        when(playerRepository.findAllByTeam(any())).thenAnswer(inv -> {
            Team t = inv.getArgument(0);
            return squadFor(t);
        });
        when(matchRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(goalRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduleService.generate(id, START_DATE, VALID_TIME);

        ArgumentCaptor<List<Match>> captor = ArgumentCaptor.forClass(List.class);
        verify(matchRepository).saveAll(captor.capture());
        long totalGoals = captor.getValue().stream().mapToLong(m -> m.getGoals().size()).sum();
        assertThat(totalGoals).isGreaterThan(0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void generate_leagueAndTeamFollowers_autoSubscribesToRelevantMatchesOnly() {
        UUID id = UUID.randomUUID();
        League league = leagueWith(id, 6);
        when(leagueRepository.findByIdWithTeams(id)).thenReturn(Optional.of(league));
        when(matchRepository.existsByLeagueId(id)).thenReturn(false);
        when(playerRepository.findAllByTeam(any())).thenReturn(List.of());
        when(matchRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID leagueFollowerUserId = UUID.randomUUID();
        UUID teamFollowerUserId = UUID.randomUUID();
        Team followedTeam = league.getTeams().get(0);

        SubscriptionDto leagueSub = new SubscriptionDto();
        leagueSub.setUserId(leagueFollowerUserId);
        leagueSub.setEntityType("LEAGUE");
        leagueSub.setEntityId(id);
        SubscriptionDto teamSub = new SubscriptionDto();
        teamSub.setUserId(teamFollowerUserId);
        teamSub.setEntityType("TEAM");
        teamSub.setEntityId(followedTeam.getId());
        when(notificationClient.getSubscriptionsForEntities(any())).thenReturn(List.of(leagueSub, teamSub));

        scheduleService.generate(id, START_DATE, VALID_TIME);

        ArgumentCaptor<SubscriptionRequest> captor = ArgumentCaptor.forClass(SubscriptionRequest.class);
        verify(notificationClient, org.mockito.Mockito.atLeastOnce()).subscribe(captor.capture());
        List<SubscriptionRequest> matchSubs = captor.getAllValues().stream()
                .filter(r -> "MATCH".equals(r.getEntityType()))
                .toList();

        long leagueFollowerMatchCount = matchSubs.stream()
                .filter(r -> leagueFollowerUserId.equals(r.getUserId())).count();
        long teamFollowerMatchCount = matchSubs.stream()
                .filter(r -> teamFollowerUserId.equals(r.getUserId())).count();

        assertThat(leagueFollowerMatchCount).isEqualTo(45);
        assertThat(teamFollowerMatchCount).isEqualTo(15);
    }

    @Test
    void generate_leagueSubscriptionForDifferentLeague_notSubscribed() {
        UUID id = UUID.randomUUID();
        League league = leagueWith(id, 6);
        when(leagueRepository.findByIdWithTeams(id)).thenReturn(Optional.of(league));
        when(matchRepository.existsByLeagueId(id)).thenReturn(false);
        when(playerRepository.findAllByTeam(any())).thenReturn(List.of());
        when(matchRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionDto wrongLeagueSub = new SubscriptionDto();
        wrongLeagueSub.setUserId(UUID.randomUUID());
        wrongLeagueSub.setEntityType("LEAGUE");
        wrongLeagueSub.setEntityId(UUID.randomUUID());
        when(notificationClient.getSubscriptionsForEntities(any())).thenReturn(List.of(wrongLeagueSub));

        scheduleService.generate(id, START_DATE, VALID_TIME);

        verify(notificationClient, never()).subscribe(any());
    }

    @Test
    void generate_autoSubscribeFails_logsWarningAndContinues() {
        UUID id = UUID.randomUUID();
        League league = leagueWith(id, 6);
        when(leagueRepository.findByIdWithTeams(id)).thenReturn(Optional.of(league));
        when(matchRepository.existsByLeagueId(id)).thenReturn(false);
        when(playerRepository.findAllByTeam(any())).thenReturn(List.of());
        when(matchRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionDto leagueSub = new SubscriptionDto();
        leagueSub.setUserId(UUID.randomUUID());
        leagueSub.setEntityType("LEAGUE");
        leagueSub.setEntityId(id);
        when(notificationClient.getSubscriptionsForEntities(any())).thenReturn(List.of(leagueSub));
        doThrow(new RuntimeException("subscribe failed")).when(notificationClient).subscribe(any());

        scheduleService.generate(id, START_DATE, VALID_TIME);

        verify(matchRepository).saveAll(any());
    }

    @Test
    void generate_followersLookupFails_generationStillSucceeds() {
        UUID id = UUID.randomUUID();
        when(leagueRepository.findByIdWithTeams(id)).thenReturn(Optional.of(leagueWith(id, 6)));
        when(matchRepository.existsByLeagueId(id)).thenReturn(false);
        when(playerRepository.findAllByTeam(any())).thenReturn(List.of());
        when(matchRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notificationClient.getSubscriptionsForEntities(any())).thenThrow(new RuntimeException("down"));

        scheduleService.generate(id, START_DATE, VALID_TIME);

        verify(matchRepository).saveAll(any());
        verify(notificationClient, never()).subscribe(any());
    }

    private List<Player> squadFor(Team team) {
        int idx = Integer.parseInt(team.getName().replace("Team ", ""));
        int count = switch (idx) {
            case 0 -> 0;
            case 1 -> 1;
            default -> 4;
        };
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Player p = new Player();
            p.setId(UUID.randomUUID());
            p.setFirstName("First" + i);
            p.setLastName("Last" + i);
            p.setTeam(team);
            players.add(p);
        }
        return players;
    }

    @Test
    void tryAutoGenerate_leagueNotFound_doesNothing() {
        UUID id = UUID.randomUUID();
        when(leagueRepository.findById(id)).thenReturn(Optional.empty());

        scheduleService.tryAutoGenerate(id);

        verify(matchRepository, never()).existsByLeagueId(any());
    }

    @Test
    void tryAutoGenerate_noScheduleStartDate_doesNothing() {
        UUID id = UUID.randomUUID();
        League league = leagueWith(id, 6);
        league.setScheduleStartDate(null);
        when(leagueRepository.findById(id)).thenReturn(Optional.of(league));

        scheduleService.tryAutoGenerate(id);

        verify(matchRepository, never()).existsByLeagueId(any());
    }

    @Test
    void tryAutoGenerate_scheduleAlreadyExists_doesNothing() {
        UUID id = UUID.randomUUID();
        League league = leagueWith(id, 6);
        league.setScheduleStartDate(START_DATE);
        when(leagueRepository.findById(id)).thenReturn(Optional.of(league));
        when(matchRepository.existsByLeagueId(id)).thenReturn(true);

        scheduleService.tryAutoGenerate(id);

        verify(matchRepository, never()).saveAll(any());
    }

    @Test
    void simulatePastMatches_noCandidates_nothingSaved() {
        when(matchRepository.findGoallessBefore(any())).thenReturn(List.of());

        scheduleService.simulatePastMatches();

        verify(matchRepository, never()).save(any());
        verify(goalRepository, never()).saveAll(any());
    }

    @Test
    void simulatePastMatches_withCandidate_savesMatch() {
        Match match = new Match();
        match.setId(UUID.randomUUID());
        Team home = new Team();
        home.setId(UUID.randomUUID());
        home.setName("Home");
        Team away = new Team();
        away.setId(UUID.randomUUID());
        away.setName("Away");
        match.setHomeTeam(home);
        match.setAwayTeam(away);
        match.setHomeScore(0);
        match.setAwayScore(0);

        when(matchRepository.findGoallessBefore(any())).thenReturn(List.of(match));
        when(playerRepository.findAllByTeam(any())).thenReturn(List.of());
        when(matchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduleService.simulatePastMatches();

        verify(matchRepository).save(match);
    }

    @Test
    void simulatePastMatches_withRealSquads_savesGeneratedGoals() {
        Team home = new Team();
        home.setId(UUID.randomUUID());
        home.setName("Home");
        Team away = new Team();
        away.setId(UUID.randomUUID());
        away.setName("Away");
        Match match = new Match();
        match.setId(UUID.randomUUID());
        match.setHomeTeam(home);
        match.setAwayTeam(away);
        match.setHomeScore(0);
        match.setAwayScore(0);

        List<Match> candidates = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Match m = new Match();
            m.setId(UUID.randomUUID());
            m.setHomeTeam(home);
            m.setAwayTeam(away);
            m.setHomeScore(0);
            m.setAwayScore(0);
            candidates.add(m);
        }
        List<Player> homePlayers = squadOfFour(home);
        List<Player> awayPlayers = squadOfFour(away);

        when(matchRepository.findGoallessBefore(any())).thenReturn(candidates);
        when(playerRepository.findAllByTeam(home)).thenReturn(homePlayers);
        when(playerRepository.findAllByTeam(away)).thenReturn(awayPlayers);
        when(matchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(goalRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduleService.simulatePastMatches();

        long totalGoals = candidates.stream().mapToLong(m -> m.getGoals().size()).sum();
        assertThat(totalGoals).isGreaterThan(0);
        verify(goalRepository).saveAll(any());
    }

    private List<Player> squadOfFour(Team team) {
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Player p = new Player();
            p.setId(UUID.randomUUID());
            p.setFirstName("First" + i);
            p.setLastName("Last" + i);
            p.setTeam(team);
            players.add(p);
        }
        return players;
    }

    @Test
    void notifyMatchEvents_withKickoff_broadcastsAndSetsFlag() {
        Match match = matchWithTeams("Home", "Away");
        League league = new League();
        league.setId(UUID.randomUUID());
        match.getHomeTeam().setLeague(league);
        when(matchRepository.findForKickoffNotification(any(), any())).thenReturn(List.of(match));
        when(matchRepository.findForHalftimeNotification(any(), any())).thenReturn(List.of());
        when(matchRepository.findForFulltimeNotification(any(), any())).thenReturn(List.of());
        when(matchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduleService.notifyMatchEvents();

        ArgumentCaptor<BroadcastRequest> captor = ArgumentCaptor.forClass(BroadcastRequest.class);
        verify(notificationClient).broadcast(captor.capture());
        assertThat(captor.getValue().getLeagueId()).isEqualTo(league.getId());
        assertThat(match.isKickoffNotified()).isTrue();
        verify(matchRepository).save(match);
    }

    @Test
    void notifyMatchEvents_noMatches_nothingBroadcast() {
        when(matchRepository.findForKickoffNotification(any(), any())).thenReturn(List.of());
        when(matchRepository.findForHalftimeNotification(any(), any())).thenReturn(List.of());
        when(matchRepository.findForFulltimeNotification(any(), any())).thenReturn(List.of());

        scheduleService.notifyMatchEvents();

        verify(notificationClient, never()).broadcast(any());
        verify(matchRepository, never()).save(any());
    }

    @Test
    void notifyMatchEvents_broadcastFails_flagNotSet() {
        Match match = matchWithTeams("Home", "Away");
        when(matchRepository.findForKickoffNotification(any(), any())).thenReturn(List.of(match));
        when(matchRepository.findForHalftimeNotification(any(), any())).thenReturn(List.of());
        when(matchRepository.findForFulltimeNotification(any(), any())).thenReturn(List.of());
        doThrow(new RuntimeException("service down")).when(notificationClient).broadcast(any());

        scheduleService.notifyMatchEvents();

        assertThat(match.isKickoffNotified()).isFalse();
        verify(matchRepository, never()).save(match);
    }

    @Test
    void notifyHalftimes_reportsFirstHalfScoreNotFinalScore() {
        Goal secondHalfGoal = liveGoal(22, Half.SECOND, 5);
        Match match = secondHalfGoal.getMatch();
        match.setHomeScore(1);
        match.setAwayScore(3);

        when(matchRepository.findForKickoffNotification(any(), any())).thenReturn(List.of());
        when(matchRepository.findForHalftimeNotification(any(), any())).thenReturn(List.of(match));
        when(matchRepository.findForFulltimeNotification(any(), any())).thenReturn(List.of());
        when(matchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduleService.notifyMatchEvents();

        ArgumentCaptor<BroadcastRequest> captor = ArgumentCaptor.forClass(BroadcastRequest.class);
        verify(notificationClient).broadcast(captor.capture());
        assertThat(captor.getValue().getMessage())
                .contains("HALF TIME 0-0")
                .doesNotContain("1-3");
    }

    @Test
    void notifyGoals_goalMinuteNotReachedYet_doesNotBroadcast() {
        Goal goal = liveGoal(2, Half.FIRST, 15);
        when(goalRepository.findUnnotifiedForMatchesStartedBetween(any(), any())).thenReturn(List.of(goal));

        scheduleService.notifyGoals();

        verify(notificationClient, never()).broadcast(any());
        verify(goalRepository, never()).save(any());
        assertThat(goal.isNotified()).isFalse();
    }

    @Test
    void notifyGoals_secondHalfGoalWaitsForHalftimeOffset() {
        Goal goal = liveGoal(26, Half.SECOND, 3);
        when(goalRepository.findUnnotifiedForMatchesStartedBetween(any(), any())).thenReturn(List.of(goal));

        scheduleService.notifyGoals();

        verify(notificationClient, never()).broadcast(any());
        assertThat(goal.isNotified()).isFalse();
    }

    @Test
    void notifyGoals_goalOlderThanFormerToastWindow_stillBroadcastsSoNothingIsLost() {
        Goal goal = liveGoal(40, Half.FIRST, 5);
        when(goalRepository.findUnnotifiedForMatchesStartedBetween(any(), any())).thenReturn(List.of(goal));
        when(goalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduleService.notifyGoals();

        verify(notificationClient).broadcast(any());
        assertThat(goal.isNotified()).isTrue();
    }

    @Test
    void notifyGoals_noUnnotifiedGoals_doesNothing() {
        when(goalRepository.findUnnotifiedForMatchesStartedBetween(any(), any())).thenReturn(List.of());

        scheduleService.notifyGoals();

        verify(notificationClient, never()).broadcast(any());
    }

    @Test
    void notifyGoals_secondHalfGoal_broadcastsWithOffsetMinute() {
        Goal goal = liveGoal(29, Half.SECOND, 3);
        when(goalRepository.findUnnotifiedForMatchesStartedBetween(any(), any())).thenReturn(List.of(goal));
        when(goalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduleService.notifyGoals();

        ArgumentCaptor<BroadcastRequest> captor = ArgumentCaptor.forClass(BroadcastRequest.class);
        verify(notificationClient).broadcast(captor.capture());
        assertThat(captor.getValue().getMessage()).contains(" 23'");
    }

    @Test
    void notifyGoals_laterGoalInSameMatch_excludedFromRunningScore() {
        Goal goalA = liveGoal(9, Half.FIRST, 6);
        Match match = goalA.getMatch();
        Player laterScorer = new Player();
        laterScorer.setId(UUID.randomUUID());
        laterScorer.setFirstName("Later");
        laterScorer.setLastName("Scorer");
        laterScorer.setTeam(match.getHomeTeam());
        Goal goalB = new Goal();
        goalB.setId(UUID.randomUUID());
        goalB.setMatch(match);
        goalB.setScorer(laterScorer);
        goalB.setHalf(Half.FIRST);
        goalB.setMinute(8);
        match.getGoals().add(goalB);

        when(goalRepository.findUnnotifiedForMatchesStartedBetween(any(), any())).thenReturn(List.of(goalA));
        when(goalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduleService.notifyGoals();

        ArgumentCaptor<BroadcastRequest> captor = ArgumentCaptor.forClass(BroadcastRequest.class);
        verify(notificationClient).broadcast(captor.capture());
        assertThat(captor.getValue().getMessage()).contains("1:0");
    }

    @Test
    void notifyGoals_explicitOffsetSeconds_usedInsteadOfMinute() {
        Goal goal = liveGoal(2, Half.FIRST, 1);
        goal.setOffsetSeconds(30);
        when(goalRepository.findUnnotifiedForMatchesStartedBetween(any(), any())).thenReturn(List.of(goal));
        when(goalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduleService.notifyGoals();

        verify(notificationClient).broadcast(any());
    }

    @Test
    void notifyGoals_withAssistant_includesAssistNameInMessage() {
        Goal goal = liveGoal(6, Half.FIRST, 5);
        Player assistant = new Player();
        assistant.setId(UUID.randomUUID());
        assistant.setFirstName("Assist");
        assistant.setLastName("Provider");
        goal.setAssistant(assistant);
        when(goalRepository.findUnnotifiedForMatchesStartedBetween(any(), any())).thenReturn(List.of(goal));
        when(goalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduleService.notifyGoals();

        ArgumentCaptor<BroadcastRequest> captor = ArgumentCaptor.forClass(BroadcastRequest.class);
        verify(notificationClient).broadcast(captor.capture());
        assertThat(captor.getValue().getMessage()).contains("assist: Assist Provider");
    }

    @Test
    void notifyGoals_goalJustHappened_broadcastsAndMarksNotified() {
        Goal goal = liveGoal(6, Half.FIRST, 5);
        when(goalRepository.findUnnotifiedForMatchesStartedBetween(any(), any())).thenReturn(List.of(goal));
        when(goalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduleService.notifyGoals();

        ArgumentCaptor<BroadcastRequest> captor = ArgumentCaptor.forClass(BroadcastRequest.class);
        verify(notificationClient).broadcast(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("GOAL");
        assertThat(captor.getValue().getMessage())
                .contains("GOAL for Home!")
                .contains("1:0")
                .contains("Ivan Petrov");
        assertThat(goal.isNotified()).isTrue();
    }

    @Test
    void tryAutoGenerate_invalidTeamCount_doesNothing() {
        UUID id = UUID.randomUUID();
        League league = leagueWith(id, 5);
        league.setScheduleStartDate(START_DATE);
        when(leagueRepository.findById(id)).thenReturn(Optional.of(league));

        scheduleService.tryAutoGenerate(id);

        verify(matchRepository, never()).existsByLeagueId(any());
    }

    @Test
    void tryAutoGenerate_invalidStartTime_logsAndSkips() {
        UUID id = UUID.randomUUID();
        League league = leagueWith(id, 6);
        league.setScheduleStartDate(START_DATE);
        league.setScheduleStartTime(LocalTime.of(7, 0));
        when(leagueRepository.findById(id)).thenReturn(Optional.of(league));
        when(matchRepository.existsByLeagueId(id)).thenReturn(false);

        scheduleService.tryAutoGenerate(id);

        verify(matchRepository, never()).saveAll(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void tryAutoGenerate_noExplicitStartTime_usesDefaultAndGenerates() {
        UUID id = UUID.randomUUID();
        League league = leagueWith(id, 6);
        league.setScheduleStartDate(START_DATE);
        league.setScheduleStartTime(null);
        when(leagueRepository.findById(id)).thenReturn(Optional.of(league));
        when(matchRepository.existsByLeagueId(id)).thenReturn(false);
        when(playerRepository.findAllByTeam(any())).thenReturn(List.of());
        when(matchRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduleService.tryAutoGenerate(id);

        ArgumentCaptor<List<Match>> captor = ArgumentCaptor.forClass(List.class);
        verify(matchRepository).saveAll(captor.capture());
        assertThat(captor.getValue().get(0).getPlayedAt().toLocalTime()).isEqualTo(LocalTime.of(11, 0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void tryAutoGenerate_explicitStartTime_usesIt() {
        UUID id = UUID.randomUUID();
        League league = leagueWith(id, 6);
        league.setScheduleStartDate(START_DATE);
        league.setScheduleStartTime(LocalTime.of(9, 0));
        when(leagueRepository.findById(id)).thenReturn(Optional.of(league));
        when(matchRepository.existsByLeagueId(id)).thenReturn(false);
        when(playerRepository.findAllByTeam(any())).thenReturn(List.of());
        when(matchRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduleService.tryAutoGenerate(id);

        ArgumentCaptor<List<Match>> captor = ArgumentCaptor.forClass(List.class);
        verify(matchRepository).saveAll(captor.capture());
        assertThat(captor.getValue().get(0).getPlayedAt().toLocalTime()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void simulatePastMatches_withLeague_broadcastsWithLeagueId() {
        League league = new League();
        league.setId(UUID.randomUUID());
        Match match = new Match();
        match.setId(UUID.randomUUID());
        Team home = new Team();
        home.setId(UUID.randomUUID());
        home.setName("Home");
        home.setLeague(league);
        Team away = new Team();
        away.setId(UUID.randomUUID());
        away.setName("Away");
        match.setHomeTeam(home);
        match.setAwayTeam(away);
        match.setHomeScore(0);
        match.setAwayScore(0);

        when(matchRepository.findGoallessBefore(any())).thenReturn(List.of(match));
        when(playerRepository.findAllByTeam(any())).thenReturn(List.of());
        when(matchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduleService.simulatePastMatches();

        ArgumentCaptor<BroadcastRequest> captor = ArgumentCaptor.forClass(BroadcastRequest.class);
        verify(notificationClient).broadcast(captor.capture());
        assertThat(captor.getValue().getLeagueId()).isEqualTo(league.getId());
    }

    @Test
    void simulatePastMatches_broadcastFails_stillSavesMatch() {
        Match match = new Match();
        match.setId(UUID.randomUUID());
        Team home = new Team();
        home.setId(UUID.randomUUID());
        home.setName("Home");
        Team away = new Team();
        away.setId(UUID.randomUUID());
        away.setName("Away");
        match.setHomeTeam(home);
        match.setAwayTeam(away);
        match.setHomeScore(0);
        match.setAwayScore(0);

        when(matchRepository.findGoallessBefore(any())).thenReturn(List.of(match));
        when(playerRepository.findAllByTeam(any())).thenReturn(List.of());
        when(matchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("down")).when(notificationClient).broadcast(any());

        scheduleService.simulatePastMatches();

        verify(matchRepository).save(match);
    }

    @Test
    void notifyHalftimes_awayGoalAndCityLabel_countsAwaySide() {
        Goal g = liveGoal(22, Half.SECOND, 5);
        Match match = g.getMatch();
        match.getHomeTeam().setCity("Sofia");
        Player awayScorer = new Player();
        awayScorer.setId(UUID.randomUUID());
        awayScorer.setFirstName("Georgi");
        awayScorer.setLastName("Georgiev");
        awayScorer.setTeam(match.getAwayTeam());
        Goal firstHalfAway = new Goal();
        firstHalfAway.setId(UUID.randomUUID());
        firstHalfAway.setMatch(match);
        firstHalfAway.setScorer(awayScorer);
        firstHalfAway.setHalf(Half.FIRST);
        firstHalfAway.setMinute(10);
        match.getGoals().add(firstHalfAway);
        match.setHomeScore(1);
        match.setAwayScore(3);

        when(matchRepository.findForKickoffNotification(any(), any())).thenReturn(List.of());
        when(matchRepository.findForHalftimeNotification(any(), any())).thenReturn(List.of(match));
        when(matchRepository.findForFulltimeNotification(any(), any())).thenReturn(List.of());
        when(matchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduleService.notifyMatchEvents();

        ArgumentCaptor<BroadcastRequest> captor = ArgumentCaptor.forClass(BroadcastRequest.class);
        verify(notificationClient).broadcast(captor.capture());
        assertThat(captor.getValue().getMessage())
                .contains("HALF TIME 0-1")
                .contains("Home (Sofia)");
    }

    @Test
    void notifyHalftimes_broadcastFails_flagNotSet() {
        Goal g = liveGoal(22, Half.SECOND, 5);
        Match match = g.getMatch();
        when(matchRepository.findForKickoffNotification(any(), any())).thenReturn(List.of());
        when(matchRepository.findForHalftimeNotification(any(), any())).thenReturn(List.of(match));
        when(matchRepository.findForFulltimeNotification(any(), any())).thenReturn(List.of());
        doThrow(new RuntimeException("down")).when(notificationClient).broadcast(any());

        scheduleService.notifyMatchEvents();

        assertThat(match.isHalftimeNotified()).isFalse();
        verify(matchRepository, never()).save(match);
    }

    @Test
    void notifySecondHalfStarts_reportsFirstHalfScoreNotFinalScore() {
        Goal firstHalfGoal = liveGoal(27, Half.FIRST, 10);
        Match match = firstHalfGoal.getMatch();
        match.setHomeScore(1);
        match.setAwayScore(3);
        Goal secondHalfGoal = new Goal();
        secondHalfGoal.setId(UUID.randomUUID());
        secondHalfGoal.setMatch(match);
        secondHalfGoal.setScorer(firstHalfGoal.getScorer());
        secondHalfGoal.setHalf(Half.SECOND);
        secondHalfGoal.setMinute(5);
        match.getGoals().add(secondHalfGoal);

        when(matchRepository.findForKickoffNotification(any(), any())).thenReturn(List.of());
        when(matchRepository.findForHalftimeNotification(any(), any())).thenReturn(List.of());
        when(matchRepository.findForSecondHalfNotification(any(), any())).thenReturn(List.of(match));
        when(matchRepository.findForFulltimeNotification(any(), any())).thenReturn(List.of());
        when(matchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduleService.notifyMatchEvents();

        ArgumentCaptor<BroadcastRequest> captor = ArgumentCaptor.forClass(BroadcastRequest.class);
        verify(notificationClient).broadcast(captor.capture());
        assertThat(captor.getValue().getMessage())
                .contains("SECOND HALF 1-0")
                .doesNotContain("1-3");
        assertThat(match.isSecondHalfNotified()).isTrue();
        verify(matchRepository).save(match);
    }

    @Test
    void notifySecondHalfStarts_awayGoalAndCityLabel_countsAwaySide() {
        Goal g = liveGoal(27, Half.FIRST, 10);
        Match match = g.getMatch();
        match.getHomeTeam().setCity("Sofia");
        Player awayScorer = new Player();
        awayScorer.setId(UUID.randomUUID());
        awayScorer.setFirstName("Georgi");
        awayScorer.setLastName("Georgiev");
        awayScorer.setTeam(match.getAwayTeam());
        Goal firstHalfAway = new Goal();
        firstHalfAway.setId(UUID.randomUUID());
        firstHalfAway.setMatch(match);
        firstHalfAway.setScorer(awayScorer);
        firstHalfAway.setHalf(Half.FIRST);
        firstHalfAway.setMinute(15);
        match.getGoals().add(firstHalfAway);

        when(matchRepository.findForKickoffNotification(any(), any())).thenReturn(List.of());
        when(matchRepository.findForHalftimeNotification(any(), any())).thenReturn(List.of());
        when(matchRepository.findForSecondHalfNotification(any(), any())).thenReturn(List.of(match));
        when(matchRepository.findForFulltimeNotification(any(), any())).thenReturn(List.of());
        when(matchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduleService.notifyMatchEvents();

        ArgumentCaptor<BroadcastRequest> captor = ArgumentCaptor.forClass(BroadcastRequest.class);
        verify(notificationClient).broadcast(captor.capture());
        assertThat(captor.getValue().getMessage())
                .contains("SECOND HALF 1-1")
                .contains("Home (Sofia)");
    }

    @Test
    void notifySecondHalfStarts_broadcastFails_flagNotSet() {
        Goal g = liveGoal(27, Half.FIRST, 10);
        Match match = g.getMatch();
        when(matchRepository.findForKickoffNotification(any(), any())).thenReturn(List.of());
        when(matchRepository.findForHalftimeNotification(any(), any())).thenReturn(List.of());
        when(matchRepository.findForSecondHalfNotification(any(), any())).thenReturn(List.of(match));
        when(matchRepository.findForFulltimeNotification(any(), any())).thenReturn(List.of());
        doThrow(new RuntimeException("down")).when(notificationClient).broadcast(any());

        scheduleService.notifyMatchEvents();

        assertThat(match.isSecondHalfNotified()).isFalse();
        verify(matchRepository, never()).save(match);
    }

    @Test
    void notifyFulltimes_broadcastsFullTimeScoreAndSetsFlag() {
        Match match = matchWithTeams("Home", "Away");
        match.setHomeScore(2);
        match.setAwayScore(1);
        when(matchRepository.findForKickoffNotification(any(), any())).thenReturn(List.of());
        when(matchRepository.findForHalftimeNotification(any(), any())).thenReturn(List.of());
        when(matchRepository.findForFulltimeNotification(any(), any())).thenReturn(List.of(match));
        when(matchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduleService.notifyMatchEvents();

        ArgumentCaptor<BroadcastRequest> captor = ArgumentCaptor.forClass(BroadcastRequest.class);
        verify(notificationClient).broadcast(captor.capture());
        assertThat(captor.getValue().getMessage()).contains("FULL TIME 2-1");
        assertThat(match.isFulltimeNotified()).isTrue();
    }

    @Test
    void notifyFulltimes_broadcastFails_flagNotSet() {
        Match match = matchWithTeams("Home", "Away");
        when(matchRepository.findForKickoffNotification(any(), any())).thenReturn(List.of());
        when(matchRepository.findForHalftimeNotification(any(), any())).thenReturn(List.of());
        when(matchRepository.findForFulltimeNotification(any(), any())).thenReturn(List.of(match));
        doThrow(new RuntimeException("down")).when(notificationClient).broadcast(any());

        scheduleService.notifyMatchEvents();

        assertThat(match.isFulltimeNotified()).isFalse();
        verify(matchRepository, never()).save(match);
    }

    @Test
    void notifyGoals_awayScorer_broadcastsAwayTeamMessage() {
        Goal goal = liveGoal(6, Half.FIRST, 5);
        Match match = goal.getMatch();
        Player awayScorer = new Player();
        awayScorer.setId(UUID.randomUUID());
        awayScorer.setFirstName("Georgi");
        awayScorer.setLastName("Georgiev");
        awayScorer.setTeam(match.getAwayTeam());
        goal.setScorer(awayScorer);
        when(goalRepository.findUnnotifiedForMatchesStartedBetween(any(), any())).thenReturn(List.of(goal));
        when(goalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduleService.notifyGoals();

        ArgumentCaptor<BroadcastRequest> captor = ArgumentCaptor.forClass(BroadcastRequest.class);
        verify(notificationClient).broadcast(captor.capture());
        assertThat(captor.getValue().getMessage()).contains("GOAL for Away!").contains("0:1");
    }

    @Test
    void notifyGoals_ownGoal_broadcastsOwnGoalMessage() {
        Goal goal = liveGoal(6, Half.FIRST, 5);
        goal.setOwnGoal(true);
        when(goalRepository.findUnnotifiedForMatchesStartedBetween(any(), any())).thenReturn(List.of(goal));
        when(goalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduleService.notifyGoals();

        ArgumentCaptor<BroadcastRequest> captor = ArgumentCaptor.forClass(BroadcastRequest.class);
        verify(notificationClient).broadcast(captor.capture());
        assertThat(captor.getValue().getMessage()).contains("(own goal)");
    }

    @Test
    void notifyGoals_penalty_broadcastsPenaltyMessage() {
        Goal goal = liveGoal(6, Half.FIRST, 5);
        goal.setPenalty(true);
        when(goalRepository.findUnnotifiedForMatchesStartedBetween(any(), any())).thenReturn(List.of(goal));
        when(goalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduleService.notifyGoals();

        ArgumentCaptor<BroadcastRequest> captor = ArgumentCaptor.forClass(BroadcastRequest.class);
        verify(notificationClient).broadcast(captor.capture());
        assertThat(captor.getValue().getMessage()).contains("(penalty)");
    }

    @Test
    void notifyGoals_nullMinute_defaultsToZero() {
        Goal goal = liveGoal(2, Half.FIRST, 5);
        goal.setMinute(null);
        when(goalRepository.findUnnotifiedForMatchesStartedBetween(any(), any())).thenReturn(List.of(goal));
        when(goalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduleService.notifyGoals();

        ArgumentCaptor<BroadcastRequest> captor = ArgumentCaptor.forClass(BroadcastRequest.class);
        verify(notificationClient).broadcast(captor.capture());
        assertThat(captor.getValue().getMessage()).contains(" 0'");
    }

    @Test
    void notifyGoals_broadcastFails_logsWarning() {
        Goal goal = liveGoal(6, Half.FIRST, 5);
        when(goalRepository.findUnnotifiedForMatchesStartedBetween(any(), any())).thenReturn(List.of(goal));
        doThrow(new RuntimeException("down")).when(notificationClient).broadcast(any());

        scheduleService.notifyGoals();

        assertThat(goal.isNotified()).isFalse();
        verify(goalRepository, never()).save(any());
    }

    private Goal liveGoal(int startedMinutesAgo, Half half, int minute) {
        Team home = new Team();
        home.setId(UUID.randomUUID());
        home.setName("Home");
        Team away = new Team();
        away.setId(UUID.randomUUID());
        away.setName("Away");

        Match match = new Match();
        match.setId(UUID.randomUUID());
        match.setHomeTeam(home);
        match.setAwayTeam(away);
        match.setPlayedAt(LocalDateTime.now().minusMinutes(startedMinutesAgo));

        Player scorer = new Player();
        scorer.setId(UUID.randomUUID());
        scorer.setFirstName("Ivan");
        scorer.setLastName("Petrov");
        scorer.setTeam(home);

        Goal goal = new Goal();
        goal.setId(UUID.randomUUID());
        goal.setMatch(match);
        goal.setScorer(scorer);
        goal.setHalf(half);
        goal.setMinute(minute);
        match.getGoals().add(goal);
        return goal;
    }

    private Match matchWithTeams(String homeName, String awayName) {
        Team home = new Team();
        home.setId(UUID.randomUUID());
        home.setName(homeName);
        Team away = new Team();
        away.setId(UUID.randomUUID());
        away.setName(awayName);
        Match match = new Match();
        match.setId(UUID.randomUUID());
        match.setHomeTeam(home);
        match.setAwayTeam(away);
        match.setHomeScore(0);
        match.setAwayScore(0);
        return match;
    }

    private League leagueWith(UUID id, int teamCount) {
        League league = new League();
        league.setId(id);
        league.setName("Test League");
        List<Team> teams = new ArrayList<>();
        for (int i = 0; i < teamCount; i++) {
            Team t = new Team();
            t.setId(UUID.randomUUID());
            t.setName("Team " + i);
            teams.add(t);
        }
        league.getTeams().addAll(teams);
        return league;
    }
}
