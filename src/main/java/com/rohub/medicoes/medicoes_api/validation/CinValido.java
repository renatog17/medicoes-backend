package com.rohub.medicoes.medicoes_api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = CinValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface CinValido {
    String message() default "CIN deve conter 11 dígitos e ser um CPF válido";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
