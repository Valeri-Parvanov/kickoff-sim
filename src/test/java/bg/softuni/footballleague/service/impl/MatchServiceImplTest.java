package bg.softuni.footballleague.service.impl;

import bg.softuni.footballleague.dto.GoalEventDto;
import bg.softuni.footballleague.dto.MatchDto;
import bg.softuni.footballleague.exception.EntityNotFoundException;
import bg.softuni.footballleague.exception.InvalidGoalException;
import bg.softuni.footballleague.model.Goal;
import bg.softuni.footballleague.model.Half;
import bg.softuni.footballleague.model.Match;
import bg.softuni.footballleague.model.Player;
import bg.softuni.footballleague.model.Team;
import bg.softuni.footballleague.repository.GoalRepository;
import bg.softuni.footballleague.repository.MatchRepository;
import bg.softuni.footballleague.repository.PlayerRepository;
import bg.softuni.footballleague.repository.TeamRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchServiceImplTest {

    @Mock private MatchRepository matchRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private GoalRepository goalRepository;
    @Mock private PlayerRepository playerRepository;

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
