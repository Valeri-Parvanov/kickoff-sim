package com.kickoffsim.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniqueShirtNumbersValidator.class)
public @interface UniqueShirtNumbers {

    String message() default "{validation.squad.shirt.duplicate}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
