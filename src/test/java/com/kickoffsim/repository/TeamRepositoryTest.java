package com.kickoffsim.repository;

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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:teamrepositorytest;DB_CLOSE_DELAY=-1;NON_KEYWORDS=MINUTE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class TeamRepositoryTest {

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
    private TeamRepository teamRepository;

    private void persistTeams() {
        Team tundzha = new Team();
        tundzha.setName("Tundzha");
        tundzha.setCity("Yambol");
        tundzha.setStrength(60);
        entityManager.persist(tundzha);

        Team other = new Team();
        other.setName("Vihor");
        other.setCity("Sofia");
        other.setStrength(60);
        entityManager.persist(other);

        Team noCity = new Team();
        noCity.setName("Feniks");
        noCity.setStrength(60);
        entityManager.persist(noCity);

        entityManager.flush();
    }

    @Test
    void searchTop6ByNameOrCity_namePlusPartialCity_matchesOnlyThatTeam() {
        persistTeams();

        List<Team> result = teamRepository.searchTop6ByNameOrCity("Tundzha Y", PageRequest.of(0, 6));

        assertThat(result).extracting(Team::getName).containsExactly("Tundzha");
    }

    @Test
    void searchTop6ByNameOrCity_cityOnly_matchesThatTeam() {
        persistTeams();

        List<Team> result = teamRepository.searchTop6ByNameOrCity("Yambol", PageRequest.of(0, 6));

        assertThat(result).extracting(Team::getName).containsExactly("Tundzha");
    }

    @Test
    void searchTop6ByNameOrCity_teamWithoutCity_matchesByNameOnly() {
        persistTeams();

        List<Team> result = teamRepository.searchTop6ByNameOrCity("Feniks", PageRequest.of(0, 6));

        assertThat(result).extracting(Team::getName).containsExactly("Feniks");
    }

    @Test
    void searchTop6ByNameOrCity_noMatch_returnsEmpty() {
        persistTeams();

        List<Team> result = teamRepository.searchTop6ByNameOrCity("zzz", PageRequest.of(0, 6));

        assertThat(result).isEmpty();
    }
}
