package com.kickoffsim.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
public class ScheduleForm {

    @NotNull(message = "{validation.schedule.date.required}")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @NotNull(message = "{validation.schedule.time.required}")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime startTime = LocalTime.of(11, 0);
}
