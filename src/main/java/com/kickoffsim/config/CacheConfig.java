package com.kickoffsim.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CacheConfig {

    public static final String LEAGUES = "leagues";
    public static final String LEAGUE_DETAIL = "leagueDetail";
    public static final String LEAGUE_STANDINGS = "leagueStandings";
    public static final String WEATHER_FORECAST = "weatherForecast";

    static final Duration LEAGUE_DETAIL_TTL = Duration.ofSeconds(3);
    static final Duration LEAGUE_STANDINGS_TTL = Duration.ofSeconds(30);
    static final Duration WEATHER_FORECAST_TTL = Duration.ofHours(1);

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setAllowNullValues(true);

        manager.registerCustomCache(LEAGUES, Caffeine.newBuilder()
                .maximumSize(200)
                .build());

        manager.registerCustomCache(LEAGUE_DETAIL, Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(LEAGUE_DETAIL_TTL)
                .build());

        manager.registerCustomCache(LEAGUE_STANDINGS, Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(LEAGUE_STANDINGS_TTL)
                .build());

        manager.registerCustomCache(WEATHER_FORECAST, Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(WEATHER_FORECAST_TTL)
                .build());

        return manager;
    }
}
