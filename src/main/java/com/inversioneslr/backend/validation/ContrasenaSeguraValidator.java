package com.inversioneslr.backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

/**
 * Recorre la contraseña una sola vez en lugar de usar una expresión regular con varias búsquedas
 * anticipadas, que es más difícil de leer y propensa a retroceso excesivo.
 */
public class ContrasenaSeguraValidator implements ConstraintValidator<ContrasenaSegura, String> {

    /** BCrypt solo usa los primeros 72 bytes: más allá, dos contraseñas distintas darían el mismo hash. */
    static final int MAX_BYTES_BCRYPT = 72;
    static final String MENSAJE_DEMASIADO_LARGA =
            "La contraseña es demasiado larga: reduzca los caracteres con tilde, ñ o símbolos especiales";

    @Override
    public boolean isValid(String contrasena, ConstraintValidatorContext context) {
        if (contrasena == null) {
            return true; // La obligatoriedad la valida @NotBlank
        }
        // Con 64 caracteres solo se supera con muchos caracteres de varios bytes (tildes, ñ, emojis).
        if (contrasena.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES_BCRYPT) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(MENSAJE_DEMASIADO_LARGA).addConstraintViolation();
            return false;
        }
        boolean mayuscula = false;
        boolean minuscula = false;
        boolean numero = false;
        boolean especial = false;

        for (int c : contrasena.codePoints().toArray()) {
            if (Character.isWhitespace(c)) {
                return false;
            }
            if (Character.isUpperCase(c)) {
                mayuscula = true;
            } else if (Character.isLowerCase(c)) {
                minuscula = true;
            } else if (Character.isDigit(c)) {
                numero = true;
            } else if (!Character.isLetter(c)) {
                especial = true;
            }
        }
        return mayuscula && minuscula && numero && especial;
    }
}
