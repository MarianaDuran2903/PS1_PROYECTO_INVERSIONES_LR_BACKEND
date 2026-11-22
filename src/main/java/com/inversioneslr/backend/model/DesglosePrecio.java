package com.inversioneslr.backend.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Precio de una reserva desglosado. Se guarda en la reserva al crearla para que un cambio posterior de la tarifa
 * o del descuento no altere lo que el Huésped ya vio.
 *
 * <p>Cálculo: la base es el precio por noche por las noches; el descuento se aplica sobre la base cuando la
 * estadía llega al mínimo de noches; la tarifa de servicio se calcula sobre la base menos el descuento (la
 * limpieza no la incluye); el total suma lo que queda de la base, la limpieza y la tarifa de servicio.
 */
public record DesglosePrecio(
        BigDecimal precioBase,
        BigDecimal porcentajeDescuento,
        BigDecimal valorDescuento,
        BigDecimal tarifaLimpieza,
        BigDecimal porcentajeTarifaServicio,
        BigDecimal tarifaServicio,
        BigDecimal total
) {

    private static final int ESCALA = 2;

    public static DesglosePrecio calcular(BigDecimal precioNoche, BigDecimal tarifaLimpieza, long noches,
                                          BigDecimal porcentajeDescuento, BigDecimal porcentajeTarifaServicio) {
        BigDecimal precioBase = redondear(precioNoche.multiply(BigDecimal.valueOf(noches)));
        BigDecimal valorDescuento = porcentaje(precioBase, porcentajeDescuento);
        BigDecimal subtotal = precioBase.subtract(valorDescuento);
        BigDecimal tarifaServicio = porcentaje(subtotal, porcentajeTarifaServicio);
        BigDecimal limpieza = redondear(tarifaLimpieza);
        BigDecimal total = subtotal.add(limpieza).add(tarifaServicio);
        return new DesglosePrecio(precioBase, porcentajeDescuento, valorDescuento, limpieza,
                porcentajeTarifaServicio, tarifaServicio, total);
    }

    /**
     * El porcentaje se calcula exacto y se redondea una sola vez, para no acumular dos redondeos.
     */
    private static BigDecimal porcentaje(BigDecimal base, BigDecimal porcentaje) {
        return redondear(base.multiply(porcentaje).movePointLeft(2));
    }

    private static BigDecimal redondear(BigDecimal valor) {
        return valor.setScale(ESCALA, RoundingMode.HALF_UP);
    }
}
