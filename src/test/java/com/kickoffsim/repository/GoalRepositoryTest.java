package com.kickoffsim.repository;

import com.kickoffsim.model.Goal;
import com.kickoffsim.model.Half;
import com.kickoffsim.model.Match;
import com.kickoffsim.model.Player;
import com.kickoffsim.model.Team;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:goalrepositorytest;DB_CLOSE_DELAY=-1;NON_KEYWORDS=MINUTE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class GoalRepositoryTest {

    @TestConfiguration
    static class CacheTestConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private GoalRepository goalRepository;

    private Team team;
    private Team opponent;
    private Player scorer;
    private Player assistant;

    private void setUpTeamAndPlayers() {
        team = new Team();
        team.setName("Home FC");
        team.setCity("Sofia");
        team.setStrength(60);
        entityManager.persist(team);

        opponent = new Team();
        opponent.setName("Away FC");
        opponent.setCity("Plovdiv");
        opponent.setStrength(60);
        entityManager.persist(opponent);

        scorer = new Player();
        scorer.setFirstName("Ivan");
        scorer.setLastName("Ivanov");
        scorer.setShirtNumber(9);
        scorer.setTeam(team);
        entityManager.persist(scorer);

        assistant = new Player();
        assistant.setFirstName("Petar");
        assistant.setLastName("Petrov");
        assistant.setShirtNumber(10);
        assistant.setTeam(team);
        entityManager.persist(assistant);
    }

    private Match persistMatch(LocalDateTime playedAt) {
        Match match = new Match();
        match.setHomeTeam(team);
        match.setAwayTeam(opponent);
        match.setPlayedAt(playedAt);
        entityManager.persist(match);
        return match;
    }

    private Goal persistGoal(Match match) {
        Goal goal = new Goal();
        goal.setMatch(match);
        goal.setScorer(scorer);
        goal.setAssistant(assistant);
        goal.setHalf(Half.FIRST);
        goal.setMinute(10);
        entityManager.persist(goal);
        return goal;
    }

    @Test
    void countGoalsByTeamGroupedByScorer_excludesGoalsFromFutureMatches() {
        setUpTeamAndPlayers();
        Match playedMatch = persistMatch(LocalDateTime.now().minusDays(1));
        Match futureMatch = persistMatch(LocalDateTime.now().plusDays(1));
        persistGoal(playedMatch);
        persistGoal(futureMatch);
        entityManager.flush();

        List<Object[]> result = goalRepository.countGoalsByTeamGroupedByScorer(team.getId(), LocalDateTime.now());

        assertThat(result).hasSize(1);
        assertThat(result.get(0)[0]).isEqualTo(scorer.getId());
        assertThat(result.get(0)[1]).isEqualTo(1L);
    }

    @Test
    void countAssistsByTeamGroupedByAssistant_excludesAssistsFromFutureMatches() {
        setUpTeamAndPlayers();
        Match playedMatch = persistMatch(LocalDateTime.now().minusDays(1));
        Match futureMatch = persistMatch(LocalDateTime.now().plusDays(1));
        persistGoal(playedMatch);
        persistGoal(futureMatch);
        entityManager.flush();

        List<Object[]> result = goalRepository.countAssistsByTeamGroupedByAssistant(team.getId(), LocalDateTime.now());

        assertThat(result).hasSize(1);
        assertThat(result.get(0)[0]).isEqualTo(assistant.getId());
        assertThat(result.get(0)[1]).isEqualTo(1L);
    }

    @Test
    void countGoalsByTeamGroupedByScorer_noPlayedMatches_returnsEmpty() {
        setUpTeamAndPlayers();
        Match futureMatch = persistMatch(LocalDateTime.now().plusDays(1));
        persistGoal(futureMatch);
        entityManager.flush();

        List<Object[]> result = goalRepository.countGoalsByTeamGroupedByScorer(team.getId(), LocalDateTime.now());

        assertThat(result).isEmpty();
    }

    @Test
    void countGoalsByTeamGroupedByScorer_unknownTeam_returnsEmpty() {
        List<Object[]> result = goalRepository.countGoalsByTeamGroupedByScorer(UUID.randomUUID(), LocalDateTime.now());

        assertThat(result).isEmpty();
    }
}
