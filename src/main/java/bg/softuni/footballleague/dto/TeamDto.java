package bg.softuni.footballleague.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class TeamDto {

    private UUID id;

    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 100)
    private String city;

    @NotNull
    private UUID leagueId;
}
