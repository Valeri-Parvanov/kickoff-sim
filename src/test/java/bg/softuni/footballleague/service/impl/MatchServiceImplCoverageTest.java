package bg.softuni.footballleague.service.impl;

import bg.softuni.footballleague.client.NotificationClient;
import bg.softuni.footballleague.dto.GoalDto;
import bg.softuni.footballleague.dto.GoalEventDto;
import bg.softuni.footballleague.dto.MatchDto;
import bg.softuni.footballleague.exception.EntityNotFoundException;
import bg.softuni.footballleague.exception.InvalidGoalException;
import bg.softuni.footballleague.exception.InvalidMatchException;
import bg.softuni.footballleague.model.Goal;
import bg.softuni.footballleague.model.Half;
import bg.softuni.footballleague.model.Match;
import bg.softuni.footballleague.model.Player;
import bg.softuni.footballleague.model.Team;
import bg.softuni.footballleague.repository.GoalRepository;
import bg.softuni.footballleague.repository.MatchRepository;
import bg.softuni.footballleague.repository.PlayerRepository;
import bg.softuni.footballleague.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchServiceImplCoverageTest {

    @Mock private MatchRepository matchRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private GoalRepository goalRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private NotificationClient notificationClient;

    @InjectMocks private MatchServiceImpl service;

    private Team team(String name) {
        Team t = new Team();
        t.setId(UUID.randomUUID());
        t.setName(name);
        t.setCity("City");
        return t;
    }

    private Player player(Team t) {
        Player p = new Player();
        p.setId(UUID.randomUUID());
        p.setFirstName("Ivan");
        p.setLastName("Petrov");
        p.setTeam(t);
        return p;
    }

    private Match match(Team home, Team away) {
        Match m = new Match();
        m.setId(UUID.randomUUID());
        m.setHomeTeam(home);
        m.setAwayTeam(away);
        m.setHomeScore(2);
        m.setAwayScore(1);
        m.setPlayedAt(LocalDateTime.now().minusHours(2));
        return m;
    }

    @Test
    void create_sameTeams_throws() {
        Team t = team("A");
        MatchDto dto = new MatchDto();
        dto.setHomeTeamId(t.getId());
        dto.setAwayTeamId(t.getId());

        assertThatThrownBy(() -> service.create(dto)).isInstanceOf(InvalidMatchException.class);
    }

    @Test
    void create_valid_savesAndReturns() {
        Team home = team("Home");
        Team away = team("Away");
        MatchDto dto = new MatchDto();
        dto.setHomeTeamId(home.getId());
        dto.setAwayTeamId(away.getId());
        dto.setPlayedAt(LocalDateTime.now());
        when(teamRepository.findById(home.getId())).thenReturn(Optional.of(home));
        when(teamRepository.findById(away.getId())).thenReturn(Optional.of(away));
        when(matchRepository.save(any())).thenAnswer(a -> a.getArgument(0));

        MatchDto result = service.create(dto);

        assertThat(result.getHomeTeamName()).isEqualTo("Home");
    }

    @Test
    void findById_withGoal_buildsTimeline() {
        Team home = team("Home");
        Team away = team("Away");
        Match m = match(home, away);
        Player scorer = player(home);
        Goal goal = new Goal();
        goal.setId(UUID.randomUUID());
        goal.setHalf(Half.FIRST);
        goal.setMinute(10);
        goal.setScorer(scorer);
        m.getGoals().add(goal);
        when(matchRepository.findById(m.getId())).thenReturn(Optional.of(m));

        MatchDto dto = service.findById(m.getId());

        assertThat(dto.getGoalTimeline()).hasSize(1);
        assertThat(dto.getGoalTimeline().get(0).getRunningHomeScore()).isEqualTo(1);
    }

    @Test
    void findById_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(matchRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void update_broadcastsAndReturns() {
        Team home = team("Home");
        Team away = team("Away");
        Match m = match(home, away);
        when(matchRepository.findById(m.getId())).thenReturn(Optional.of(m));
        when(teamRepository.findById(home.getId())).thenReturn(Optional.of(home));
        when(teamRepository.findById(away.getId())).thenReturn(Optional.of(away));
        when(matchRepository.save(any())).thenAnswer(a -> a.getArgument(0));
        MatchDto dto = new MatchDto();
        dto.setHomeTeamId(home.getId());
        dto.setAwayTeamId(away.getId());
        dto.setPlayedAt(LocalDateTime.now());
        dto.setHomeScore(1);
        dto.setAwayScore(0);

        service.update(m.getId(), dto);

        verify(notificationClient).broadcast(any());
    }

    @Test
    void delete_deletesMatch() {
        Team home = team("Home");
        Team away = team("Away");
        Match m = match(home, away);
        when(matchRepository.findById(m.getId())).thenReturn(Optional.of(m));

        service.delete(m.getId());

        verify(matchRepository).delete(m);
    }

    @Test
    void addGoal_valid_savesAndBroadcasts() {
        Team home = team("Home");
        Team away = team("Away");
        Match m = match(home, away);
        Player scorer = player(home);
        when(matchRepository.findById(m.getId())).thenReturn(Optional.of(m));
        when(playerRepository.findById(scorer.getId())).thenReturn(Optional.of(scorer));
        when(goalRepository.countGoalsBenefitingTeam(any(), any(), any())).thenReturn(0L);
        when(goalRepository.countByMatchAndHalfAndMinuteExcluding(any(), any(), any(), any())).thenReturn(0L);

        GoalEventDto dto = new GoalEventDto();
        dto.setScorerId(scorer.getId());
        dto.setMinute(10);

        service.addGoal(m.getId(), dto);

        verify(goalRepository).save(any());
    }

    @Test
    void addGoal_scorerNotInMatch_throws() {
        Team home = team("Home");
        Team away = team("Away");
        Match m = match(home, away);
        Player outsider = player(team("Other"));
        when(matchRepository.findById(m.getId())).thenReturn(Optional.of(m));
        when(playerRepository.findById(outsider.getId())).thenReturn(Optional.of(outsider));

        GoalEventDto dto = new GoalEventDto();
        dto.setScorerId(outsider.getId());

        assertThatThrownBy(() -> service.addGoal(m.getId(), dto))
                .isInstanceOf(InvalidGoalException.class);
    }

    @Test
    void findGoalById_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(goalRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findGoalById(id)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void deleteGoal_deletes() {
        Goal goal = new Goal();
        goal.setId(UUID.randomUUID());
        when(goalRepository.findById(goal.getId())).thenReturn(Optional.of(goal));

        service.deleteGoal(goal.getId());

        verify(goalRepository).delete(goal);
    }

    @Test
    void findByLeague_mapsDtos() {
        Team home = team("Home");
        Team away = team("Away");
        when(matchRepository.findByLeagueId(any())).thenReturn(List.of(match(home, away)));

        assertThat(service.findByLeague(UUID.randomUUID())).hasSize(1);
    }

    @Test
    void findByDate_mapsDtos() {
        Team home = team("Home");
        Team away = team("Away");
        when(matchRepository.findByDateRange(any(), any())).thenReturn(List.of(match(home, away)));

        assertThat(service.findByDate(LocalDate.now())).hasSize(1);
    }

    @Test
    void findAllMatchDates_returnsDistinctSorted() {
        when(matchRepository.findAllPlayedAtTimes())
                .thenReturn(List.of(LocalDateTime.of(2026, 1, 2, 10, 0), LocalDateTime.of(2026, 1, 1, 10, 0)));

        assertThat(service.findAllMatchDates()).containsExactly(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2));
    }

    @Test
    void findAllMatchUtcIsos_returnsIsoStrings() {
        when(matchRepository.findAllPlayedAtTimes())
                .thenReturn(List.of(LocalDateTime.of(2026, 6, 1, 12, 0)));

        assertThat(service.findAllMatchUtcIsos()).hasSize(1);
    }

    @Test
    void addGoal_ownGoalWithAssistant_throws() {
        Team home = team("Home");
        Team away = team("Away");
        Match m = match(home, away);
        Player scorer = player(home);
        when(matchRepository.findById(m.getId())).thenReturn(Optional.of(m));
        when(playerRepository.findById(scorer.getId())).thenReturn(Optional.of(scorer));
        when(goalRepository.countGoalsBenefitingTeam(any(), any(), any())).thenReturn(0L);
        when(goalRepository.countByMatchAndHalfAndMinuteExcluding(any(), any(), any(), any())).thenReturn(0L);
        GoalEventDto dto = new GoalEventDto();
        dto.setScorerId(scorer.getId());
        dto.setOwnGoal(true);
        dto.setAssistantId(UUID.randomUUID());
        dto.setMinute(10);

        assertThatThrownBy(() -> service.addGoal(m.getId(), dto))
                .isInstanceOf(InvalidGoalException.class);
    }

    @Test
    void addGoal_duplicateMinute_throws() {
        Team home = team("Home");
        Team away = team("Away");
        Match m = match(home, away);
        Player scorer = player(home);
        when(matchRepository.findById(m.getId())).thenReturn(Optional.of(m));
        when(playerRepository.findById(scorer.getId())).thenReturn(Optional.of(scorer));
        when(goalRepository.countGoalsBenefitingTeam(any(), any(), any())).thenReturn(0L);
        when(goalRepository.countByMatchAndHalfAndMinuteExcluding(any(), any(), any(), any())).thenReturn(1L);
        GoalEventDto dto = new GoalEventDto();
        dto.setScorerId(scorer.getId());
        dto.setMinute(25);

        assertThatThrownBy(() -> service.addGoal(m.getId(), dto))
                .isInstanceOf(InvalidGoalException.class);
    }

    @Test
    void addGoal_exceedsDeclaredScore_throws() {
        Team home = team("Home");
        Team away = team("Away");
        Match m = match(home, away);
        Player scorer = player(home);
        when(matchRepository.findById(m.getId())).thenReturn(Optional.of(m));
        when(playerRepository.findById(scorer.getId())).thenReturn(Optional.of(scorer));
        when(goalRepository.countGoalsBenefitingTeam(any(), any(), any())).thenReturn(2L);
        GoalEventDto dto = new GoalEventDto();
        dto.setScorerId(scorer.getId());

        assertThatThrownBy(() -> service.addGoal(m.getId(), dto))
                .isInstanceOf(InvalidGoalException.class);
    }

    @Test
    void addGoal_selfAssist_throws() {
        Team home = team("Home");
        Team away = team("Away");
        Match m = match(home, away);
        Player scorer = player(home);
        when(matchRepository.findById(m.getId())).thenReturn(Optional.of(m));
        when(playerRepository.findById(scorer.getId())).thenReturn(Optional.of(scorer));
        when(goalRepository.countGoalsBenefitingTeam(any(), any(), any())).thenReturn(0L);
        when(goalRepository.countByMatchAndHalfAndMinuteExcluding(any(), any(), any(), any())).thenReturn(0L);
        GoalEventDto dto = new GoalEventDto();
        dto.setScorerId(scorer.getId());
        dto.setAssistantId(scorer.getId());
        dto.setMinute(10);

        assertThatThrownBy(() -> service.addGoal(m.getId(), dto))
                .isInstanceOf(InvalidGoalException.class);
    }

    @Test
    void addGoal_assistantFromOtherTeam_throws() {
        Team home = team("Home");
        Team away = team("Away");
        Match m = match(home, away);
        Player scorer = player(home);
        Player assistant = player(away);
        when(matchRepository.findById(m.getId())).thenReturn(Optional.of(m));
        when(playerRepository.findById(scorer.getId())).thenReturn(Optional.of(scorer));
        when(playerRepository.findById(assistant.getId())).thenReturn(Optional.of(assistant));
        when(goalRepository.countGoalsBenefitingTeam(any(), any(), any())).thenReturn(0L);
        when(goalRepository.countByMatchAndHalfAndMinuteExcluding(any(), any(), any(), any())).thenReturn(0L);
        GoalEventDto dto = new GoalEventDto();
        dto.setScorerId(scorer.getId());
        dto.setAssistantId(assistant.getId());
        dto.setMinute(10);

        assertThatThrownBy(() -> service.addGoal(m.getId(), dto))
                .isInstanceOf(InvalidGoalException.class);
    }

    @Test
    void addGoal_validAssistant_saves() {
        Team home = team("Home");
        Team away = team("Away");
        Match m = match(home, away);
        Player scorer = player(home);
        Player assistant = player(home);
        when(matchRepository.findById(m.getId())).thenReturn(Optional.of(m));
        when(playerRepository.findById(scorer.getId())).thenReturn(Optional.of(scorer));
        when(playerRepository.findById(assistant.getId())).thenReturn(Optional.of(assistant));
        when(goalRepository.countGoalsBenefitingTeam(any(), any(), any())).thenReturn(0L);
        when(goalRepository.countByMatchAndHalfAndMinuteExcluding(any(), any(), any(), any())).thenReturn(0L);
        GoalEventDto dto = new GoalEventDto();
        dto.setScorerId(scorer.getId());
        dto.setAssistantId(assistant.getId());
        dto.setMinute(25);

        service.addGoal(m.getId(), dto);

        verify(goalRepository).save(any());
    }

    @Test
    void updateGoal_valid_saves() {
        Team home = team("Home");
        Team away = team("Away");
        Match m = match(home, away);
        Player scorer = player(home);
        Goal goal = new Goal();
        goal.setId(UUID.randomUUID());
        goal.setMatch(m);
        when(goalRepository.findById(goal.getId())).thenReturn(Optional.of(goal));
        when(playerRepository.findById(scorer.getId())).thenReturn(Optional.of(scorer));
        when(goalRepository.countGoalsBenefitingTeam(any(), any(), any())).thenReturn(0L);
        GoalEventDto dto = new GoalEventDto();
        dto.setScorerId(scorer.getId());

        service.updateGoal(goal.getId(), dto);

        verify(goalRepository).save(goal);
    }

    @Test
    void updateGoal_scorerNotInMatch_throws() {
        Team home = team("Home");
        Team away = team("Away");
        Match m = match(home, away);
        Player outsider = player(team("Other"));
        Goal goal = new Goal();
        goal.setId(UUID.randomUUID());
        goal.setMatch(m);
        when(goalRepository.findById(goal.getId())).thenReturn(Optional.of(goal));
        when(playerRepository.findById(outsider.getId())).thenReturn(Optional.of(outsider));
        GoalEventDto dto = new GoalEventDto();
        dto.setScorerId(outsider.getId());

        assertThatThrownBy(() -> service.updateGoal(goal.getId(), dto))
                .isInstanceOf(InvalidGoalException.class);
    }

    @Test
    void updateGoal_missing_throws() {
        UUID id = UUID.randomUUID();
        when(goalRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateGoal(id, new GoalEventDto()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void findAll_noArg_usesDefaultSort() {
        when(matchRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(match(team("Home"), team("Away"))));

        assertThat(service.findAll()).hasSize(1);
    }

    @Test
    void findById_ownGoalNullMinuteAndLeague_buildsFullTimeline() {
        Team home = team("Home");
        Team away = team("Away");
        bg.softuni.footballleague.model.League league = new bg.softuni.footballleague.model.League();
        league.setId(UUID.randomUUID());
        league.setName("Premier");
        home.setLeague(league);
        Match m = match(home, away);

        Goal ownGoal = new Goal();
        ownGoal.setId(UUID.randomUUID());
        ownGoal.setScorer(player(away));
        ownGoal.setOwnGoal(true);
        ownGoal.setHalf(Half.FIRST);
        ownGoal.setMinute(3);

        Goal awayGoal = new Goal();
        awayGoal.setId(UUID.randomUUID());
        awayGoal.setScorer(player(away));
        awayGoal.setHalf(Half.FIRST);
        awayGoal.setMinute(5);

        Goal noMinute = new Goal();
        noMinute.setId(UUID.randomUUID());
        noMinute.setScorer(player(home));
        noMinute.setHalf(Half.SECOND);
        noMinute.setMinute(null);

        m.getGoals().add(ownGoal);
        m.getGoals().add(awayGoal);
        m.getGoals().add(noMinute);
        when(matchRepository.findById(m.getId())).thenReturn(Optional.of(m));

        MatchDto dto = service.findById(m.getId());

        assertThat(dto.getLeagueName()).isEqualTo("Premier");
        assertThat(dto.getGoalTimeline()).hasSize(3);
        assertThat(dto.getHomeHalfScore()).isEqualTo(1);
        assertThat(dto.getAwayHalfScore()).isEqualTo(1);
    }

    @Test
    void update_withLeague_broadcastsWithLeagueId() {
        Team home = team("Home");
        bg.softuni.footballleague.model.League league = new bg.softuni.footballleague.model.League();
        league.setId(UUID.randomUUID());
        home.setLeague(league);
        Team away = team("Away");
        Match m = match(home, away);
        when(matchRepository.findById(m.getId())).thenReturn(Optional.of(m));
        when(teamRepository.findById(home.getId())).thenReturn(Optional.of(home));
        when(teamRepository.findById(away.getId())).thenReturn(Optional.of(away));
        when(matchRepository.save(any())).thenAnswer(a -> a.getArgument(0));
        MatchDto dto = new MatchDto();
        dto.setHomeTeamId(home.getId());
        dto.setAwayTeamId(away.getId());
        dto.setPlayedAt(LocalDateTime.now());

        service.update(m.getId(), dto);

        verify(notificationClient).broadcast(any());
    }

    @Test
    void findGoalById_found_mapsAssistant() {
        Team home = team("Home");
        Player scorer = player(home);
        Player assistant = player(home);
        Goal goal = new Goal();
        goal.setId(UUID.randomUUID());
        goal.setScorer(scorer);
        goal.setAssistant(assistant);
        goal.setHalf(Half.FIRST);
        goal.setMinute(9);
        when(goalRepository.findById(goal.getId())).thenReturn(Optional.of(goal));

        assertThat(service.findGoalById(goal.getId()).getAssistantId()).isEqualTo(assistant.getId());
    }
}
