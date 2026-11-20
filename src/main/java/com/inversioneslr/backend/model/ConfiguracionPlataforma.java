package com.inversioneslr.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Parámetros de la plataforma (tabla configuracion_plataforma, una sola fila): tarifa de servicio y descuento
 * por estadía larga. Son configurables en la base de datos.
 */
@Entity
@Table(name = "configuracion_plataforma")
public class ConfiguracionPlataforma {

    /** Identificador de la única fila de la tabla. */
    public static final short ID_UNICO = 1;

    @Id
    @Column(name = "id_configuracion")
    private Short idConfiguracion;

    @Column(name = "porcentaje_tarifa_servicio", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeTarifaServicio;

    @Column(name = "porcentaje_descuento", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeDescuento;

    @Column(name = "noches_minimas_descuento", nullable = false)
    private Integer nochesMinimasDescuento;

    protected ConfiguracionPlataforma() {
        // Requerido por JPA
    }

    /**
     * Descuento que corresponde a una estadía de esa cantidad de noches: el configurado si llega al mínimo, y
     * cero en caso contrario.
     */
    public BigDecimal porcentajeDescuentoPara(long noches) {
        return noches >= nochesMinimasDescuento ? porcentajeDescuento : BigDecimal.ZERO;
    }

    public BigDecimal getPorcentajeTarifaServicio() {
        return porcentajeTarifaServicio;
    }

    public BigDecimal getPorcentajeDescuento() {
        return porcentajeDescuento;
    }

    public Integer getNochesMinimasDescuento() {
        return nochesMinimasDescuento;
    }
}
