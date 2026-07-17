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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeagueServiceImplCoverageTest {

    @Mock private LeagueRepository leagueRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private MatchService matchService;

    @InjectMocks private LeagueServiceImpl service;

    private Team team(String name) {
        Team t = new Team();
        t.setId(UUID.randomUUID());
        t.setName(name);
        t.setCity("City");
        return t;
    }

    private League leagueWith(Team... teams) {
        League l = new League();
        l.setId(UUID.randomUUID());
        l.setName("First League");
        for (Team t : teams) l.getTeams().add(t);
        return l;
    }

    @Test
    void findAll_mapsToDtos() {
        when(leagueRepository.findAll(any(Sort.class))).thenReturn(List.of(leagueWith()));

        assertThat(service.findAll(Sort.by("name"))).hasSize(1);
    }

    @Test
    void findById_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(leagueRepository.findById(id)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.findById(id)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void create_noTeams_throws() {
        LeagueDto dto = new LeagueDto();
        dto.setName("Cup");
        dto.setTeamIds(new ArrayList<>());

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(InvalidLeagueOperationException.class);
    }

    @Test
    void create_invalidTeamCount_throws() {
        LeagueDto dto = new LeagueDto();
        dto.setName("Cup");
        dto.setTeamIds(List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(InvalidLeagueOperationException.class);
    }

    @Test
    void create_teamAlreadyInLeague_throws() {
        List<UUID> ids = sixIds();
        LeagueDto dto = new LeagueDto();
        dto.setName("Cup");
        dto.setTeamIds(ids);
        when(leagueRepository.save(any())).thenAnswer(a -> a.getArgument(0));
        Team assigned = team("Taken");
        assigned.setLeague(new League());
        when(teamRepository.findAllById(anyCollection())).thenReturn(List.of(assigned));

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(InvalidLeagueOperationException.class);
    }

    @Test
    void create_teamTooFewPlayers_throws() {
        List<UUID> ids = sixIds();
        LeagueDto dto = new LeagueDto();
        dto.setName("Cup");
        dto.setTeamIds(ids);
        when(leagueRepository.save(any())).thenAnswer(a -> a.getArgument(0));
        Team t = team("Low");
        when(teamRepository.findAllById(anyCollection())).thenReturn(List.of(t));
        when(playerRepository.countByTeam(t)).thenReturn(3L);

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(InvalidLeagueOperationException.class);
    }

    @Test
    void create_valid_savesAndReturns() {
        List<UUID> ids = sixIds();
        LeagueDto dto = new LeagueDto();
        dto.setName("Cup");
        dto.setTeamIds(ids);
        when(leagueRepository.save(any())).thenAnswer(a -> a.getArgument(0));
        List<Team> teams = new ArrayList<>();
        for (int i = 0; i < 6; i++) teams.add(team("T" + i));
        when(teamRepository.findAllById(anyCollection())).thenReturn(teams);
        when(playerRepository.countByTeam(any())).thenReturn(6L);

        LeagueDto result = service.create(dto);

        assertThat(result.getName()).isEqualTo("Cup");
        verify(leagueRepository).save(any());
    }

    @Test
    void update_updatesEntity() {
        UUID id = UUID.randomUUID();
        League league = leagueWith();
        when(leagueRepository.findById(id)).thenReturn(java.util.Optional.of(league));
        when(leagueRepository.save(any())).thenAnswer(a -> a.getArgument(0));
        LeagueDto dto = new LeagueDto();
        dto.setName("Renamed");

        assertThat(service.update(id, dto).getName()).isEqualTo("Renamed");
    }

    @Test
    void delete_detachesTeamsAndDeletes() {
        UUID id = UUID.randomUUID();
        Team t = team("A");
        League league = leagueWith(t);
        when(leagueRepository.findById(id)).thenReturn(java.util.Optional.of(league));
        when(matchRepository.findAllByHomeTeamOrAwayTeam(t, t)).thenReturn(List.<Match>of());

        service.delete(id);

        verify(teamRepository).save(t);
        verify(leagueRepository).delete(league);
        assertThat(t.getLeague()).isNull();
    }

    @Test
    void hasLeagueStarted_delegates() {
        UUID id = UUID.randomUUID();
        when(matchRepository.hasPlayedMatchesForLeague(any(), any())).thenReturn(true);

        assertThat(service.hasLeagueStarted(id)).isTrue();
    }

    @Test
    void findDetail_computesStandingsFromFinishedMatch() {
        Team home = team("Alpha");
        Team away = team("Beta");
        League league = leagueWith(home, away);
        when(leagueRepository.findById(league.getId())).thenReturn(java.util.Optional.of(league));

        MatchDto finished = new MatchDto();
        finished.setId(UUID.randomUUID());
        finished.setHomeTeamId(home.getId());
        finished.setAwayTeamId(away.getId());
        finished.setHomeScore(2);
        finished.setAwayScore(1);
        finished.setRoundNumber(1);
        finished.setPlayedAt(LocalDateTime.now().minusHours(3));

        MatchDto future = new MatchDto();
        future.setId(UUID.randomUUID());
        future.setHomeTeamId(home.getId());
        future.setAwayTeamId(away.getId());
        future.setRoundNumber(2);
        future.setPlayedAt(LocalDateTime.now().plusDays(1));

        when(matchService.findByLeague(league.getId())).thenReturn(List.of(finished, future));

        LeagueDetailView view = service.findDetail(league.getId());

        assertThat(view.getStandings()).hasSize(2);
        assertThat(view.getStandings().get(0).getTeamName()).isEqualTo("Alpha");
        assertThat(view.getStandings().get(0).getPoints()).isEqualTo(3);
        assertThat(view.getMatches()).hasSize(2);
    }

    private List<UUID> sixIds() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 6; i++) ids.add(UUID.randomUUID());
        return ids;
    }

    private StandingRow standing(String name, int played, int wins) {
        StandingRow r = new StandingRow();
        r.setTeamId(UUID.randomUUID());
        r.setTeamName(name);
        r.setPlayed(played);
        r.setWins(wins);
        return r;
    }

    @Test
    void markChampion_leaderMathematicallySecured_setsChampion() {
        LeagueFormat format = LeagueFormat.forTeamCount(6).orElseThrow();
        StandingRow leader = standing("Alpha", format.getTotalRounds(), format.getTotalRounds());
        StandingRow rival = standing("Beta", format.getTotalRounds(), 0);

        service.markChampion(new ArrayList<>(List.of(leader, rival)), format);

        assertThat(leader.isChampion()).isTrue();
    }

    @Test
    void markChampion_rivalStillInContention_doesNotMark() {
        LeagueFormat format = LeagueFormat.forTeamCount(6).orElseThrow();
        StandingRow leader = standing("Alpha", 1, 1);
        StandingRow rival = standing("Beta", 1, 0);

        service.markChampion(new ArrayList<>(List.of(leader, rival)), format);

        assertThat(leader.isChampion()).isFalse();
    }

    @Test
    void markChampion_nullFormatOrNoPlays_returns() {
        StandingRow leader = standing("Alpha", 0, 0);
        StandingRow rival = standing("Beta", 0, 0);
        List<StandingRow> rows = new ArrayList<>(List.of(leader, rival));

        service.markChampion(rows, null);
        service.markChampion(rows, LeagueFormat.forTeamCount(6).orElseThrow());

        assertThat(leader.isChampion()).isFalse();
    }

    @Test
    void findDetail_tiedTeams_resolvedByHeadToHead() {
        Team alpha = team("Alpha");
        Team beta = team("Beta");
        League league = leagueWith(alpha, beta);
        when(leagueRepository.findById(league.getId())).thenReturn(java.util.Optional.of(league));

        MatchDto first = new MatchDto();
        first.setId(UUID.randomUUID());
        first.setHomeTeamId(alpha.getId());
        first.setAwayTeamId(beta.getId());
        first.setHomeScore(1);
        first.setAwayScore(0);
        first.setRoundNumber(1);
        first.setPlayedAt(LocalDateTime.now().minusDays(2));

        MatchDto second = new MatchDto();
        second.setId(UUID.randomUUID());
        second.setHomeTeamId(beta.getId());
        second.setAwayTeamId(alpha.getId());
        second.setHomeScore(1);
        second.setAwayScore(0);
        second.setRoundNumber(2);
        second.setPlayedAt(LocalDateTime.now().minusDays(1));

        when(matchService.findByLeague(league.getId())).thenReturn(List.of(first, second));

        LeagueDetailView view = service.findDetail(league.getId());

        assertThat(view.getStandings()).extracting(StandingRow::getTeamName)
                .containsExactly("Alpha", "Beta");
    }

    @Test
    void findDetail_liveMatch_countsLiveScore() {
        Team alpha = team("Alpha");
        Team beta = team("Beta");
        League league = leagueWith(alpha, beta);
        when(leagueRepository.findById(league.getId())).thenReturn(java.util.Optional.of(league));

        MatchDto live = new MatchDto();
        live.setId(UUID.randomUUID());
        live.setHomeTeamId(alpha.getId());
        live.setAwayTeamId(beta.getId());
        live.setRoundNumber(1);
        live.setPlayedAt(LocalDateTime.now().minusMinutes(30));
        GoalDto firstHalf = new GoalDto();
        firstHalf.setMinute(5);
        firstHalf.setHalf(Half.FIRST);
        firstHalf.setHomeGoal(true);
        GoalDto secondHalf = new GoalDto();
        secondHalf.setMinute(3);
        secondHalf.setHalf(Half.SECOND);
        secondHalf.setHomeGoal(false);
        GoalDto tooLate = new GoalDto();
        tooLate.setMinute(19);
        tooLate.setHalf(Half.SECOND);
        tooLate.setHomeGoal(false);
        live.getGoalTimeline().add(firstHalf);
        live.getGoalTimeline().add(secondHalf);
        live.getGoalTimeline().add(tooLate);

        when(matchService.findByLeague(league.getId())).thenReturn(List.of(live));

        LeagueDetailView view = service.findDetail(league.getId());

        StandingRow alphaRow = view.getStandings().stream()
                .filter(r -> "Alpha".equals(r.getTeamName())).findFirst().orElseThrow();
        assertThat(alphaRow.getPlayed()).isEqualTo(1);
        assertThat(alphaRow.getGoalsFor()).isEqualTo(1);
        assertThat(alphaRow.getGoalsAgainst()).isEqualTo(1);
    }

    @Test
    void findDetail_awayWin_andSkipsIncompleteMatches() {
        Team alpha = team("Alpha");
        Team beta = team("Beta");
        League league = leagueWith(alpha, beta);
        when(leagueRepository.findById(league.getId())).thenReturn(java.util.Optional.of(league));

        MatchDto awayWin = new MatchDto();
        awayWin.setId(UUID.randomUUID());
        awayWin.setHomeTeamId(alpha.getId());
        awayWin.setAwayTeamId(beta.getId());
        awayWin.setHomeScore(0);
        awayWin.setAwayScore(2);
        awayWin.setRoundNumber(1);
        awayWin.setPlayedAt(LocalDateTime.now().minusDays(1));

        MatchDto noScore = new MatchDto();
        noScore.setId(UUID.randomUUID());
        noScore.setHomeTeamId(alpha.getId());
        noScore.setAwayTeamId(beta.getId());
        noScore.setRoundNumber(2);
        noScore.setPlayedAt(LocalDateTime.now().minusDays(2));

        MatchDto noDate = new MatchDto();
        noDate.setId(UUID.randomUUID());
        noDate.setHomeTeamId(alpha.getId());
        noDate.setAwayTeamId(beta.getId());
        noDate.setRoundNumber(3);
        noDate.setPlayedAt(null);

        when(matchService.findByLeague(league.getId()))
                .thenReturn(new ArrayList<>(List.of(awayWin, noScore)));

        LeagueDetailView view = service.findDetail(league.getId());

        StandingRow betaRow = view.getStandings().stream()
                .filter(r -> "Beta".equals(r.getTeamName())).findFirst().orElseThrow();
        assertThat(betaRow.getWins()).isEqualTo(1);
        assertThat(betaRow.getPlayed()).isEqualTo(1);
    }

    @Test
    void findDetail_tiedWithDraws_headToHeadDrawBranch() {
        Team alpha = team("Alpha");
        Team beta = team("Beta");
        League league = leagueWith(alpha, beta);
        when(leagueRepository.findById(league.getId())).thenReturn(java.util.Optional.of(league));

        MatchDto d1 = new MatchDto();
        d1.setId(UUID.randomUUID());
        d1.setHomeTeamId(alpha.getId());
        d1.setAwayTeamId(beta.getId());
        d1.setHomeScore(1);
        d1.setAwayScore(1);
        d1.setRoundNumber(1);
        d1.setPlayedAt(LocalDateTime.now().minusDays(2));

        MatchDto d2 = new MatchDto();
        d2.setId(UUID.randomUUID());
        d2.setHomeTeamId(beta.getId());
        d2.setAwayTeamId(alpha.getId());
        d2.setHomeScore(2);
        d2.setAwayScore(2);
        d2.setRoundNumber(2);
        d2.setPlayedAt(LocalDateTime.now().minusDays(1));

        when(matchService.findByLeague(league.getId())).thenReturn(List.of(d1, d2));

        LeagueDetailView view = service.findDetail(league.getId());

        assertThat(view.getStandings()).extracting(StandingRow::getTeamName)
                .containsExactly("Alpha", "Beta");
    }

    @Test
    void create_nullTeamIds_throws() {
        LeagueDto dto = new LeagueDto();
        dto.setName("Cup");
        dto.setTeamIds(null);

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(InvalidLeagueOperationException.class);
    }

    @Test
    void findAll_noArg_usesDefaultSort() {
        when(leagueRepository.findAll(any(Sort.class))).thenReturn(List.of(leagueWith()));

        assertThat(service.findAll()).hasSize(1);
    }

    @Test
    void findById_found_returnsDto() {
        League league = leagueWith();
        when(leagueRepository.findById(league.getId())).thenReturn(java.util.Optional.of(league));

        assertThat(service.findById(league.getId()).getName()).isEqualTo("First League");
    }
}
