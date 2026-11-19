package com.inversioneslr.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Catálogo de ciudades (tabla ciudad).
 */
@Entity
@Table(name = "ciudad")
public class Ciudad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ciudad")
    private Long idCiudad;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    protected Ciudad() {
        // Requerido por JPA
    }

    public Ciudad(String nombre) {
        this.nombre = nombre;
    }

    public Long getIdCiudad() {
        return idCiudad;
    }

    public String getNombre() {
        return nombre;
    }
}
