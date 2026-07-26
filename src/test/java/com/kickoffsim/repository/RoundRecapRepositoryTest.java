package com.kickoffsim.repository;

import com.kickoffsim.model.League;
import com.kickoffsim.model.RoundRecap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class RoundRecapRepositoryTest {

    @Autowired
    private LeagueRepository leagueRepository;

    @Autowired
    private RoundRecapRepository roundRecapRepository;

    @MockitoBean
    private CacheManager cacheManager;

    @Test
    void findReturnsLocaleSpecificRecap() {
        League league = leagueRepository.save(league("Test League"));
        roundRecapRepository.saveAndFlush(recap(league, 1, "bg", "Български"));
        roundRecapRepository.saveAndFlush(recap(league, 1, "en", "English"));

        assertThat(roundRecapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(
                league.getId(), 1, "bg")).get()
                .extracting(RoundRecap::getContent)
                .isEqualTo("Български");
    }

    @Test
    void uniqueConstraintRejectsSameLeagueRoundAndLocale() {
        League league = leagueRepository.save(league("Unique League"));
        roundRecapRepository.saveAndFlush(recap(league, 2, "de", "Erster"));

        assertThatThrownBy(() ->
                roundRecapRepository.saveAndFlush(recap(league, 2, "de", "Zweiter")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void uniqueConstraintAllowsDifferentRoundLocaleOrLeague() {
        League firstLeague = leagueRepository.save(league("First League"));
        League secondLeague = leagueRepository.save(league("Second League"));

        roundRecapRepository.saveAndFlush(recap(firstLeague, 1, "en", "First"));
        roundRecapRepository.saveAndFlush(recap(firstLeague, 2, "en", "Second"));
        roundRecapRepository.saveAndFlush(recap(firstLeague, 1, "de", "Deutsch"));
        roundRecapRepository.saveAndFlush(recap(secondLeague, 1, "en", "Other league"));

        assertThat(roundRecapRepository.count()).isEqualTo(4);
    }

    private League league(String name) {
        League league = new League();
        league.setName(name);
        return league;
    }

    private RoundRecap recap(League league, int round, String locale, String content) {
        RoundRecap recap = new RoundRecap();
        recap.setLeague(league);
        recap.setRoundNumber(round);
        recap.setLocaleTag(locale);
        recap.setContent(content);
        recap.setGeneratedAt(LocalDateTime.now());
        recap.setSourceFingerprint("a".repeat(64));
        return recap;
    }
}
