package com.inversioneslr.backend.model;

import java.math.BigDecimal;

/**
 * Información editable de una publicación, ya normalizada: sin espacios sobrantes y con el barrio en nulo
 * cuando no se escribió.
 */
public record DatosPublicacion(String titulo, String descripcion, String direccionTextual, String barrioZona,
                               Integer capacidad, BigDecimal precioNoche) {

    public static DatosPublicacion normalizados(String titulo, String descripcion, String direccionTextual,
                                                String barrioZona, Integer capacidad, BigDecimal precioNoche) {
        return new DatosPublicacion(titulo.strip(), descripcion.strip(), direccionTextual.strip(),
                barrioZona == null || barrioZona.isBlank() ? null : barrioZona.strip(), capacidad, precioNoche);
    }
}
