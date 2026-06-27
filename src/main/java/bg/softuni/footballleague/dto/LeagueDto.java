package bg.softuni.footballleague.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class LeagueDto {

    private UUID id;

    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 100)
    private String country;
}
