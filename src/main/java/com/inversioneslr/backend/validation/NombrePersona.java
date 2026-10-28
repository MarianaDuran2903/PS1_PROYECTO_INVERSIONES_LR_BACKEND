package com.inversioneslr.backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Nombre o apellido: letras (con tildes y ñ) separadas por un solo espacio, guion o apóstrofe.
 * Admite espacios al inicio o al final. Vacío o nulo es válido: la obligatoriedad se valida con @NotBlank.
 * Debe coincidir con la regla del frontend.
 */
@Documented
@Constraint(validatedBy = NombrePersonaValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface NombrePersona {

    String message() default "Solo se permiten letras, espacios, guion o apóstrofe";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
