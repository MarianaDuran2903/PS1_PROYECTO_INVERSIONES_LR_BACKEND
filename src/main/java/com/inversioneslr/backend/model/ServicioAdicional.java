package com.inversioneslr.backend.model;

import com.inversioneslr.backend.model.enums.TipoCobro;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Servicio con costo que una publicación ofrece además de la estadía (tabla servicio_adicional), como el desayuno
 * o el transporte desde el aeropuerto. Se identifica por la publicación y su nombre.
 */
@Entity
@Table(name = "servicio_adicional")
public class ServicioAdicional {

    @EmbeddedId
    private ServicioAdicionalId id;

    @Column(name = "precio", nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_cobro", nullable = false, length = 20)
    private TipoCobro tipoCobro;

    protected ServicioAdicional() {
        // Requerido por JPA
    }

    public ServicioAdicional(Long idPublicacion, String nombre, BigDecimal precio, TipoCobro tipoCobro) {
        this.id = new ServicioAdicionalId(idPublicacion, nombre);
        this.precio = precio;
        this.tipoCobro = tipoCobro;
    }

    /**
     * Lo que cuesta este servicio en una estadía de esa cantidad de noches.
     */
    public BigDecimal costoPara(long noches) {
        return tipoCobro.costoPara(precio, noches);
    }

    public Long getIdPublicacion() {
        return id.idPublicacion();
    }

    public String getNombre() {
        return id.nombre();
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public TipoCobro getTipoCobro() {
        return tipoCobro;
    }
}
