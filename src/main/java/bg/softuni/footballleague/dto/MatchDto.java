package bg.softuni.footballleague.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class MatchDto {

    private UUID id;

    @NotNull(message = "Please select a home team")
    private UUID homeTeamId;

    @NotNull(message = "Please select an away team")
    private UUID awayTeamId;

    @PositiveOrZero(message = "Home score cannot be negative")
    private Integer homeScore;

    @PositiveOrZero(message = "Away score cannot be negative")
    private Integer awayScore;

    @NotNull(message = "Please select a date and time")
    @PastOrPresent(message = "Match date/time cannot be in the future")
    private LocalDateTime playedAt;

    @JsonIgnore
    @AssertTrue(message = "Matches must be scheduled between 08:00 and 23:30, on the hour or half hour")
    public boolean isPlayedAtTimeValid() {
        if (playedAt == null) return true;
        LocalTime t = playedAt.toLocalTime();
        int minute = t.getMinute();
        return !t.isBefore(LocalTime.of(8, 0))
                && !t.isAfter(LocalTime.of(23, 30))
                && (minute == 0 || minute == 30);
    }

    private Integer roundNumber;

    private String leagueName;

    private UUID leagueId;

    private String homeTeamName;

    private String homeTeamCity;

    private String awayTeamName;

    private String awayTeamCity;

    private Integer homeHalfScore;

    private Integer awayHalfScore;

    private List<GoalDto> goalTimeline = new ArrayList<>();

    public String getPlayedAtUtcIso() {
        if (playedAt == null) return "";
        return playedAt.atZone(ZoneId.of("Europe/Sofia")).toInstant().toString();
    }
}
