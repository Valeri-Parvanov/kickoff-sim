package bg.softuni.footballleague.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileDto {

    @Email(message = "Enter a valid email address")
    @Size(max = 100)
    private String email;
}
