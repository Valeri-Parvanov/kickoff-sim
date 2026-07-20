package com.kickoffsim.service.impl;

import com.kickoffsim.dto.GoalDto;
import com.kickoffsim.dto.GoalEventDto;
import com.kickoffsim.dto.MatchDto;
import com.kickoffsim.exception.EntityNotFoundException;
import com.kickoffsim.exception.InvalidGoalException;
import com.kickoffsim.model.Goal;
import com.kickoffsim.model.Half;
import com.kickoffsim.model.League;
import com.kickoffsim.model.Match;
import com.kickoffsim.model.Player;
import com.kickoffsim.model.Team;
import com.kickoffsim.client.BroadcastRequest;
import com.kickoffsim.client.NotificationClient;
import com.kickoffsim.repository.GoalRepository;
import com.kickoffsim.repository.MatchRepository;
import com.kickoffsim.repository.PlayerRepository;
import com.kickoffsim.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchServiceImplTest {

    @Mock private MatchRepository matchRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private GoalRepository goalRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private NotificationClient notificationClient;

    @InjectMocks
    private MatchServiceImpl matchService;

    private UUID matchId;
    private UUID homeTeamId;
    private UUID awayTeamId;
    private UUID homePlayerId;
    private UUID awayPlayerId;

    private Match match;
    private Team homeTeam;
    private Team awayTeam;
    private Player homePlayer;
    private Player awayPlayer;

    @BeforeEach
    void setUp() {
        matchId      = UUID.randomUUID();
        homeTeamId   = UUID.randomUUID();
        awayTeamId   = UUID.randomUUID();
        homePlayerId = UUID.randomUUID();
        awayPlayerId = UUID.randomUUID();

        homeTeam = new Team();
        homeTeam.setId(homeTeamId);

        awayTeam = new Team();
        awayTeam.setId(awayTeamId);

        homePlayer = new Player();
        homePlayer.setId(homePlayerId);
        homePlayer.setFirstName("Ivan");
        homePlayer.setLastName("Ivanov");
        homePlayer.setTeam(homeTeam);

        awayPlayer = new Player();
        awayPlayer.setId(awayPlayerId);
        awayPlayer.setFirstName("Georgi");
        awayPlayer.setLastName("Georgiev");
        awayPlayer.setTeam(awayTeam);

        match = new Match();
        match.setId(matchId);
        match.setHomeTeam(homeTeam);
        match.setAwayTeam(awayTeam);
        match.setHomeScore(2);
        match.setAwayScore(1);
        match.setPlayedAt(LocalDateTime.now().minusDays(1));
    }

    @ParameterizedTest(name = "minute={0} → half={1}, storedMinute={2}")
    @CsvSource({
            "1,  FIRST,  1",
            "20, FIRST,  20",
            "21, SECOND, 1",
            "40, SECOND, 20"
    })
    void addGoal_halfAndMinuteAreCalculatedCorrectly(int rawMinute, String expectedHalf, int expectedStoredMinute) {
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(playerRepository.findById(homePlayerId)).thenReturn(Optional.of(homePlayer));
        when(goalRepository.countGoalsBenefitingTeam(match, homeTeamId, null)).thenReturn(0L);

        GoalEventDto dto = new GoalEventDto();
        dto.setScorerId(homePlayerId);
        dto.setMinute(rawMinute);

        matchService.addGoal(matchId, dto);

        ArgumentCaptor<Goal> captor = ArgumentCaptor.forClass(Goal.class);
        verify(goalRepository).save(captor.capture());

        Goal saved = captor.getValue();
        assertThat(saved.getHalf()).isEqualTo(Half.valueOf(expectedHalf));
        assertThat(saved.getMinute()).isEqualTo(expectedStoredMinute);
    }

    @Test
    void addGoal_nullMinute_defaultsToFirstHalfWithNullMinute() {
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(playerRepository.findById(homePlayerId)).thenReturn(Optional.of(homePlayer));
        when(goalRepository.countGoalsBenefitingTeam(match, homeTeamId, null)).thenReturn(0L);

        GoalEventDto dto = new GoalEventDto();
        dto.setScorerId(homePlayerId);
        dto.setMinute(null);

        matchService.addGoal(matchId, dto);

        ArgumentCaptor<Goal> captor = ArgumentCaptor.forClass(Goal.class);
        verify(goalRepository).save(captor.capture());
        assertThat(captor.getValue().getHalf()).isEqualTo(Half.FIRST);
        assertThat(captor.getValue().getMinute()).isNull();
    }

    @Test
    void addGoal_scorerNotInMatch_throwsInvalidGoalException() {
        Player outsider = new Player();
        outsider.setId(UUID.randomUUID());
        outsider.setFirstName("Petar");
        outsider.setLastName("Petrov");
        Team otherTeam = new Team();
        otherTeam.setId(UUID.randomUUID());
        outsider.setTeam(otherTeam);

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(playerRepository.findById(outsider.getId())).thenReturn(Optional.of(outsider));

        GoalEventDto dto = new GoalEventDto();
        dto.setScorerId(outsider.getId());
        dto.setMinute(10);

        assertThatThrownBy(() -> matchService.addGoal(matchId, dto))
                .isInstanceOf(InvalidGoalException.class)
                .hasMessageContaining("does not play in this match");

        verify(goalRepository, never()).save(any());
    }

    @Test
    void addGoal_homeScoreLimitReached_throwsInvalidGoalException() {
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(playerRepository.findById(homePlayerId)).thenReturn(Optional.of(homePlayer));
        when(goalRepository.countGoalsBenefitingTeam(match, homeTeamId, null)).thenReturn(2L);

        GoalEventDto dto = new GoalEventDto();
        dto.setScorerId(homePlayerId);
        dto.setMinute(5);

        assertThatThrownBy(() -> matchService.addGoal(matchId, dto))
                .isInstanceOf(InvalidGoalException.class)
                .hasMessageContaining("home score is 2");
    }

    @Test
    void addGoal_awayScoreLimitReached_throwsInvalidGoalException() {
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(playerRepository.findById(awayPlayerId)).thenReturn(Optional.of(awayPlayer));
        when(goalRepository.countGoalsBenefitingTeam(match, awayTeamId, null)).thenReturn(1L);

        GoalEventDto dto = new GoalEventDto();
        dto.setScorerId(awayPlayerId);
        dto.setMinute(15);

        assertThatThrownBy(() -> matchService.addGoal(matchId, dto))
                .isInstanceOf(InvalidGoalException.class)
                .hasMessageContaining("away score is 1");
    }

    @Test
    void addGoal_assistantFromWrongTeam_throwsInvalidGoalException() {
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(playerRepository.findById(homePlayerId)).thenReturn(Optional.of(homePlayer));
        when(playerRepository.findById(awayPlayerId)).thenReturn(Optional.of(awayPlayer));
        when(goalRepository.countGoalsBenefitingTeam(match, homeTeamId, null)).thenReturn(0L);

        GoalEventDto dto = new GoalEventDto();
        dto.setScorerId(homePlayerId);
        dto.setAssistantId(awayPlayerId);
        dto.setMinute(10);

        assertThatThrownBy(() -> matchService.addGoal(matchId, dto))
                .isInstanceOf(InvalidGoalException.class)
                .hasMessageContaining("same team as the scorer");
    }

    @Test
    void addGoal_scorerAsOwnAssistant_throwsInvalidGoalException() {
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(playerRepository.findById(homePlayerId)).thenReturn(Optional.of(homePlayer));
        when(goalRepository.countGoalsBenefitingTeam(match, homeTeamId, null)).thenReturn(0L);

        GoalEventDto dto = new GoalEventDto();
        dto.setScorerId(homePlayerId);
        dto.setAssistantId(homePlayerId);
        dto.setMinute(10);

        assertThatThrownBy(() -> matchService.addGoal(matchId, dto))
                .isInstanceOf(InvalidGoalException.class)
                .hasMessageContaining("cannot assist his own goal");

        verify(goalRepository, never()).save(any());
    }

    @Test
    void addGoal_duplicateMinute_throwsInvalidGoalException() {
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(playerRepository.findById(homePlayerId)).thenReturn(Optional.of(homePlayer));
        when(goalRepository.countGoalsBenefitingTeam(match, homeTeamId, null)).thenReturn(0L);
        when(goalRepository.countByMatchAndHalfAndMinuteExcluding(match, Half.FIRST, 10, null)).thenReturn(1L);

        GoalEventDto dto = new GoalEventDto();
        dto.setScorerId(homePlayerId);
        dto.setMinute(10);

        assertThatThrownBy(() -> matchService.addGoal(matchId, dto))
                .isInstanceOf(InvalidGoalException.class)
                .hasMessageContaining("minute 10");

        verify(goalRepository, never()).save(any());
    }

    @Test
    void updateGoal_changingScorerToFullAwayTeam_throwsInvalidGoalException() {
        UUID goalId = UUID.randomUUID();

        Goal goal = goalWithScorer(homePlayer);
        goal.setId(goalId);
        goal.setMatch(match);

        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));
        when(playerRepository.findById(awayPlayerId)).thenReturn(Optional.of(awayPlayer));
        when(goalRepository.countGoalsBenefitingTeam(match, awayTeamId, goalId)).thenReturn(1L);

        GoalEventDto dto = new GoalEventDto();
        dto.setScorerId(awayPlayerId);
        dto.setMinute(10);

        assertThatThrownBy(() -> matchService.updateGoal(goalId, dto))
                .isInstanceOf(InvalidGoalException.class)
                .hasMessageContaining("away score is 1");
    }

    @Test
    void updateGoal_sameHomeScorer_doesNotCountOldGoalTwice() {
        UUID goalId = UUID.randomUUID();
        match.setHomeScore(1);

        Goal goal = goalWithScorer(homePlayer);
        goal.setId(goalId);
        goal.setMatch(match);

        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));
        when(playerRepository.findById(homePlayerId)).thenReturn(Optional.of(homePlayer));
        when(goalRepository.countGoalsBenefitingTeam(match, homeTeamId, goalId)).thenReturn(0L);

        GoalEventDto dto = new GoalEventDto();
        dto.setScorerId(homePlayerId);
        dto.setMinute(10);

        matchService.updateGoal(goalId, dto);

        verify(goalRepository).save(any(Goal.class));
    }

    @Test
    void updateGoal_scorerNotInMatch_throwsInvalidGoalException() {
        UUID goalId = UUID.randomUUID();
        Goal goal = goalWithScorer(homePlayer);
        goal.setId(goalId);
        goal.setMatch(match);

        Player outsider = new Player();
        outsider.setId(UUID.randomUUID());
        outsider.setFirstName("Petar");
        outsider.setLastName("Petrov");
        Team otherTeam = new Team();
        otherTeam.setId(UUID.randomUUID());
        outsider.setTeam(otherTeam);

        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));
        when(playerRepository.findById(outsider.getId())).thenReturn(Optional.of(outsider));

        GoalEventDto dto = new GoalEventDto();
        dto.setScorerId(outsider.getId());
        dto.setMinute(10);

        assertThatThrownBy(() -> matchService.updateGoal(goalId, dto))
                .isInstanceOf(InvalidGoalException.class)
                .hasMessageContaining("does not play in this match");

        verify(goalRepository, never()).save(any());
    }

    @Test
    void updateGoal_notFound_throwsEntityNotFoundException() {
        UUID goalId = UUID.randomUUID();
        when(goalRepository.findById(goalId)).thenReturn(Optional.empty());

        GoalEventDto dto = new GoalEventDto();
        dto.setScorerId(homePlayerId);

        assertThatThrownBy(() -> matchService.updateGoal(goalId, dto))
                .isInstanceOf(EntityNotFoundException.class);

        verify(goalRepository, never()).save(any());
    }

    @Test
    void addGoal_withLeague_broadcastsLeagueId() {
        League league = new League();
        league.setId(UUID.randomUUID());
        homeTeam.setLeague(league);
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(playerRepository.findById(homePlayerId)).thenReturn(Optional.of(homePlayer));
        when(goalRepository.countGoalsBenefitingTeam(match, homeTeamId, null)).thenReturn(0L);

        GoalEventDto dto = new GoalEventDto();
        dto.setScorerId(homePlayerId);
        dto.setMinute(10);

        matchService.addGoal(matchId, dto);

        ArgumentCaptor<BroadcastRequest> captor = ArgumentCaptor.forClass(BroadcastRequest.class);
        verify(notificationClient).broadcast(captor.capture());
        assertThat(captor.getValue().getLeagueId()).isEqualTo(league.getId());
    }

    @Test
    void deleteGoal_goalNotFound_throwsEntityNotFoundException() {
        UUID goalId = UUID.randomUUID();
        when(goalRepository.findById(goalId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchService.deleteGoal(goalId))
                .isInstanceOf(EntityNotFoundException.class);

        verify(goalRepository, never()).delete(any());
    }

    @Test
    void deleteGoal_goalExists_deletesGoal() {
        UUID goalId = UUID.randomUUID();
        Goal goal = goalWithScorer(homePlayer);
        goal.setId(goalId);
        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));

        matchService.deleteGoal(goalId);

        verify(goalRepository).delete(goal);
    }

    @Test
    void create_homeAndAwayScoresAreSavedToEntity() {
        when(teamRepository.findById(homeTeamId)).thenReturn(Optional.of(homeTeam));
        when(teamRepository.findById(awayTeamId)).thenReturn(Optional.of(awayTeam));

        Match savedMatch = new Match();
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> {
            Match m = inv.getArgument(0);
            savedMatch.setHomeScore(m.getHomeScore());
            savedMatch.setAwayScore(m.getAwayScore());
            savedMatch.setHomeTeam(m.getHomeTeam());
            savedMatch.setAwayTeam(m.getAwayTeam());
            savedMatch.setPlayedAt(m.getPlayedAt());
            savedMatch.setId(UUID.randomUUID());
            return savedMatch;
        });

        MatchDto dto = new MatchDto();
        dto.setHomeTeamId(homeTeamId);
        dto.setAwayTeamId(awayTeamId);
        dto.setHomeScore(3);
        dto.setAwayScore(1);
        dto.setPlayedAt(LocalDateTime.now().minusDays(1));

        matchService.create(dto);

        assertThat(savedMatch.getHomeScore()).isEqualTo(3);
        assertThat(savedMatch.getAwayScore()).isEqualTo(1);
    }

    @Test
    void create_homeTeamNotFound_throwsEntityNotFoundException() {
        when(teamRepository.findById(homeTeamId)).thenReturn(Optional.empty());

        MatchDto dto = new MatchDto();
        dto.setHomeTeamId(homeTeamId);
        dto.setAwayTeamId(awayTeamId);
        dto.setPlayedAt(LocalDateTime.now().minusDays(1));

        assertThatThrownBy(() -> matchService.create(dto))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void addGoal_scorerNotFound_throwsEntityNotFoundException() {
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(playerRepository.findById(homePlayerId)).thenReturn(Optional.empty());

        GoalEventDto dto = new GoalEventDto();
        dto.setScorerId(homePlayerId);

        assertThatThrownBy(() -> matchService.addGoal(matchId, dto))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void addGoal_broadcastsGoalNotification() {
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(playerRepository.findById(homePlayerId)).thenReturn(Optional.of(homePlayer));
        when(goalRepository.countGoalsBenefitingTeam(match, homeTeamId, null)).thenReturn(0L);

        GoalEventDto dto = new GoalEventDto();
        dto.setScorerId(homePlayerId);
        dto.setMinute(9);

        matchService.addGoal(matchId, dto);

        ArgumentCaptor<BroadcastRequest> captor = ArgumentCaptor.forClass(BroadcastRequest.class);
        verify(notificationClient).broadcast(captor.capture());
        assertThat(captor.getValue().getMatchId()).isEqualTo(matchId);
        assertThat(captor.getValue().getType()).isEqualTo("GOAL");
        assertThat(captor.getValue().getMessage()).contains("Ivan Ivanov");
    }

    @Test
    void update_broadcastsNotificationWithLeague() {
        League league = new League();
        league.setId(UUID.randomUUID());
        league.setName("Premier");
        homeTeam.setLeague(league);
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(teamRepository.findById(homeTeamId)).thenReturn(Optional.of(homeTeam));
        when(teamRepository.findById(awayTeamId)).thenReturn(Optional.of(awayTeam));
        when(matchRepository.save(any(Match.class))).thenReturn(match);

        MatchDto dto = new MatchDto();
        dto.setHomeTeamId(homeTeamId);
        dto.setAwayTeamId(awayTeamId);
        dto.setHomeScore(2);
        dto.setAwayScore(1);
        dto.setPlayedAt(LocalDateTime.now().minusDays(1));

        matchService.update(matchId, dto);

        ArgumentCaptor<BroadcastRequest> captor = ArgumentCaptor.forClass(BroadcastRequest.class);
        verify(notificationClient).broadcast(captor.capture());
        assertThat(captor.getValue().getLeagueId()).isEqualTo(league.getId());
    }

    @Test
    void update_broadcastFails_logsWarningButStillReturns() {
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(teamRepository.findById(homeTeamId)).thenReturn(Optional.of(homeTeam));
        when(teamRepository.findById(awayTeamId)).thenReturn(Optional.of(awayTeam));
        when(matchRepository.save(any(Match.class))).thenReturn(match);
        doThrow(new RuntimeException("down")).when(notificationClient).broadcast(any());

        MatchDto dto = new MatchDto();
        dto.setHomeTeamId(homeTeamId);
        dto.setAwayTeamId(awayTeamId);
        dto.setHomeScore(2);
        dto.setAwayScore(1);
        dto.setPlayedAt(LocalDateTime.now().minusDays(1));

        MatchDto result = matchService.update(matchId, dto);

        assertThat(result).isNotNull();
    }

    @Test
    void addGoal_ownGoal_creditsAwayTeamAndBroadcastsOwnGoalMessage() {
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(playerRepository.findById(homePlayerId)).thenReturn(Optional.of(homePlayer));
        when(goalRepository.countGoalsBenefitingTeam(match, awayTeamId, null)).thenReturn(0L);

        GoalEventDto dto = new GoalEventDto();
        dto.setScorerId(homePlayerId);
        dto.setMinute(10);
        dto.setOwnGoal(true);

        matchService.addGoal(matchId, dto);

        ArgumentCaptor<Goal> captor = ArgumentCaptor.forClass(Goal.class);
        verify(goalRepository).save(captor.capture());
        assertThat(captor.getValue().getAssistant()).isNull();

        ArgumentCaptor<BroadcastRequest> bCaptor = ArgumentCaptor.forClass(BroadcastRequest.class);
        verify(notificationClient).broadcast(bCaptor.capture());
        assertThat(bCaptor.getValue().getMessage()).contains("own goal");
    }

    @Test
    void addGoal_ownGoalWithAssistant_throwsInvalidGoalException() {
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(playerRepository.findById(homePlayerId)).thenReturn(Optional.of(homePlayer));
        when(goalRepository.countGoalsBenefitingTeam(match, awayTeamId, null)).thenReturn(0L);

        GoalEventDto dto = new GoalEventDto();
        dto.setScorerId(homePlayerId);
        dto.setAssistantId(awayPlayerId);
        dto.setMinute(10);
        dto.setOwnGoal(true);

        assertThatThrownBy(() -> matchService.addGoal(matchId, dto))
                .isInstanceOf(InvalidGoalException.class)
                .hasMessageContaining("Own goals cannot have an assist");

        verify(goalRepository, never()).save(any());
    }

    @Test
    void addGoal_penalty_broadcastsPenaltyMessage() {
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(playerRepository.findById(homePlayerId)).thenReturn(Optional.of(homePlayer));
        when(goalRepository.countGoalsBenefitingTeam(match, homeTeamId, null)).thenReturn(0L);

        GoalEventDto dto = new GoalEventDto();
        dto.setScorerId(homePlayerId);
        dto.setMinute(10);
        dto.setPenalty(true);

        matchService.addGoal(matchId, dto);

        ArgumentCaptor<BroadcastRequest> captor = ArgumentCaptor.forClass(BroadcastRequest.class);
        verify(notificationClient).broadcast(captor.capture());
        assertThat(captor.getValue().getMessage()).contains("penalty");
    }

    @Test
    void addGoal_broadcastFails_logsWarningButGoalIsSaved() {
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(playerRepository.findById(homePlayerId)).thenReturn(Optional.of(homePlayer));
        when(goalRepository.countGoalsBenefitingTeam(match, homeTeamId, null)).thenReturn(0L);
        doThrow(new RuntimeException("down")).when(notificationClient).broadcast(any());

        GoalEventDto dto = new GoalEventDto();
        dto.setScorerId(homePlayerId);
        dto.setMinute(10);

        matchService.addGoal(matchId, dto);

        verify(goalRepository).save(any(Goal.class));
    }

    @Test
    void updateGoal_ownGoal_creditsOtherTeamAndSkipsAssistant() {
        UUID goalId = UUID.randomUUID();
        Goal goal = goalWithScorer(homePlayer);
        goal.setId(goalId);
        goal.setMatch(match);
        goal.setOwnGoal(true);

        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));
        when(playerRepository.findById(homePlayerId)).thenReturn(Optional.of(homePlayer));
        when(goalRepository.countGoalsBenefitingTeam(match, awayTeamId, goalId)).thenReturn(0L);

        GoalEventDto dto = new GoalEventDto();
        dto.setScorerId(homePlayerId);
        dto.setMinute(10);

        matchService.updateGoal(goalId, dto);

        ArgumentCaptor<Goal> captor = ArgumentCaptor.forClass(Goal.class);
        verify(goalRepository).save(captor.capture());
        assertThat(captor.getValue().getAssistant()).isNull();
    }

    @Test
    void updateGoal_ownGoalByAwayScorer_creditsHomeTeam() {
        UUID goalId = UUID.randomUUID();
        Goal goal = goalWithScorer(awayPlayer);
        goal.setId(goalId);
        goal.setMatch(match);
        goal.setOwnGoal(true);

        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));
        when(playerRepository.findById(awayPlayerId)).thenReturn(Optional.of(awayPlayer));
        when(goalRepository.countGoalsBenefitingTeam(match, homeTeamId, goalId)).thenReturn(0L);

        GoalEventDto dto = new GoalEventDto();
        dto.setScorerId(awayPlayerId);
        dto.setMinute(10);

        matchService.updateGoal(goalId, dto);

        ArgumentCaptor<Goal> captor = ArgumentCaptor.forClass(Goal.class);
        verify(goalRepository).save(captor.capture());
        assertThat(captor.getValue().getAssistant()).isNull();
    }

    @Test
    void findGoalById_returnsDtoWithScorerDetails() {
        UUID goalId = UUID.randomUUID();
        Goal goal = goalWithScorer(homePlayer);
        goal.setId(goalId);
        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));

        GoalDto result = matchService.findGoalById(goalId);

        assertThat(result.getScorerId()).isEqualTo(homePlayerId);
    }

    @Test
    void findGoalById_notFound_throwsEntityNotFoundException() {
        UUID goalId = UUID.randomUUID();
        when(goalRepository.findById(goalId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchService.findGoalById(goalId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void findGoalById_noScorer_leavesScorerFieldsNull() {
        UUID goalId = UUID.randomUUID();
        Goal goal = new Goal();
        goal.setId(goalId);
        goal.setHalf(Half.FIRST);
        goal.setMinute(5);
        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));

        GoalDto result = matchService.findGoalById(goalId);

        assertThat(result.getScorerId()).isNull();
    }

    @Test
    void findById_buildsGoalTimelineWithRunningScoresAndHalfSplit() {
        Goal g1 = goalWithScorer(homePlayer);
        g1.setHalf(Half.FIRST);
        g1.setMinute(10);

        Goal g2 = new Goal();
        g2.setId(UUID.randomUUID());
        g2.setScorer(awayPlayer);
        g2.setHalf(Half.SECOND);
        g2.setMinute(5);
        g2.setMatch(match);

        Goal g3 = new Goal();
        g3.setId(UUID.randomUUID());
        g3.setScorer(homePlayer);
        g3.setHalf(Half.SECOND);
        g3.setMinute(15);
        g3.setOwnGoal(true);
        g3.setMatch(match);

        match.getGoals().addAll(List.of(g1, g2, g3));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        MatchDto result = matchService.findById(matchId);

        assertThat(result.getGoalTimeline()).hasSize(3);
        assertThat(result.getHomeHalfScore()).isEqualTo(1);
        assertThat(result.getAwayHalfScore()).isEqualTo(0);
        assertThat(result.getGoalTimeline().get(1).isHomeGoal()).isFalse();
        assertThat(result.getGoalTimeline().get(2).isHomeGoal()).isFalse();
        assertThat(result.getGoalTimeline().get(2).isFirstInHalf()).isFalse();
    }

    @Test
    void findById_goalsInSameMinute_sortedByOffsetSecondsForTieBreak() {
        Goal earlier = goalWithScorer(homePlayer);
        earlier.setHalf(Half.SECOND);
        earlier.setMinute(10);
        earlier.setOffsetSeconds(5);

        Goal later = new Goal();
        later.setId(UUID.randomUUID());
        later.setScorer(awayPlayer);
        later.setHalf(Half.SECOND);
        later.setMinute(10);
        later.setOffsetSeconds(45);
        later.setMatch(match);

        match.getGoals().addAll(List.of(later, earlier));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        MatchDto result = matchService.findById(matchId);

        assertThat(result.getGoalTimeline()).hasSize(2);
        assertThat(result.getGoalTimeline().get(0).getScorerId()).isEqualTo(homePlayerId);
        assertThat(result.getGoalTimeline().get(1).getScorerId()).isEqualTo(awayPlayerId);
    }

    @Test
    void findById_goalsInSameMinuteNullOffset_treatsNullOffsetAsZero() {
        Goal noOffset = goalWithScorer(homePlayer);
        noOffset.setHalf(Half.SECOND);
        noOffset.setMinute(10);
        noOffset.setOffsetSeconds(null);

        Goal withOffset = new Goal();
        withOffset.setId(UUID.randomUUID());
        withOffset.setScorer(awayPlayer);
        withOffset.setHalf(Half.SECOND);
        withOffset.setMinute(10);
        withOffset.setOffsetSeconds(20);
        withOffset.setMatch(match);

        match.getGoals().addAll(List.of(withOffset, noOffset));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        MatchDto result = matchService.findById(matchId);

        assertThat(result.getGoalTimeline()).hasSize(2);
        assertThat(result.getGoalTimeline().get(0).getScorerId()).isEqualTo(homePlayerId);
        assertThat(result.getGoalTimeline().get(1).getScorerId()).isEqualTo(awayPlayerId);
    }

    @Test
    void findById_noGoals_doesNotSetHalfScores() {
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        MatchDto result = matchService.findById(matchId);

        assertThat(result.getGoalTimeline()).isEmpty();
        assertThat(result.getHomeHalfScore()).isNull();
    }

    private Goal goalWithScorer(Player scorer) {
        Goal g = new Goal();
        g.setId(UUID.randomUUID());
        g.setScorer(scorer);
        g.setHalf(Half.FIRST);
        g.setMinute(10);
        g.setMatch(match);
        return g;
    }
}
