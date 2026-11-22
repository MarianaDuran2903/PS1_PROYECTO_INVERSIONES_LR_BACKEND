package com.inversioneslr.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Objeto de valor con los nombres y apellidos de un usuario. Se guarda embebido en las columnas
 * primer_nombre, segundo_nombre, primer_apellido y segundo_apellido de la tabla usuario.
 */
@Embeddable
public record NombreCompleto(

        @Column(name = "primer_nombre", nullable = false, length = 50)
        String primerNombre,

        @Column(name = "segundo_nombre", length = 50)
        String segundoNombre,

        @Column(name = "primer_apellido", nullable = false, length = 50)
        String primerApellido,

        @Column(name = "segundo_apellido", length = 50)
        String segundoApellido
) {

    /**
     * Crea el nombre sin espacios sobrantes y con los opcionales en nulo cuando no se escribieron.
     */
    public static NombreCompleto normalizado(String primerNombre, String segundoNombre,
                                             String primerApellido, String segundoApellido) {
        return new NombreCompleto(
                primerNombre.strip(),
                opcional(segundoNombre),
                primerApellido.strip(),
                opcional(segundoApellido));
    }

    private static String opcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }
}
