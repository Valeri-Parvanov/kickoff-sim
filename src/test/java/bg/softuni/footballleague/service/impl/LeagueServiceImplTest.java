package bg.softuni.footballleague.service.impl;

import bg.softuni.footballleague.dto.GoalDto;
import bg.softuni.footballleague.dto.LeagueDetailView;
import bg.softuni.footballleague.dto.LeagueDto;
import bg.softuni.footballleague.dto.MatchDto;
import bg.softuni.footballleague.dto.StandingRow;
import bg.softuni.footballleague.exception.EntityNotFoundException;
import bg.softuni.footballleague.exception.InvalidLeagueOperationException;
import bg.softuni.footballleague.model.Half;
import bg.softuni.footballleague.model.League;
import bg.softuni.footballleague.model.LeagueFormat;
import bg.softuni.footballleague.model.Match;
import bg.softuni.footballleague.model.Team;
import bg.softuni.footballleague.repository.LeagueRepository;
import bg.softuni.footballleague.repository.MatchRepository;
import bg.softuni.footballleague.repository.PlayerRepository;
import bg.softuni.footballleague.repository.TeamRepository;
import bg.softuni.footballleague.service.MatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeagueServiceImplTest {

    @Mock private LeagueRepository leagueRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private MatchService matchService;

    @InjectMocks
    private LeagueServiceImpl leagueService;

    private StandingRow standing(String name, int wins, int draws, int played) {
        StandingRow row = new StandingRow();
        row.setTeamId(UUID.randomUUID());
        row.setTeamName(name);
        row.setWins(wins);
        row.setDraws(draws);
        row.setPlayed(played);
        return row;
    }

    @Test
    void markChampion_fourPointsClearWithOneRoundLeft_isChampion() {
        StandingRow leader = standing("Neftochimic", 12, 2, 17);
        StandingRow rival = standing("Orlovi", 10, 4, 17);

        leagueService.markChampion(List.of(leader, rival), LeagueFormat.TEN);

        assertThat(leader.getPoints()).isEqualTo(38);
        assertThat(rival.getPoints()).isEqualTo(34);
        assertThat(leader.isChampion()).isTrue();
    }

    @Test
    void markChampion_threePointsClearWithOneRoundLeft_isNotChampion() {
        StandingRow leader = standing("Neftochimic", 12, 1, 17);
        StandingRow rival = standing("Orlovi", 10, 4, 17);

        leagueService.markChampion(List.of(leader, rival), LeagueFormat.TEN);

        assertThat(leader.isChampion()).isFalse();
    }

    @Test
    void markChampion_lowerRivalWithGamesInHandCanStillCatchUp_isNotChampion() {
        StandingRow leader = standing("Neftochimic", 12, 2, 17);
        StandingRow second = standing("Orlovi", 10, 4, 17);
        StandingRow third = standing("Yantra", 10, 0, 14);

        leagueService.markChampion(List.of(leader, second, third), LeagueFormat.TEN);

        assertThat(leader.isChampion()).isFalse();
    }

    @Test
    void markChampion_allMatchesPlayed_marksLeader() {
        StandingRow leader = standing("Neftochimic", 12, 2, 18);
        StandingRow rival = standing("Orlovi", 11, 4, 18);

        leagueService.markChampion(List.of(leader, rival), LeagueFormat.TEN);

        assertThat(leader.isChampion()).isTrue();
    }

    @Test
    void markChampion_nothingPlayedYet_marksNobody() {
        StandingRow leader = standing("Neftochimic", 0, 0, 0);
        StandingRow rival = standing("Orlovi", 0, 0, 0);

        leagueService.markChampion(List.of(leader, rival), LeagueFormat.TEN);

        assertThat(leader.isChampion()).isFalse();
    }

    @Test
    void markChampion_singleTeamStandings_doesNothing() {
        StandingRow leader = standing("Neftochimic", 5, 0, 5);

        leagueService.markChampion(List.of(leader), LeagueFormat.TEN);

        assertThat(leader.isChampion()).isFalse();
    }

    @Test
    void create_savesAndReturnsDto() {
        List<UUID> teamIds = new ArrayList<>();
        List<Team> teams = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            UUID id = UUID.randomUUID();
            Team team = new Team();
            team.setId(id);
            team.setLeague(null);
            teamIds.add(id);
            teams.add(team);
        }

        when(leagueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(teamRepository.findAllById(any())).thenReturn(teams);
        when(playerRepository.countByTeam(any(Team.class))).thenReturn(6L);
        when(teamRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LeagueDto dto = new LeagueDto();
        dto.setName("First League");
        dto.setTeamIds(teamIds);

        LeagueDto saved = leagueService.create(dto);

        assertThat(saved.getName()).isEqualTo("First League");
        verify(leagueRepository).save(any(League.class));
    }

    @Test
    void findById_notFound_throwsEntityNotFoundException() {
        UUID id = UUID.randomUUID();
        when(leagueRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leagueService.findById(id))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void delete_removesMatchesForEachTeamThenLeague() {
        UUID leagueId = UUID.randomUUID();

        Team teamA = new Team();
        teamA.setId(UUID.randomUUID());
        Team teamB = new Team();
        teamB.setId(UUID.randomUUID());

        League league = new League();
        league.setId(leagueId);
        league.getTeams().addAll(List.of(teamA, teamB));

        when(leagueRepository.findById(leagueId)).thenReturn(Optional.of(league));
        when(matchRepository.findAllByHomeTeamOrAwayTeam(teamA, teamA)).thenReturn(List.of(new Match()));
        when(matchRepository.findAllByHomeTeamOrAwayTeam(teamB, teamB)).thenReturn(List.of(new Match()));
        when(teamRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        leagueService.delete(leagueId);

        verify(matchRepository).findAllByHomeTeamOrAwayTeam(teamA, teamA);
        verify(matchRepository).findAllByHomeTeamOrAwayTeam(teamB, teamB);
        verify(leagueRepository).delete(league);
    }

    @Test
    void delete_notFound_throwsEntityNotFoundException() {
        UUID id = UUID.randomUUID();
        when(leagueRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leagueService.delete(id))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void update_notFound_throwsEntityNotFoundException() {
        UUID id = UUID.randomUUID();
        when(leagueRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leagueService.update(id, new LeagueDto()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void update_savesUpdatedNameAndReturnsDto() {
        UUID id = UUID.randomUUID();
        League league = new League();
        league.setId(id);
        league.setName("Old Name");
        when(leagueRepository.findById(id)).thenReturn(Optional.of(league));
        when(leagueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(matchRepository.countByLeagueId(id)).thenReturn(0L);
        when(matchRepository.countPlayedByLeagueId(eq(id), any(LocalDateTime.class))).thenReturn(0L);

        LeagueDto dto = new LeagueDto();
        dto.setName("New Name");
        LeagueDto result = leagueService.update(id, dto);

        assertThat(result.getName()).isEqualTo("New Name");
        verify(leagueRepository).save(league);
    }

    @Test
    void create_zeroTeams_throwsException() {
        LeagueDto dto = new LeagueDto();
        dto.setName("Empty");
        dto.setTeamIds(null);

        assertThatThrownBy(() -> leagueService.create(dto))
                .isInstanceOf(InvalidLeagueOperationException.class);
    }

    @Test
    void create_invalidTeamCount_throwsException() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 7; i++) ids.add(UUID.randomUUID());
        LeagueDto dto = new LeagueDto();
        dto.setName("Bad Count");
        dto.setTeamIds(ids);

        assertThatThrownBy(() -> leagueService.create(dto))
                .isInstanceOf(InvalidLeagueOperationException.class);
    }

    @Test
    void create_teamAlreadyInLeague_throwsException() {
        List<UUID> teamIds = new ArrayList<>();
        List<Team> teams = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            UUID id = UUID.randomUUID();
            Team team = new Team();
            team.setId(id);
            team.setName("Team" + i);
            teamIds.add(id);
            teams.add(team);
        }
        teams.get(0).setLeague(new League());

        when(leagueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(teamRepository.findAllById(any())).thenReturn(teams);

        LeagueDto dto = new LeagueDto();
        dto.setName("Cup");
        dto.setTeamIds(teamIds);

        assertThatThrownBy(() -> leagueService.create(dto))
                .isInstanceOf(InvalidLeagueOperationException.class);
    }

    @Test
    void create_teamTooFewPlayers_throwsException() {
        List<UUID> teamIds = new ArrayList<>();
        List<Team> teams = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            UUID id = UUID.randomUUID();
            Team team = new Team();
            team.setId(id);
            team.setName("Team" + i);
            teamIds.add(id);
            teams.add(team);
        }

        when(leagueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(teamRepository.findAllById(any())).thenReturn(teams);
        when(playerRepository.countByTeam(any(Team.class))).thenReturn(3L);

        LeagueDto dto = new LeagueDto();
        dto.setName("Cup");
        dto.setTeamIds(teamIds);

        assertThatThrownBy(() -> leagueService.create(dto))
                .isInstanceOf(InvalidLeagueOperationException.class);
    }

    @Test
    void hasLeagueStarted_delegatesToRepository() {
        UUID leagueId = UUID.randomUUID();
        when(matchRepository.hasPlayedMatchesForLeague(eq(leagueId), any(LocalDateTime.class))).thenReturn(true);

        assertThat(leagueService.hasLeagueStarted(leagueId)).isTrue();
    }

    private Team teamEntity(String name) {
        Team t = new Team();
        t.setId(UUID.randomUUID());
        t.setName(name);
        t.setCity("City");
        return t;
    }

    private MatchDto matchDto(UUID home, UUID away, LocalDateTime playedAt, Integer homeScore, Integer awayScore) {
        MatchDto m = new MatchDto();
        m.setId(UUID.randomUUID());
        m.setHomeTeamId(home);
        m.setAwayTeamId(away);
        m.setPlayedAt(playedAt);
        m.setHomeScore(homeScore);
        m.setAwayScore(awayScore);
        return m;
    }

    @Test
    void findDetail_computesWinLossDrawAndExcludesOutsideMatches() {
        UUID leagueId = UUID.randomUUID();
        Team teamA = teamEntity("Alpha");
        Team teamB = teamEntity("Bravo");
        Team teamC = teamEntity("Charlie");
        UUID outsideId = UUID.randomUUID();

        League league = new League();
        league.setId(leagueId);
        league.setName("Premier");
        league.getTeams().addAll(List.of(teamA, teamB, teamC));
        when(leagueRepository.findById(leagueId)).thenReturn(Optional.of(league));

        LocalDateTime old = LocalDateTime.now().minusHours(2);
        MatchDto win = matchDto(teamA.getId(), teamB.getId(), old, 2, 1);
        win.setRoundNumber(1);
        MatchDto draw = matchDto(teamA.getId(), teamC.getId(), old, 1, 1);
        MatchDto outsideAway = matchDto(teamA.getId(), outsideId, old, 3, 0);
        MatchDto outsideHome = matchDto(outsideId, teamA.getId(), old, 0, 3);
        MatchDto nullPlayedAt = matchDto(teamB.getId(), teamC.getId(), null, 1, 0);
        MatchDto future = matchDto(teamB.getId(), teamC.getId(), LocalDateTime.now().plusDays(1), 1, 0);
        MatchDto nullHomeScore = matchDto(teamB.getId(), teamC.getId(), old, null, 1);
        MatchDto nullAwayScore = matchDto(teamB.getId(), teamC.getId(), old, 1, null);
        MatchDto awayWin = matchDto(teamB.getId(), teamC.getId(), old.minusMinutes(1), 0, 2);

        when(matchService.findByLeague(leagueId)).thenReturn(List.of(
                win, draw, outsideAway, outsideHome, nullPlayedAt, future, nullHomeScore, nullAwayScore, awayWin));

        LeagueDetailView view = leagueService.findDetail(leagueId);

        StandingRow rowA = view.getStandings().stream().filter(r -> r.getTeamId().equals(teamA.getId())).findFirst().orElseThrow();
        StandingRow rowB = view.getStandings().stream().filter(r -> r.getTeamId().equals(teamB.getId())).findFirst().orElseThrow();
        StandingRow rowC = view.getStandings().stream().filter(r -> r.getTeamId().equals(teamC.getId())).findFirst().orElseThrow();

        assertThat(rowA.getPlayed()).isEqualTo(2);
        assertThat(rowA.getWins()).isEqualTo(1);
        assertThat(rowA.getDraws()).isEqualTo(1);
        assertThat(rowB.getLosses()).isEqualTo(2);
        assertThat(rowC.getDraws()).isEqualTo(1);
        assertThat(rowC.getWins()).isEqualTo(1);
        assertThat(rowB.getPlayed()).isEqualTo(2);
        assertThat(rowC.getPlayed()).isEqualTo(2);
    }

    @Test
    void findDetail_liveMatchGoalTimeline_computesScoreFromGoals() {
        UUID leagueId = UUID.randomUUID();
        Team teamA = teamEntity("Alpha");
        Team teamB = teamEntity("Bravo");

        League league = new League();
        league.setId(leagueId);
        league.setName("Premier");
        league.getTeams().addAll(List.of(teamA, teamB));
        when(leagueRepository.findById(leagueId)).thenReturn(Optional.of(league));

        MatchDto liveEarly = matchDto(teamA.getId(), teamB.getId(), LocalDateTime.now().minusMinutes(10), 0, 0);
        liveEarly.getGoalTimeline().add(goal(5, Half.FIRST, true));
        liveEarly.getGoalTimeline().add(goal(15, Half.FIRST, false));
        liveEarly.getGoalTimeline().add(goal(1, Half.SECOND, false));

        MatchDto liveLate = matchDto(teamA.getId(), teamB.getId(), LocalDateTime.now().minusMinutes(30), 0, 0);
        liveLate.getGoalTimeline().add(goalMissingMinute(Half.FIRST));
        liveLate.getGoalTimeline().add(goalMissingHalf(5));
        liveLate.getGoalTimeline().add(goal(25, Half.FIRST, true));
        liveLate.getGoalTimeline().add(goal(3, Half.SECOND, false));
        liveLate.getGoalTimeline().add(goal(50, Half.SECOND, false));

        when(matchService.findByLeague(leagueId)).thenReturn(List.of(liveEarly, liveLate));

        LeagueDetailView view = leagueService.findDetail(leagueId);

        StandingRow rowA = view.getStandings().stream().filter(r -> r.getTeamId().equals(teamA.getId())).findFirst().orElseThrow();
        assertThat(rowA.getPlayed()).isEqualTo(2);
        assertThat(rowA.getGoalsFor()).isEqualTo(2);
        assertThat(rowA.getGoalsAgainst()).isEqualTo(1);
    }

    @Test
    void findDetail_tiedTeams_sortsByHeadToHead() {
        UUID leagueId = UUID.randomUUID();
        Team teamA = teamEntity("Alpha");
        Team teamB = teamEntity("Bravo");
        Team teamC = teamEntity("Charlie");
        Team teamD = teamEntity("Delta");
        Team teamE = teamEntity("Echo");
        Team teamF = teamEntity("Foxtrot");
        Team teamG = teamEntity("Golf");
        Team teamH = teamEntity("Hotel");

        League league = new League();
        league.setId(leagueId);
        league.setName("Premier");
        league.getTeams().addAll(List.of(teamA, teamB, teamC, teamD, teamE, teamF, teamG, teamH));
        when(leagueRepository.findById(leagueId)).thenReturn(Optional.of(league));

        LocalDateTime old = LocalDateTime.now().minusHours(2);
        MatchDto aBeatsC = matchDto(teamA.getId(), teamC.getId(), old, 2, 0);
        MatchDto bBeatsD = matchDto(teamB.getId(), teamD.getId(), old, 2, 0);
        MatchDto aDrawsB = matchDto(teamA.getId(), teamB.getId(), old, 0, 0);
        MatchDto aVsBHomeScoreOnly = matchDto(teamA.getId(), teamB.getId(), old, 1, null);
        MatchDto aVsBBothNull = matchDto(teamA.getId(), teamB.getId(), old, null, null);
        // Future-dated: excluded from the standings loop (line 162) but still visible to
        // sortByH2H (no date filter there), so these exercise the home-win/away-win h2h
        // branches without disturbing the points/goalDiff/goalsFor tie between A and B.
        LocalDateTime future = LocalDateTime.now().plusDays(1);
        MatchDto futureHomeWin = matchDto(teamA.getId(), teamB.getId(), future, 2, 0);
        MatchDto futureAwayWin = matchDto(teamB.getId(), teamA.getId(), future, 0, 2);
        // Same points (3) as each other, but different goalDiff — exercises samePrimaryKey's
        // goalDiff== check evaluating false after points== was true.
        MatchDto eBeatsC = matchDto(teamE.getId(), teamC.getId(), old, 2, 0);
        MatchDto fBeatsD = matchDto(teamF.getId(), teamD.getId(), old, 1, 0);
        // Same points (3) and goalDiff (+2) as each other, but different goalsFor — exercises
        // samePrimaryKey's goalsFor== check evaluating false after points==/goalDiff== were true.
        MatchDto gBeatsC = matchDto(teamG.getId(), teamC.getId(), old, 3, 1);
        MatchDto hBeatsD = matchDto(teamH.getId(), teamD.getId(), old, 2, 0);

        when(matchService.findByLeague(leagueId)).thenReturn(
                List.of(aBeatsC, bBeatsD, aDrawsB, aVsBHomeScoreOnly, aVsBBothNull, futureHomeWin, futureAwayWin,
                        eBeatsC, fBeatsD, gBeatsC, hBeatsD));

        LeagueDetailView view = leagueService.findDetail(leagueId);

        assertThat(view.getStandings()).isNotEmpty();
        StandingRow rowA = view.getStandings().stream().filter(r -> r.getTeamId().equals(teamA.getId())).findFirst().orElseThrow();
        StandingRow rowB = view.getStandings().stream().filter(r -> r.getTeamId().equals(teamB.getId())).findFirst().orElseThrow();
        assertThat(rowA.getPoints()).isEqualTo(4);
        assertThat(rowB.getPoints()).isEqualTo(4);
    }

    private GoalDto goal(int minute, Half half, boolean homeGoal) {
        GoalDto g = new GoalDto();
        g.setMinute(minute);
        g.setHalf(half);
        g.setHomeGoal(homeGoal);
        return g;
    }

    private GoalDto goalMissingMinute(Half half) {
        GoalDto g = new GoalDto();
        g.setHalf(half);
        return g;
    }

    private GoalDto goalMissingHalf(int minute) {
        GoalDto g = new GoalDto();
        g.setMinute(minute);
        return g;
    }
}
