package com.inversioneslr.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Política de cancelación (tabla politica_cancelacion). Los valores son configurables en la base de datos.
 */
@Entity
@Table(name = "politica_cancelacion")
public class PoliticaCancelacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_politica")
    private Long idPolitica;

    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    @Column(name = "horas_anticipacion_min", nullable = false)
    private Integer horasAnticipacionMin;

    @Column(name = "porcentaje_devolucion", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeDevolucion;

    protected PoliticaCancelacion() {
        // Requerido por JPA
    }

    public Long getIdPolitica() {
        return idPolitica;
    }

    public String getNombre() {
        return nombre;
    }

    public Integer getHorasAnticipacionMin() {
        return horasAnticipacionMin;
    }

    public BigDecimal getPorcentajeDevolucion() {
        return porcentajeDevolucion;
    }
}
