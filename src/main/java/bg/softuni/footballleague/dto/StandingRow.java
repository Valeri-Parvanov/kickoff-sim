package bg.softuni.footballleague.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class StandingRow {

    private UUID teamId;
    private String teamName;
    private String teamCity;
    private int played;
    private int wins;
    private int draws;
    private int losses;
    private int goalsFor;
    private int goalsAgainst;
    private boolean champion;

    public int getGoalDiff() {
        return goalsFor - goalsAgainst;
    }

    public int getPoints() {
        return wins * 3 + draws;
    }
}
