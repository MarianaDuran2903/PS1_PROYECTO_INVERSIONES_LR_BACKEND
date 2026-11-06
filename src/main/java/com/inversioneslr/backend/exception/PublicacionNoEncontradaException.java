package com.inversioneslr.backend.exception;

/**
 * La publicación pedida no existe. Se responde 404.
 */
public class PublicacionNoEncontradaException extends RuntimeException {

    public PublicacionNoEncontradaException() {
        super("La publicación no existe");
    }
}
