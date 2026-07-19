package com.kickoffsim.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class PlayerRowDto {

    private UUID id;

    private String firstName;

    private String lastName;

    private Integer shirtNumber;

    public boolean isEmpty() {
        return (firstName == null || firstName.isBlank())
                && (lastName == null || lastName.isBlank());
    }
}
