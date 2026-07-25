package com.kickoffsim.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeactivateAccountDto {

    @NotBlank
    private String password;
}
