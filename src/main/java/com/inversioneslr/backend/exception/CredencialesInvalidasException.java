package com.inversioneslr.backend.exception;

/**
 * Inicio de sesión rechazado. El mensaje es siempre el mismo (correo inexistente, contraseña incorrecta,
 * cuenta desactivada o restringida) para no revelar qué correos existen.
 */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        super("Correo o contraseña incorrectos");
    }
}
