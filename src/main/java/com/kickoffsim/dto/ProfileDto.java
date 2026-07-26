package com.kickoffsim.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileDto {

    @Email(message = "{validation.profile.email.invalid}")
    @Size(max = 100, message = "{validation.profile.email.max}")
    private String email;
}
