package com.kickoffsim.service.impl;

import com.kickoffsim.dto.WeatherDayDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class OpenMeteoDayClient {

    private static final String GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search";
    private static final String FORECAST_URL = "https://api.open-meteo.com/v1/forecast";

    private final RestClient restClient;

    public OpenMeteoDayClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Cacheable(value = "weatherForecast", key = "#city + '-' + #date")
    public Optional<WeatherDayDto> fetchDay(String city, LocalDate date) {
        try {
            return geocode(city).flatMap(coordinates -> fetchHourly(coordinates, date));
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
        if (response == null) {
            return Optional.empty();
        }
        JsonNode results = response.path("results");
        if (!results.isArray() || results.isEmpty()) {
            return Optional.empty();
        }
        JsonNode first = results.get(0);
        return Optional.of(new double[]{first.path("latitude").asDouble(), first.path("longitude").asDouble()});
    }

    private Optional<WeatherDayDto> fetchHourly(double[] coordinates, LocalDate date) {
        JsonNode response = restClient.get()
                .uri(FORECAST_URL + "?latitude={lat}&longitude={lon}&hourly=temperature_2m,precipitation_probability&timezone=auto&start_date={date}&end_date={date}",
                        Map.of("lat", coordinates[0], "lon", coordinates[1], "date", date))
                .retrieve()
                .body(JsonNode.class);
        if (response == null) {
            return Optional.empty();
        }
        JsonNode hourly = response.path("hourly");
        JsonNode temps = hourly.path("temperature_2m");
        if (!temps.isArray() || temps.isEmpty()) {
            return Optional.empty();
        }
        JsonNode precipitation = hourly.path("precipitation_probability");
        List<Double> hourlyTemps = new ArrayList<>();
        List<Integer> hourlyPrecipitation = new ArrayList<>();
        for (int i = 0; i < temps.size(); i++) {
            JsonNode temp = temps.get(i);
            hourlyTemps.add(temp.isNull() ? null : temp.asDouble());
            JsonNode chance = precipitation.isArray() && i < precipitation.size() ? precipitation.get(i) : null;
            hourlyPrecipitation.add(chance == null || chance.isNull() ? null : chance.asInt());
        }
        return Optional.of(new WeatherDayDto(date, hourlyTemps, hourlyPrecipitation));
    }
}
