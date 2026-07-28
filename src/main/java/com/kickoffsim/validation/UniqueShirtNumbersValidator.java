package com.kickoffsim.validation;

import com.kickoffsim.dto.PlayerDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UniqueShirtNumbersValidator implements ConstraintValidator<UniqueShirtNumbers, List<PlayerDto>> {

    @Override
    public boolean isValid(List<PlayerDto> players, ConstraintValidatorContext context) {
        if (players == null) {
            return true;
        }
        Set<Integer> seen = new HashSet<>();
        for (PlayerDto player : players) {
            Integer shirtNumber = player.getShirtNumber();
            if (shirtNumber != null && !seen.add(shirtNumber)) {
                return false;
            }
        }
        return true;
    }
}
