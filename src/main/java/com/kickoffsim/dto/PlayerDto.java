package com.kickoffsim.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class PlayerDto {

    private UUID id;

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must be at most 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must be at most 100 characters")
    private String lastName;

    @NotNull(message = "Shirt number is required")
    @Positive(message = "Shirt number must be greater than 0")
    @Max(value = 99, message = "Shirt number must be at most 99")
    private Integer shirtNumber;

    @NotNull(message = "Please select a team")
    private UUID teamId;

    private String teamName;

    private int goals;

    private int assists;
}
