package com.kickoffsim.service.impl;

import com.kickoffsim.dto.WeatherForecastDto;
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

class WeatherServiceImplTest {

    private MockRestServiceServer mockServer;
    private WeatherServiceImpl weatherService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        weatherService = new WeatherServiceImpl(builder);
    }

    @Test
    void forecastFor_returnsEmpty_whenCityIsNull() {
        assertThat(weatherService.forecastFor(null, LocalDate.now().plusDays(1))).isEmpty();
    }

    @Test
    void forecastFor_returnsEmpty_whenDateIsNull() {
        assertThat(weatherService.forecastFor("Sofia", null)).isEmpty();
    }

    @Test
    void forecastFor_returnsEmpty_whenDateInPast() {
        assertThat(weatherService.forecastFor("Sofia", LocalDate.now().minusDays(1))).isEmpty();
    }

    @Test
    void forecastFor_returnsEmpty_whenDateTooFarInFuture() {
        assertThat(weatherService.forecastFor("Sofia", LocalDate.now().plusDays(30))).isEmpty();
    }

    @Test
    void forecastFor_returnsEmpty_whenGeocodingHasNoResults() {
        mockServer.expect(requestTo(containsString("geocoding-api.open-meteo.com")))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));

        assertThat(weatherService.forecastFor("Nowhereville", LocalDate.now().plusDays(1))).isEmpty();
    }

    @Test
    void forecastFor_returnsEmpty_whenGeocodingResultsMissing() {
        mockServer.expect(requestTo(containsString("geocoding-api.open-meteo.com")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(weatherService.forecastFor("Nowhereville", LocalDate.now().plusDays(1))).isEmpty();
    }

    @Test
    void forecastFor_returnsEmpty_whenForecastHasNoDailyData() {
        mockServer.expect(requestTo(containsString("geocoding-api.open-meteo.com")))
                .andRespond(withSuccess("{\"results\":[{\"latitude\":42.5,\"longitude\":23.3}]}", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(containsString("api.open-meteo.com/v1/forecast")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(weatherService.forecastFor("Sofia", LocalDate.now().plusDays(1))).isEmpty();
    }

    @Test
    void forecastFor_returnsEmpty_whenRestClientThrows() {
        mockServer.expect(requestTo(containsString("geocoding-api.open-meteo.com")))
                .andRespond(withServerError());

        assertThat(weatherService.forecastFor("Sofia", LocalDate.now().plusDays(1))).isEmpty();
    }

    @Test
    void forecastFor_returnsForecastWithoutPrecipitation_whenFieldMissing() {
        mockServer.expect(requestTo(containsString("geocoding-api.open-meteo.com")))
                .andRespond(withSuccess("{\"results\":[{\"latitude\":42.5,\"longitude\":23.3}]}", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(containsString("api.open-meteo.com/v1/forecast")))
                .andRespond(withSuccess(
                        "{\"daily\":{\"temperature_2m_max\":[25.4],\"temperature_2m_min\":[14.1]}}",
                        MediaType.APPLICATION_JSON));

        Optional<WeatherForecastDto> result = weatherService.forecastFor("Sofia", LocalDate.now().plusDays(1));

        assertThat(result).isPresent();
        assertThat(result.get().getMaxTempC()).isEqualTo(25.4);
        assertThat(result.get().getMinTempC()).isEqualTo(14.1);
        assertThat(result.get().getPrecipitationProbability()).isNull();
    }

    @Test
    void forecastFor_returnsEmpty_whenMaxTempsArrayIsEmpty() {
        mockServer.expect(requestTo(containsString("geocoding-api.open-meteo.com")))
                .andRespond(withSuccess("{\"results\":[{\"latitude\":42.5,\"longitude\":23.3}]}", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(containsString("api.open-meteo.com/v1/forecast")))
                .andRespond(withSuccess(
                        "{\"daily\":{\"temperature_2m_max\":[],\"temperature_2m_min\":[]}}",
                        MediaType.APPLICATION_JSON));

        assertThat(weatherService.forecastFor("Sofia", LocalDate.now().plusDays(1))).isEmpty();
    }

    @Test
    void forecastFor_returnsForecastWithoutPrecipitation_whenPrecipitationArrayIsEmpty() {
        mockServer.expect(requestTo(containsString("geocoding-api.open-meteo.com")))
                .andRespond(withSuccess("{\"results\":[{\"latitude\":42.5,\"longitude\":23.3}]}", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(containsString("api.open-meteo.com/v1/forecast")))
                .andRespond(withSuccess(
                        "{\"daily\":{\"temperature_2m_max\":[25.4],\"temperature_2m_min\":[14.1],\"precipitation_probability_max\":[]}}",
                        MediaType.APPLICATION_JSON));

        Optional<WeatherForecastDto> result = weatherService.forecastFor("Sofia", LocalDate.now().plusDays(1));

        assertThat(result).isPresent();
        assertThat(result.get().getPrecipitationProbability()).isNull();
    }

    @Test
    void forecastFor_returnsForecast_onHappyPath() {
        LocalDate date = LocalDate.now().plusDays(1);
        mockServer.expect(requestTo(containsString("geocoding-api.open-meteo.com")))
                .andRespond(withSuccess("{\"results\":[{\"latitude\":42.5,\"longitude\":23.3}]}", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(containsString("api.open-meteo.com/v1/forecast")))
                .andRespond(withSuccess(
                        "{\"daily\":{\"temperature_2m_max\":[25.4],\"temperature_2m_min\":[14.1],\"precipitation_probability_max\":[30]}}",
                        MediaType.APPLICATION_JSON));

        Optional<WeatherForecastDto> result = weatherService.forecastFor("Sofia", date);

        assertThat(result).isPresent();
        assertThat(result.get().getDate()).isEqualTo(date);
        assertThat(result.get().getMaxTempC()).isEqualTo(25.4);
        assertThat(result.get().getMinTempC()).isEqualTo(14.1);
        assertThat(result.get().getPrecipitationProbability()).isEqualTo(30);
    }
}
