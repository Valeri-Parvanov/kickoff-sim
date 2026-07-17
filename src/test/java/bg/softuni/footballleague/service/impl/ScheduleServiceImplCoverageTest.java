package bg.softuni.footballleague.service.impl;

import bg.softuni.footballleague.client.NotificationClient;
import bg.softuni.footballleague.exception.EntityNotFoundException;
import bg.softuni.footballleague.exception.InvalidLeagueOperationException;
import bg.softuni.footballleague.model.Goal;
import bg.softuni.footballleague.model.Half;
import bg.softuni.footballleague.model.League;
import bg.softuni.footballleague.model.Match;
import bg.softuni.footballleague.model.Player;
import bg.softuni.footballleague.model.Team;
import bg.softuni.footballleague.repository.GoalRepository;
import bg.softuni.footballleague.repository.LeagueRepository;
import bg.softuni.footballleague.repository.MatchRepository;
import bg.softuni.footballleague.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScheduleServiceImplCoverageTest {

    @Mock private LeagueRepository leagueRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private GoalRepository goalRepository;
    @Mock private NotificationClient notificationClient;

    @InjectMocks private ScheduleServiceImpl service;

    private Team team(String name) {
        Team t = new Team();
        t.setId(UUID.randomUUID());
        t.setName(name);
        t.setCity("City");
        return t;
    }

    private League leagueWithTeams(int count) {
        League l = new League();
        l.setId(UUID.randomUUID());
        l.setName("Test League");
        for (int i = 0; i < count; i++) l.getTeams().add(team("T" + i));
        return l;
    }

    private Player player(Team t) {
        Player p = new Player();
        p.setId(UUID.randomUUID());
        p.setFirstName("Ivan");
        p.setLastName("Petrov");
        p.setTeam(t);
        return p;
    }

    @Test
    void generate_leagueNotFound_throws() {
        UUID id = UUID.randomUUID();
        when(leagueRepository.findByIdWithTeams(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(id, LocalDate.now(), LocalTime.of(11, 0)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void generate_invalidTeamCount_throws() {
        League league = leagueWithTeams(3);
        when(leagueRepository.findByIdWithTeams(league.getId())).thenReturn(Optional.of(league));

        assertThatThrownBy(() -> service.generate(league.getId(), LocalDate.now(), LocalTime.of(11, 0)))
                .isInstanceOf(InvalidLeagueOperationException.class);
    }

    @Test
    void generate_startTimeNotQuarterMark_throws() {
        League league = leagueWithTeams(6);
        when(leagueRepository.findByIdWithTeams(league.getId())).thenReturn(Optional.of(league));

        assertThatThrownBy(() -> service.generate(league.getId(), LocalDate.now(), LocalTime.of(11, 7)))
                .isInstanceOf(InvalidLeagueOperationException.class);
    }

    @Test
    void generate_startTimeBeforeEight_throws() {
        League league = leagueWithTeams(6);
        when(leagueRepository.findByIdWithTeams(league.getId())).thenReturn(Optional.of(league));

        assertThatThrownBy(() -> service.generate(league.getId(), LocalDate.now(), LocalTime.of(7, 0)))
                .isInstanceOf(InvalidLeagueOperationException.class);
    }

    @Test
    void generate_startTimeTooLate_throws() {
        League league = leagueWithTeams(6);
        when(leagueRepository.findByIdWithTeams(league.getId())).thenReturn(Optional.of(league));

        assertThatThrownBy(() -> service.generate(league.getId(), LocalDate.now(), LocalTime.of(22, 0)))
                .isInstanceOf(InvalidLeagueOperationException.class);
    }

    @Test
    void generate_scheduleExists_throws() {
        League league = leagueWithTeams(6);
        when(leagueRepository.findByIdWithTeams(league.getId())).thenReturn(Optional.of(league));
        when(matchRepository.existsByLeagueId(league.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.generate(league.getId(), LocalDate.now(), LocalTime.of(11, 0)))
                .isInstanceOf(InvalidLeagueOperationException.class);
    }

    @Test
    void tryAutoGenerate_missingLeague_returns() {
        UUID id = UUID.randomUUID();
        when(leagueRepository.findById(id)).thenReturn(Optional.empty());

        service.tryAutoGenerate(id);

        verify(matchRepository, never()).saveAll(anyList());
    }

    @Test
    void tryAutoGenerate_noStartDate_returns() {
        League league = leagueWithTeams(6);
        when(leagueRepository.findById(league.getId())).thenReturn(Optional.of(league));

        service.tryAutoGenerate(league.getId());

        verify(matchRepository, never()).saveAll(anyList());
    }

    @Test
    void tryAutoGenerate_invalidFormat_returns() {
        League league = leagueWithTeams(3);
        league.setScheduleStartDate(LocalDate.now());
        when(leagueRepository.findById(league.getId())).thenReturn(Optional.of(league));

        service.tryAutoGenerate(league.getId());

        verify(matchRepository, never()).saveAll(anyList());
    }

    @Test
    void tryAutoGenerate_scheduleExists_returns() {
        League league = leagueWithTeams(6);
        league.setScheduleStartDate(LocalDate.now());
        when(leagueRepository.findById(league.getId())).thenReturn(Optional.of(league));
        when(matchRepository.existsByLeagueId(league.getId())).thenReturn(true);

        service.tryAutoGenerate(league.getId());

        verify(matchRepository, never()).saveAll(anyList());
    }

    @Test
    void tryAutoGenerate_invalidStartTime_returns() {
        League league = leagueWithTeams(6);
        league.setScheduleStartDate(LocalDate.now());
        league.setScheduleStartTime(LocalTime.of(7, 0));
        when(leagueRepository.findById(league.getId())).thenReturn(Optional.of(league));

        service.tryAutoGenerate(league.getId());

        verify(matchRepository, never()).saveAll(anyList());
    }

    @Test
    void tryAutoGenerate_valid_buildsSchedule() {
        League league = leagueWithTeams(6);
        league.setScheduleStartDate(LocalDate.now());
        when(leagueRepository.findById(league.getId())).thenReturn(Optional.of(league));
        when(matchRepository.existsByLeagueId(league.getId())).thenReturn(false);
        when(playerRepository.findAllByTeam(any(Team.class)))
                .thenAnswer(a -> List.of(player(a.getArgument(0)), player(a.getArgument(0))));

        service.tryAutoGenerate(league.getId());

        verify(matchRepository).saveAll(anyList());
    }

    @Test
    void notifyGoals_inWindow_broadcastsAndMarksNotified() {
        Team home = team("Home");
        Team away = team("Away");
        Match match = new Match();
        match.setId(UUID.randomUUID());
        match.setHomeTeam(home);
        match.setAwayTeam(away);
        match.setPlayedAt(LocalDateTime.now().minusMinutes(10));

        Goal inWindow = new Goal();
        inWindow.setId(UUID.randomUUID());
        inWindow.setMatch(match);
        inWindow.setScorer(player(home));
        inWindow.setHalf(Half.FIRST);
        inWindow.setMinute(8);

        Goal outOfWindow = new Goal();
        outOfWindow.setId(UUID.randomUUID());
        outOfWindow.setMatch(match);
        outOfWindow.setScorer(player(away));
        outOfWindow.setHalf(Half.FIRST);
        outOfWindow.setMinute(1);

        match.getGoals().add(inWindow);
        match.getGoals().add(outOfWindow);
        when(goalRepository.findUnnotifiedForMatchesStartedBetween(any(), any()))
                .thenReturn(List.of(inWindow, outOfWindow));

        service.notifyGoals();

        verify(notificationClient).broadcast(any());
        verify(goalRepository).save(inWindow);
        verify(goalRepository, never()).save(outOfWindow);
    }

    @Test
    void notifyGoals_secondHalfOwnGoalAndPenalty_futureGoalSkipped() {
        Team home = team("Home");
        Team away = team("Away");
        Match match = new Match();
        match.setId(UUID.randomUUID());
        match.setHomeTeam(home);
        match.setAwayTeam(away);
        match.setPlayedAt(LocalDateTime.now().minusMinutes(30));

        Goal ownGoal = new Goal();
        ownGoal.setId(UUID.randomUUID());
        ownGoal.setMatch(match);
        ownGoal.setScorer(player(away));
        ownGoal.setOwnGoal(true);
        ownGoal.setHalf(Half.SECOND);
        ownGoal.setMinute(3);

        Goal penalty = new Goal();
        penalty.setId(UUID.randomUUID());
        penalty.setMatch(match);
        penalty.setScorer(player(home));
        penalty.setPenalty(true);
        penalty.setHalf(Half.FIRST);
        penalty.setMinute(27);

        Match freshMatch = new Match();
        freshMatch.setId(UUID.randomUUID());
        freshMatch.setHomeTeam(home);
        freshMatch.setAwayTeam(away);
        freshMatch.setPlayedAt(LocalDateTime.now().minusMinutes(3));
        Goal future = new Goal();
        future.setId(UUID.randomUUID());
        future.setMatch(freshMatch);
        future.setScorer(player(home));
        future.setHalf(Half.SECOND);
        future.setMinute(10);

        match.getGoals().add(ownGoal);
        match.getGoals().add(penalty);
        freshMatch.getGoals().add(future);
        when(goalRepository.findUnnotifiedForMatchesStartedBetween(any(), any()))
                .thenReturn(List.of(ownGoal, penalty, future));

        service.notifyGoals();

        verify(goalRepository).save(ownGoal);
        verify(goalRepository).save(penalty);
        verify(goalRepository, never()).save(future);
    }

    @Test
    void notifyMatchEvents_sendsKickoffHalftimeFulltime() {
        Team home = team("Home");
        Team away = team("Away");

        Match kickoff = new Match();
        kickoff.setId(UUID.randomUUID());
        kickoff.setHomeTeam(home);
        kickoff.setAwayTeam(away);
        kickoff.setPlayedAt(LocalDateTime.now().minusMinutes(1));

        Match halftime = new Match();
        halftime.setId(UUID.randomUUID());
        halftime.setHomeTeam(home);
        halftime.setAwayTeam(away);
        halftime.setPlayedAt(LocalDateTime.now().minusMinutes(23));
        Goal homeGoal = new Goal();
        homeGoal.setMatch(halftime);
        homeGoal.setScorer(player(home));
        homeGoal.setHalf(Half.FIRST);
        homeGoal.setMinute(5);
        Goal awayGoal = new Goal();
        awayGoal.setMatch(halftime);
        awayGoal.setScorer(player(away));
        awayGoal.setHalf(Half.FIRST);
        awayGoal.setMinute(10);
        Goal secondHalf = new Goal();
        secondHalf.setMatch(halftime);
        secondHalf.setScorer(player(home));
        secondHalf.setHalf(Half.SECOND);
        secondHalf.setMinute(3);
        halftime.getGoals().add(homeGoal);
        halftime.getGoals().add(awayGoal);
        halftime.getGoals().add(secondHalf);

        Match fulltime = new Match();
        fulltime.setId(UUID.randomUUID());
        fulltime.setHomeTeam(home);
        fulltime.setAwayTeam(away);
        fulltime.setPlayedAt(LocalDateTime.now().minusMinutes(48));
        fulltime.setHomeScore(2);
        fulltime.setAwayScore(1);

        when(matchRepository.findForKickoffNotification(any(), any())).thenReturn(List.of(kickoff));
        when(matchRepository.findForHalftimeNotification(any(), any())).thenReturn(List.of(halftime));
        when(matchRepository.findForFulltimeNotification(any(), any())).thenReturn(List.of(fulltime));

        service.notifyMatchEvents();

        verify(matchRepository).save(kickoff);
        verify(matchRepository).save(halftime);
        verify(matchRepository).save(fulltime);
    }
}
