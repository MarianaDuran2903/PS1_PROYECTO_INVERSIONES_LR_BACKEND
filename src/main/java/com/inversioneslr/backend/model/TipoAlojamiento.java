package com.inversioneslr.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Catálogo de tipos de alojamiento: apartamento, casa, habitación... (tabla tipo_alojamiento).
 */
@Entity
@Table(name = "tipo_alojamiento")
public class TipoAlojamiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tipo")
    private Long idTipo;

    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    protected TipoAlojamiento() {
        // Requerido por JPA
    }

    public TipoAlojamiento(String nombre) {
        this.nombre = nombre;
    }

    public Long getIdTipo() {
        return idTipo;
    }

    public String getNombre() {
        return nombre;
    }
}
