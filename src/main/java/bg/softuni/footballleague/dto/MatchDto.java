package bg.softuni.footballleague.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class MatchDto {

    private Long id;

    @NotNull
    private Long homeTeamId;

    @NotNull
    private Long awayTeamId;

    @PositiveOrZero
    private Integer homeScore;

    @PositiveOrZero
    private Integer awayScore;

    @NotNull
    private LocalDateTime playedAt;
}
