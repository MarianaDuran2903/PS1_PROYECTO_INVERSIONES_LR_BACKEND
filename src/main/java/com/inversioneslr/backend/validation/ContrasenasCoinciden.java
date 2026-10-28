package com.inversioneslr.backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Valida a nivel de clase que la confirmación sea igual a la contraseña. El error se reporta en el
 * campo confirmarContrasena para que el frontend lo muestre junto a ese campo.
 */
@Documented
@Constraint(validatedBy = ContrasenasCoincidenValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ContrasenasCoinciden {

    String message() default "La confirmación no coincide con la contraseña";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
