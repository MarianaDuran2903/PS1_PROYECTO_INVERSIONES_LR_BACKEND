-- ============================================================================
-- V8: la reserva nace PENDIENTE_PAGO y ocupa las fechas hasta que se paga o vence.
--
-- Con el pago en pasarela la reserva debe existir antes de cobrar, y mientras el Huésped paga las fechas
-- no pueden ofrecerse a otro. Cambios sobre V1:
--   * estado PENDIENTE_PAGO, con su fecha de vencimiento (obligatoria mientras está pendiente);
--   * PENDIENTE_PAGO ocupa fechas igual que CONFIRMADA y REVISION_MANUAL (restricción ex_reserva_sin_solape);
--   * numero_huespedes: cuántas personas se alojan (el Anfitrión y la comparación de reintentos lo necesitan).
-- ============================================================================

ALTER TABLE reserva DROP CONSTRAINT ck_reserva_estado;
ALTER TABLE reserva ADD CONSTRAINT ck_reserva_estado
    CHECK (estado IN ('PENDIENTE_PAGO', 'CONFIRMADA', 'CANCELADA', 'REVISION_MANUAL'));

-- Nula en las reservas que no esperan pago. Se conserva al confirmar o cancelar como historial.
ALTER TABLE reserva ADD COLUMN fecha_expiracion TIMESTAMPTZ;
ALTER TABLE reserva ADD CONSTRAINT ck_reserva_expiracion
    CHECK (estado <> 'PENDIENTE_PAGO' OR fecha_expiracion IS NOT NULL);

-- El valor por defecto cubre las filas anteriores a esta migración.
ALTER TABLE reserva ADD COLUMN numero_huespedes INTEGER NOT NULL DEFAULT 1;
ALTER TABLE reserva ADD CONSTRAINT ck_reserva_huespedes CHECK (numero_huespedes >= 1);

-- Evita la doble reserva por concurrencia (RF-RES-002): la fecha de salida no cuenta como noche ocupada.
-- CANCELADA no ocupa fechas; PENDIENTE_PAGO sí, para que dos Huéspedes no paguen las mismas noches.
ALTER TABLE reserva DROP CONSTRAINT ex_reserva_sin_solape;
ALTER TABLE reserva ADD CONSTRAINT ex_reserva_sin_solape EXCLUDE USING gist (
    id_publicacion WITH =,
    daterange(fecha_llegada, fecha_salida, '[)') WITH &&
) WHERE (estado IN ('PENDIENTE_PAGO', 'CONFIRMADA', 'REVISION_MANUAL'));

-- La tarea de vencimiento busca solo las reservas que siguen esperando pago.
CREATE INDEX ix_reserva_pendiente_vencimiento ON reserva (fecha_expiracion) WHERE estado = 'PENDIENTE_PAGO';

COMMENT ON COLUMN reserva.estado IS 'PENDIENTE_PAGO, CONFIRMADA, CANCELADA o REVISION_MANUAL. "Completada" se deriva: CONFIRMADA con fecha_salida ya pasada.';
COMMENT ON COLUMN reserva.fecha_expiracion IS 'Hasta cuándo espera el pago una reserva PENDIENTE_PAGO; al vencer pasa a CANCELADA y libera las fechas.';
COMMENT ON COLUMN reserva.numero_huespedes IS 'Cantidad de huéspedes de la reserva; no supera la capacidad de la publicación al crearla.';
