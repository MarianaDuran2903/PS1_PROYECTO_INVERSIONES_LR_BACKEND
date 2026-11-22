package com.inversioneslr.backend.model;

import com.inversioneslr.backend.model.enums.TipoCobro;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lo que costaría una estadía, desglosado. Es una consulta: no se guarda ni reserva nada.
 *
 * <p>El desglose de la estadía es el mismo que se guarda al reservar ({@link DesglosePrecio}), así que cotizar y
 * reservar dan el mismo valor. Los servicios adicionales se suman aparte: no reciben el descuento por estadía
 * larga ni pagan la tarifa de servicio, igual que la limpieza. El total es lo de la estadía más los servicios.
 *
 * @param noches                     noches de la estadía
 * @param precioNoche                precio de una noche, con el que se calculó la base
 * @param desglose                   base, descuento, limpieza, tarifa de servicio y total de la estadía sin servicios
 * @param serviciosAdicionales       cada servicio elegido con lo que cuesta en esta estadía, en el orden pedido
 * @param totalServiciosAdicionales  suma de los servicios elegidos; cero si no se eligió ninguno
 * @param total                      lo que pagaría el Huésped
 */
public record CotizacionEstancia(
        long noches,
        BigDecimal precioNoche,
        DesglosePrecio desglose,
        List<ConceptoAdicional> serviciosAdicionales,
        BigDecimal totalServiciosAdicionales,
        BigDecimal total
) {

    /**
     * Un servicio adicional dentro de la cotización.
     *
     * @param nombre     nombre del servicio
     * @param tipoCobro  si se cobra por noche o una sola vez
     * @param precio     precio unitario del servicio
     * @param valor      lo que cuesta en esta estadía
     */
    public record ConceptoAdicional(String nombre, TipoCobro tipoCobro, BigDecimal precio, BigDecimal valor) {
    }

    public static CotizacionEstancia calcular(BigDecimal precioNoche, BigDecimal tarifaLimpieza, long noches,
                                              BigDecimal porcentajeDescuento, BigDecimal porcentajeTarifaServicio,
                                              List<ServicioAdicional> servicios) {
        DesglosePrecio desglose = DesglosePrecio.calcular(precioNoche, tarifaLimpieza, noches, porcentajeDescuento,
                porcentajeTarifaServicio);
        List<ConceptoAdicional> conceptos = servicios.stream()
                .map(servicio -> new ConceptoAdicional(servicio.getNombre(), servicio.getTipoCobro(),
                        servicio.getPrecio(), servicio.costoPara(noches)))
                .toList();
        BigDecimal totalServicios = conceptos.stream()
                .map(ConceptoAdicional::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CotizacionEstancia(noches, precioNoche, desglose, conceptos, totalServicios,
                desglose.total().add(totalServicios));
    }
}
