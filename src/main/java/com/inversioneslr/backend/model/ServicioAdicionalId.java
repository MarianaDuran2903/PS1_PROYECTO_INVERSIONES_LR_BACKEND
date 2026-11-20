package com.inversioneslr.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;

/**
 * Identificador de un servicio adicional: pertenece a una publicación y se distingue por su nombre dentro de ella.
 */
@Embeddable
public record ServicioAdicionalId(
        @Column(name = "id_publicacion", nullable = false, updatable = false)
        Long idPublicacion,

        @Column(name = "nombre", nullable = false, updatable = false, length = 100)
        String nombre
) implements Serializable {
}
