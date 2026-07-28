package com.kickoffsim.service.impl;

import com.kickoffsim.dto.WeatherDayDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenMeteoDayClientTest {

    private static final String GEOCODE_OK = "{\"results\":[{\"latitude\":42.5,\"longitude\":23.3}]}";

    private MockRestServiceServer mockServer;
    private OpenMeteoDayClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new OpenMeteoDayClient(builder);
    }

    @Test
    void fetchDay_returnsEmpty_whenGeocodingHasNoResults() {
        mockServer.expect(requestTo(containsString("geocoding-api.open-meteo.com")))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));

        assertThat(client.fetchDay("Nowhereville", LocalDate.now())).isEmpty();
    }

    @Test
    void fetchDay_returnsEmpty_whenGeocodingResultsMissing() {
        mockServer.expect(requestTo(containsString("geocoding-api.open-meteo.com")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.fetchDay("Nowhereville", LocalDate.now())).isEmpty();
    }

    @Test
    void fetchDay_returnsEmpty_whenGeocodingBodyIsEmpty() {
        mockServer.expect(requestTo(containsString("geocoding-api.open-meteo.com")))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThat(client.fetchDay("Sofia", LocalDate.now())).isEmpty();
    }

    @Test
    void fetchDay_returnsEmpty_whenForecastBodyIsEmpty() {
        mockServer.expect(requestTo(containsString("geocoding-api.open-meteo.com")))
                .andRespond(withSuccess(GEOCODE_OK, MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(containsString("api.open-meteo.com/v1/forecast")))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThat(client.fetchDay("Sofia", LocalDate.now())).isEmpty();
    }

    @Test
    void fetchDay_returnsEmpty_whenRestClientThrows() {
        mockServer.expect(requestTo(containsString("geocoding-api.open-meteo.com")))
                .andRespond(withServerError());

        assertThat(client.fetchDay("Sofia", LocalDate.now())).isEmpty();
    }

    @Test
    void fetchDay_returnsEmpty_whenHourlyBlockMissing() {
        mockServer.expect(requestTo(containsString("geocoding-api.open-meteo.com")))
                .andRespond(withSuccess(GEOCODE_OK, MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(containsString("api.open-meteo.com/v1/forecast")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.fetchDay("Sofia", LocalDate.now())).isEmpty();
    }

    @Test
    void fetchDay_returnsEmpty_whenTemperatureSeriesIsEmpty() {
        mockServer.expect(requestTo(containsString("geocoding-api.open-meteo.com")))
                .andRespond(withSuccess(GEOCODE_OK, MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(containsString("api.open-meteo.com/v1/forecast")))
                .andRespond(withSuccess("{\"hourly\":{\"temperature_2m\":[]}}", MediaType.APPLICATION_JSON));

        assertThat(client.fetchDay("Sofia", LocalDate.now())).isEmpty();
    }

    @Test
    void fetchDay_readsHourlySeries() {
        LocalDate date = LocalDate.now();
        mockServer.expect(requestTo(containsString("geocoding-api.open-meteo.com")))
                .andRespond(withSuccess(GEOCODE_OK, MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(containsString("api.open-meteo.com/v1/forecast")))
                .andRespond(withSuccess(
                        "{\"hourly\":{\"temperature_2m\":[12.5,13.5,null],\"precipitation_probability\":[10,20,30]}}",
                        MediaType.APPLICATION_JSON));

        Optional<WeatherDayDto> result = client.fetchDay("Sofia", date);

        assertThat(result).isPresent();
        assertThat(result.get().getDate()).isEqualTo(date);
        assertThat(result.get().getHourlyTempC()).containsExactly(12.5, 13.5, null);
        assertThat(result.get().getHourlyPrecipitationProbability()).containsExactly(10, 20, 30);
    }

    @Test
    void fetchDay_fillsNullPrecipitation_whenSeriesShorterThanTemperatures() {
        mockServer.expect(requestTo(containsString("geocoding-api.open-meteo.com")))
                .andRespond(withSuccess(GEOCODE_OK, MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(containsString("api.open-meteo.com/v1/forecast")))
                .andRespond(withSuccess(
                        "{\"hourly\":{\"temperature_2m\":[12.5,13.5],\"precipitation_probability\":[10]}}",
                        MediaType.APPLICATION_JSON));

        Optional<WeatherDayDto> result = client.fetchDay("Sofia", LocalDate.now());

        assertThat(result).isPresent();
        assertThat(result.get().getHourlyPrecipitationProbability()).containsExactly(10, null);
    }

    @Test
    void fetchDay_fillsNullPrecipitation_whenValueIsJsonNull() {
        mockServer.expect(requestTo(containsString("geocoding-api.open-meteo.com")))
                .andRespond(withSuccess(GEOCODE_OK, MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(containsString("api.open-meteo.com/v1/forecast")))
                .andRespond(withSuccess(
                        "{\"hourly\":{\"temperature_2m\":[12.5],\"precipitation_probability\":[null]}}",
                        MediaType.APPLICATION_JSON));

        Optional<WeatherDayDto> result = client.fetchDay("Sofia", LocalDate.now());

        assertThat(result).isPresent();
        assertThat(result.get().getHourlyPrecipitationProbability()).containsExactly((Integer) null);
    }

    @Test
    void fetchDay_fillsNullPrecipitation_whenSeriesMissing() {
        mockServer.expect(requestTo(containsString("geocoding-api.open-meteo.com")))
                .andRespond(withSuccess(GEOCODE_OK, MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(containsString("api.open-meteo.com/v1/forecast")))
                .andRespond(withSuccess(
                        "{\"hourly\":{\"temperature_2m\":[12.5]}}", MediaType.APPLICATION_JSON));

        Optional<WeatherDayDto> result = client.fetchDay("Sofia", LocalDate.now());

        assertThat(result).isPresent();
        assertThat(result.get().getHourlyPrecipitationProbability()).containsExactly((Integer) null);
    }
}
