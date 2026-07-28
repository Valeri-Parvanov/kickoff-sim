package com.kickoffsim.dto;

import lombok.Getter;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

@Getter
public class WeatherDayDto implements Serializable {

    private final LocalDate date;
    private final List<Double> hourlyTempC;
    private final List<Integer> hourlyPrecipitationProbability;

    public WeatherDayDto(LocalDate date, List<Double> hourlyTempC, List<Integer> hourlyPrecipitationProbability) {
        this.date = date;
        this.hourlyTempC = hourlyTempC;
        this.hourlyPrecipitationProbability = hourlyPrecipitationProbability;
    }
}
