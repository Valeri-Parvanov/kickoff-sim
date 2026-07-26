package com.kickoffsim.repository;

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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:playerrepositorytest;DB_CLOSE_DELAY=-1;NON_KEYWORDS=MINUTE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class PlayerRepositoryTest {

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
    private PlayerRepository playerRepository;

    private void persistPlayers() {
        Team team = new Team();
        team.setName("Test FC");
        team.setStrength(60);
        entityManager.persist(team);

        Player popov = new Player();
        popov.setFirstName("Kostadin");
        popov.setLastName("Popov");
        popov.setShirtNumber(2);
        popov.setTeam(team);
        entityManager.persist(popov);

        Player aleksandrov = new Player();
        aleksandrov.setFirstName("Kostadin");
        aleksandrov.setLastName("Aleksandrov");
        aleksandrov.setShirtNumber(11);
        aleksandrov.setTeam(team);
        entityManager.persist(aleksandrov);

        Player other = new Player();
        other.setFirstName("Ivan");
        other.setLastName("Petrov");
        other.setShirtNumber(7);
        other.setTeam(team);
        entityManager.persist(other);

        entityManager.flush();
    }

    @Test
    void searchTop6ByFullName_firstNamePlusPartialLastName_matchesOnlyThatPlayer() {
        persistPlayers();

        List<Player> result = playerRepository.searchTop6ByFullName("Kostadin P", PageRequest.of(0, 6));

        assertThat(result).extracting(Player::getLastName).containsExactly("Popov");
    }

    @Test
    void searchTop6ByFullName_firstNameOnly_matchesAllSharingIt() {
        persistPlayers();

        List<Player> result = playerRepository.searchTop6ByFullName("Kostadin", PageRequest.of(0, 6));

        assertThat(result).extracting(Player::getLastName)
                .containsExactlyInAnyOrder("Popov", "Aleksandrov");
    }

    @Test
    void searchTop6ByFullName_noMatch_returnsEmpty() {
        persistPlayers();

        List<Player> result = playerRepository.searchTop6ByFullName("zzz", PageRequest.of(0, 6));

        assertThat(result).isEmpty();
    }
}
