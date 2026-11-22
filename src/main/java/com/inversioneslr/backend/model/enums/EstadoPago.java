package com.inversioneslr.backend.model.enums;

import java.util.List;

/**
 * Estado de un pago (columna estado de la tabla pago_simulado).
 */
public enum EstadoPago {
    /** Sesión de pago creada; el Huésped aún no pagó. */
    PENDIENTE,
    /** Cobro aprobado. Es final: ningún aviso posterior lo cambia. */
    APROBADO,
    /** Un intento falló. La sesión sigue abierta: el Huésped puede reintentar con otra tarjeta. */
    RECHAZADO,
    /** La sesión venció sin pago. Es final: pagar de nuevo exige abrir otra sesión. */
    EXPIRADO;

    /** Estados de una sesión que todavía admite el pago. */
    public static final List<EstadoPago> ABIERTOS = List.of(PENDIENTE, RECHAZADO);

    public boolean estaAbierto() {
        return ABIERTOS.contains(this);
    }
}
