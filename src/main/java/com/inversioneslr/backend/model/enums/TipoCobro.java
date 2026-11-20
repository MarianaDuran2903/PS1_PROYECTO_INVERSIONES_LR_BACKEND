package com.inversioneslr.backend.model.enums;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Cómo se cobra un servicio adicional de una publicación.
 */
public enum TipoCobro {

    /** El precio se cobra por cada noche de la estadía. */
    POR_NOCHE,

    /** El precio se cobra una sola vez, sin importar cuántas noches dure la estadía. */
    POR_ESTANCIA;

    private static final int ESCALA = 2;

    /**
     * Lo que cuesta el servicio en una estadía de esa cantidad de noches, en pesos con dos decimales.
     */
    public BigDecimal costoPara(BigDecimal precio, long noches) {
        BigDecimal costo = this == POR_NOCHE ? precio.multiply(BigDecimal.valueOf(noches)) : precio;
        return costo.setScale(ESCALA, RoundingMode.HALF_UP);
    }
}
