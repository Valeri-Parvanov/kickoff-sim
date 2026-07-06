package bg.softuni.footballleague.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class LeagueDto {

    private UUID id;

    @NotBlank
    @Size(max = 100)
    private String name;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate scheduleStartDate;

    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime scheduleStartTime;

    private List<UUID> teamIds = new ArrayList<>();
}
