package com.kickoffsim.service.impl;

import tools.jackson.databind.JsonNode;
import com.kickoffsim.dto.WeatherForecastDto;
import com.kickoffsim.service.WeatherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class WeatherServiceImpl implements WeatherService {

    private static final int MAX_FORECAST_DAYS = 15;
    private static final String GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search";
    private static final String FORECAST_URL = "https://api.open-meteo.com/v1/forecast";

    private final RestClient restClient;

    public WeatherServiceImpl(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    @Cacheable(value = "weatherForecast", key = "#city + '-' + #date")
    public Optional<WeatherForecastDto> forecastFor(String city, LocalDate date) {
        if (city == null || date == null) {
            return Optional.empty();
        }
        LocalDate today = LocalDate.now();
        if (date.isBefore(today) || date.isAfter(today.plusDays(MAX_FORECAST_DAYS))) {
            return Optional.empty();
        }
        try {
            return geocode(city).flatMap(coordinates -> fetchForecast(coordinates, date));
        } catch (RestClientException e) {
            log.warn("Weather lookup failed for city '{}': {}", city, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<double[]> geocode(String city) {
        JsonNode response = restClient.get()
                .uri(GEOCODING_URL + "?name={city}&count=1&language=en&format=json", city)
                .retrieve()
                .body(JsonNode.class);
        JsonNode results = response.path("results");
        if (!results.isArray() || results.isEmpty()) {
            return Optional.empty();
        }
        JsonNode first = results.get(0);
        return Optional.of(new double[]{first.path("latitude").asDouble(), first.path("longitude").asDouble()});
    }

    private Optional<WeatherForecastDto> fetchForecast(double[] coordinates, LocalDate date) {
        JsonNode response = restClient.get()
                .uri(FORECAST_URL + "?latitude={lat}&longitude={lon}&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&start_date={date}&end_date={date}",
                        Map.of("lat", coordinates[0], "lon", coordinates[1], "date", date))
                .retrieve()
                .body(JsonNode.class);
        JsonNode daily = response.path("daily");
        JsonNode maxTemps = daily.path("temperature_2m_max");
        if (!maxTemps.isArray() || maxTemps.isEmpty()) {
            return Optional.empty();
        }
        JsonNode minTemps = daily.path("temperature_2m_min");
        JsonNode precipitation = daily.path("precipitation_probability_max");
        Integer precipitationProbability = precipitation.isArray() && !precipitation.isEmpty()
                ? precipitation.get(0).asInt()
                : null;
        return Optional.of(new WeatherForecastDto(
                date,
                maxTemps.get(0).asDouble(),
                minTemps.get(0).asDouble(),
                precipitationProbability));
    }
}
