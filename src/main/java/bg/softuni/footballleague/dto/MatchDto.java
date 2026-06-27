package bg.softuni.footballleague.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class MatchDto {

    private UUID id;

    @NotNull
    private UUID homeTeamId;

    @NotNull
    private UUID awayTeamId;

    @PositiveOrZero
    private Integer homeScore;

    @PositiveOrZero
    private Integer awayScore;

    @NotNull
    private LocalDateTime playedAt;
}
