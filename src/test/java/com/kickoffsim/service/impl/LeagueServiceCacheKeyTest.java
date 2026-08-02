package com.kickoffsim.service.impl;

import com.kickoffsim.dto.LeagueDetailView;
import com.kickoffsim.dto.LeagueDto;
import com.kickoffsim.model.League;
import com.kickoffsim.repository.LeagueRepository;
import com.kickoffsim.repository.MatchRepository;
import com.kickoffsim.repository.PlayerRepository;
import com.kickoffsim.repository.TeamRepository;
import com.kickoffsim.service.LeagueService;
import com.kickoffsim.service.MatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(LeagueServiceCacheKeyTest.CacheTestConfig.class)
class LeagueServiceCacheKeyTest {

    private static final UUID ALL_ID = UUID.randomUUID();
    private static final UUID OPTION_ID = UUID.randomUUID();
    private static final UUID DETAIL_ID = UUID.randomUUID();
    private static final UUID OTHER_DETAIL_ID = UUID.randomUUID();

    @Configuration
    @EnableCaching
    static class CacheTestConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("leagues", "leagueDetail", "leagueStandings");
        }

        @Bean
        LeagueRepository leagueRepository() {
            return mock(LeagueRepository.class);
        }

        @Bean
        MatchRepository matchRepository() {
            return mock(MatchRepository.class);
        }

        @Bean
        TeamRepository teamRepository() {
            return mock(TeamRepository.class);
        }

        @Bean
        PlayerRepository playerRepository() {
            return mock(PlayerRepository.class);
        }

        @Bean
        MatchService matchService() {
            return mock(MatchService.class);
        }

        @Bean
        LeagueService leagueService(LeagueRepository leagueRepository,
                                    MatchRepository matchRepository,
                                    TeamRepository teamRepository,
                                    PlayerRepository playerRepository,
                                    MatchService matchService) {
            return new LeagueServiceImpl(leagueRepository, matchRepository, teamRepository,
                    playerRepository, matchService);
        }
    }

    @Autowired
    private LeagueService leagueService;

    @Autowired
    private LeagueRepository leagueRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private MatchService matchService;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        reset(leagueRepository, matchRepository, matchService);

        cacheManager.getCacheNames().forEach(name -> {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });

        League league = new League();
        league.setId(ALL_ID);
        league.setName("Full League");

        when(leagueRepository.findAll(any(Sort.class))).thenReturn(List.of(league));
        when(matchRepository.countMatchesGroupedByLeague(any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(new Object[]{ALL_ID, 10L, 4L}));

        LeagueRepository.LeagueOption option = mock(LeagueRepository.LeagueOption.class);
        when(option.getId()).thenReturn(OPTION_ID);
        when(option.getName()).thenReturn("Option League");

        when(leagueRepository.findAllOptions()).thenReturn(List.of(option));

        League detailLeague = new League();
        detailLeague.setId(DETAIL_ID);
        detailLeague.setName("Detail League");

        League otherDetailLeague = new League();
        otherDetailLeague.setId(OTHER_DETAIL_ID);
        otherDetailLeague.setName("Other Detail League");

        when(leagueRepository.findByIdWithTeams(DETAIL_ID)).thenReturn(Optional.of(detailLeague));
        when(leagueRepository.findByIdWithTeams(OTHER_DETAIL_ID)).thenReturn(Optional.of(otherDetailLeague));
        when(matchService.findByLeague(any(UUID.class))).thenReturn(List.of());
    }

    @Test
    void findAllOptionsFirst_doesNotPoisonFindAll() {
        List<LeagueDto> options = leagueService.findAllOptions();
        List<LeagueDto> all = leagueService.findAll();

        assertEquals(OPTION_ID, options.get(0).getId());
        assertEquals("Option League", options.get(0).getName());

        assertEquals(ALL_ID, all.get(0).getId());
        assertEquals("Full League", all.get(0).getName());
        assertEquals(10L, all.get(0).getTotalMatches());
        assertEquals(4L, all.get(0).getPlayedMatches());
    }

    @Test
    void findAllFirst_doesNotPoisonFindAllOptions() {
        List<LeagueDto> all = leagueService.findAll();
        List<LeagueDto> options = leagueService.findAllOptions();

        assertEquals(ALL_ID, all.get(0).getId());
        assertEquals(10L, all.get(0).getTotalMatches());

        assertEquals(OPTION_ID, options.get(0).getId());
        assertEquals("Option League", options.get(0).getName());
        assertEquals(0L, options.get(0).getTotalMatches());
    }

    @Test
    void findAll_isServedFromCacheOnSecondCall() {
        leagueService.findAll();
        leagueService.findAll();

        verify(leagueRepository, times(1)).findAll(any(Sort.class));
    }

    @Test
    void findAllOptions_isServedFromCacheOnSecondCall() {
        leagueService.findAllOptions();
        leagueService.findAllOptions();

        verify(leagueRepository, times(1)).findAllOptions();
    }

    @Test
    void findDetail_isServedFromCacheOnSecondCall() {
        leagueService.findDetail(DETAIL_ID);
        leagueService.findDetail(DETAIL_ID);

        verify(leagueRepository, times(1)).findByIdWithTeams(DETAIL_ID);
        verify(matchService, times(1)).findByLeague(DETAIL_ID);
    }

    @Test
    void findStandings_isCachedSeparatelyFromDetail() {
        leagueService.findStandings(DETAIL_ID);
        leagueService.findStandings(DETAIL_ID);

        verify(leagueRepository, times(1)).findByIdWithTeams(DETAIL_ID);
        assertNotNull(cacheManager.getCache("leagueStandings"));
        assertNotNull(cacheManager.getCache("leagueStandings").get(DETAIL_ID));
    }

    @Test
    void findDetail_isKeyedByLeagueId() {
        LeagueDetailView first = leagueService.findDetail(DETAIL_ID);
        LeagueDetailView second = leagueService.findDetail(OTHER_DETAIL_ID);

        assertEquals("Detail League", first.getName());
        assertEquals("Other Detail League", second.getName());

        verify(leagueRepository, times(1)).findByIdWithTeams(DETAIL_ID);
        verify(leagueRepository, times(1)).findByIdWithTeams(OTHER_DETAIL_ID);
    }

    @Test
    void leagueDetailCache_isClearedOnLeagueUpdate() {
        leagueService.findDetail(DETAIL_ID);

        Cache detailCache = cacheManager.getCache("leagueDetail");
        assertNotNull(detailCache);
        assertNotNull(detailCache.get(DETAIL_ID));

        League updated = new League();
        updated.setId(DETAIL_ID);
        updated.setName("Detail League");
        when(leagueRepository.save(any(League.class))).thenReturn(updated);

        LeagueDto dto = new LeagueDto();
        dto.setName("Renamed League");
        leagueService.update(DETAIL_ID, dto);

        assertNull(detailCache.get(DETAIL_ID));
    }
}
