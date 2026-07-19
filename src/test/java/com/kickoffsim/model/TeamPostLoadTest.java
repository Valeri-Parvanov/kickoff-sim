package com.kickoffsim.model;

import com.kickoffsim.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class TeamPostLoadTest {

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

    @Test
    void legacyTeamWithZeroStrength_backfillsOnLoad() {
        Team team = new Team();
        team.setName("Alpha");
        team.setCity("Sofia");
        team.setStrength(0);
        Team persisted = entityManager.persistFlushFind(team);
        entityManager.clear();

        Team reloaded = teamRepository.findById(persisted.getId()).orElseThrow();

        assertThat(reloaded.getStrength()).isBetween(20, 95);
    }

    @Test
    void teamWithPositiveStrength_keepsStoredValueOnLoad() {
        Team team = new Team();
        team.setName("Beta");
        team.setCity("Plovdiv");
        team.setStrength(75);
        Team persisted = entityManager.persistFlushFind(team);
        entityManager.clear();

        Team reloaded = teamRepository.findById(persisted.getId()).orElseThrow();

        assertThat(reloaded.getStrength()).isEqualTo(75);
    }
}
