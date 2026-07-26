package com.kickoffsim.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterDto {

    @NotBlank(message = "{validation.register.username.required}")
    @Size(min = 3, max = 50, message = "{validation.register.username.size}")
    @Pattern(regexp = "^\\S+$", message = "{validation.register.username.nospaces}")
    private String username;

    @NotBlank(message = "{validation.register.password.required}")
    @Size(min = 6, max = 100, message = "{validation.register.password.size}")
    private String password;

    @NotBlank(message = "{validation.register.confirm.required}")
    private String confirmPassword;
}
