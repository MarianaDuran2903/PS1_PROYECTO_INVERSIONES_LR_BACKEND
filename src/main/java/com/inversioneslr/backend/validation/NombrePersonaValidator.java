package com.inversioneslr.backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Recorre el texto una sola vez en lugar de usar una expresión regular con un grupo repetido,
 * que en Java puede desbordar la pila con entradas grandes.
 */
public class NombrePersonaValidator implements ConstraintValidator<NombrePersona, String> {

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext context) {
        if (valor == null || valor.isBlank()) {
            return true; // La obligatoriedad la valida @NotBlank
        }
        int[] caracteres = valor.strip().codePoints().toArray();
        if (!Character.isLetter(caracteres[0]) || !Character.isLetter(caracteres[caracteres.length - 1])) {
            return false;
        }
        boolean anteriorEsSeparador = false;
        for (int c : caracteres) {
            if (Character.isLetter(c)) {
                anteriorEsSeparador = false;
            } else if (esSeparador(c) && !anteriorEsSeparador) {
                anteriorEsSeparador = true;
            } else {
                return false; // Carácter no permitido o dos separadores seguidos
            }
        }
        return true;
    }

    private static boolean esSeparador(int c) {
        return c == ' ' || c == '-' || c == '\'';
    }
}
