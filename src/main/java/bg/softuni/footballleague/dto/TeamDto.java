package bg.softuni.footballleague.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class TeamDto {

    private UUID id;

    @NotBlank(message = "Team name is required")
    @Size(max = 100, message = "Team name must be at most 100 characters")
    @Pattern(regexp = "^\\S+$", message = "Team name must not contain spaces")
    private String name;

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City must be at most 100 characters")
    @Pattern(regexp = "^\\S+$", message = "City must not contain spaces")
    private String city;

    private UUID leagueId;

    private String leagueName;

    private long playerCount;
}
