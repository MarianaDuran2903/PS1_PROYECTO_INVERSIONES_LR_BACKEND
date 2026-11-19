package com.inversioneslr.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "servicio")
public class Servicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_servicio")
    private Long idServicio;

    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    protected Servicio() {
        // Requerido por JPA
    }

    public Servicio(String nombre) {
        this.nombre = nombre;
    }

    public Long getIdServicio() {
        return idServicio;
    }

    public String getNombre() {
        return nombre;
    }
}