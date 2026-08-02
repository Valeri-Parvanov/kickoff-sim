package com.kickoffsim.config;

import com.github.benmanes.caffeine.cache.Policy;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheConfigTest {

    private final CacheManager cacheManager = new CacheConfig().cacheManager();

    private Optional<Policy.FixedExpiration<Object, Object>> expiryOf(String name) {
        CaffeineCache cache = (CaffeineCache) cacheManager.getCache(name);
        assertNotNull(cache);
        return cache.getNativeCache().policy().expireAfterWrite();
    }

    @Test
    void leaguesCache_isRegisteredWithoutExpiry() {
        assertTrue(expiryOf(CacheConfig.LEAGUES).isEmpty());
    }

    @Test
    void leagueDetailCache_expiresAfterConfiguredTtl() {
        Optional<Policy.FixedExpiration<Object, Object>> expiry = expiryOf(CacheConfig.LEAGUE_DETAIL);

        assertTrue(expiry.isPresent());
        assertEquals(Duration.ofSeconds(3), expiry.get().getExpiresAfter());
        assertEquals(CacheConfig.LEAGUE_DETAIL_TTL, expiry.get().getExpiresAfter());
    }

    @Test
    void leagueStandingsCache_expiresAfterConfiguredTtl() {
        Optional<Policy.FixedExpiration<Object, Object>> expiry = expiryOf(CacheConfig.LEAGUE_STANDINGS);

        assertTrue(expiry.isPresent());
        assertEquals(Duration.ofSeconds(30), expiry.get().getExpiresAfter());
        assertEquals(CacheConfig.LEAGUE_STANDINGS_TTL, expiry.get().getExpiresAfter());
    }

    @Test
    void weatherForecastCache_expiresAfterConfiguredTtl() {
        Optional<Policy.FixedExpiration<Object, Object>> expiry = expiryOf(CacheConfig.WEATHER_FORECAST);

        assertTrue(expiry.isPresent());
        assertEquals(Duration.ofHours(1), expiry.get().getExpiresAfter());
        assertEquals(CacheConfig.WEATHER_FORECAST_TTL, expiry.get().getExpiresAfter());
    }

    @Test
    void unknownCacheName_isCreatedOnDemand() {
        assertNotNull(cacheManager.getCache("someOtherCache"));
    }

    @Test
    void caches_allowNullValues() {
        CaffeineCache cache = (CaffeineCache) cacheManager.getCache(CacheConfig.LEAGUE_DETAIL);
        assertNotNull(cache);

        cache.put("k", null);

        assertNotNull(cache.get("k"));
        assertEquals(null, cache.get("k").get());
    }
}
