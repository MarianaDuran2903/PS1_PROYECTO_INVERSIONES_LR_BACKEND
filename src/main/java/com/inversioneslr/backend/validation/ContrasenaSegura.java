package com.inversioneslr.backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Contraseña segura: al menos una mayúscula, una minúscula, un número y un carácter especial
 * (cualquiera que no sea letra, número ni espacio), sin espacios. La longitud se valida aparte con @Size.
 * Debe coincidir con la regla del frontend.
 */
@Documented
@Constraint(validatedBy = ContrasenaSeguraValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ContrasenaSegura {

    String message() default "La contraseña debe tener al menos una mayúscula, una minúscula, "
            + "un número y un carácter especial, sin espacios";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
