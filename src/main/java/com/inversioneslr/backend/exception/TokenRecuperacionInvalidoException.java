package com.inversioneslr.backend.exception;

/**
 * El token de recuperación no existe, venció o ya se usó. El mensaje es el mismo en los tres casos para no
 * revelar cuál ocurrió. Se responde 400.
 */
public class TokenRecuperacionInvalidoException extends RuntimeException {

    public TokenRecuperacionInvalidoException() {
        super("El enlace de recuperación no es válido o ha expirado");
    }
}
