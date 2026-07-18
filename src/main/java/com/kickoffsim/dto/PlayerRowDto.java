package bg.softuni.footballleague.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlayerRowDto {

    private String firstName;

    private String lastName;

    private Integer shirtNumber;

    public boolean isEmpty() {
        return (firstName == null || firstName.isBlank())
                && (lastName == null || lastName.isBlank());
    }
}
