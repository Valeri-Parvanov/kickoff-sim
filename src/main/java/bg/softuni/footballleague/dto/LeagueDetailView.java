package bg.softuni.footballleague.dto;

import bg.softuni.footballleague.model.LeagueFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class LeagueDetailView {

    private UUID id;
    private String name;
    private List<TeamDto> teams;
    private List<StandingRow> standings;
    private List<MatchDto> matches;
    private LeagueFormat format;
    private LocalDate scheduleStartDate;
    private LocalTime scheduleStartTime;
}
