package com.inversioneslr.backend.exception;

public class CorreoYaRegistradoException extends RuntimeException {

    public CorreoYaRegistradoException() {
        super("El correo ya está registrado");
    }
}
