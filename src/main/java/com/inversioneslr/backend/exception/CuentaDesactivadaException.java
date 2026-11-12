package com.inversioneslr.backend.exception;

/**
 * Las credenciales son válidas, pero la cuenta está desactivada.
 * Permite al frontend ofrecer la reactivación mediante /auth/reactivar.
 */
public class CuentaDesactivadaException extends RuntimeException {
    public CuentaDesactivadaException(String message) {
        super(message);
    }
}
