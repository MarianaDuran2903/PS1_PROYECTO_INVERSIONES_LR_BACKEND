-- ============================================================================
-- V10: pago de la reserva con Stripe Checkout (modo de pruebas).
--
-- pago_simulado (V1) ya guarda el pago y el identificador de la sesión de Checkout (referencia_stripe). Aquí se
-- completa con lo que hace falta para cobrar y dejar evidencia:
--   * estado acotado: PENDIENTE (sesión creada), RECHAZADO (intento fallido; la sesión sigue abierta y el Huésped
--     puede reintentar), APROBADO y EXPIRADO (finales);
--   * la sesión abierta (su URL y su vencimiento), el payment intent y el motivo del rechazo, como evidencia;
--   * a lo sumo una sesión abierta por reserva, para que una reserva no se pague dos veces;
--   * evento_pago_procesado: los avisos (webhooks) de Stripe ya aplicados, porque Stripe reenvía los que no
--     confirmamos y un aviso repetido no debe repetir su efecto.
-- ============================================================================

ALTER TABLE pago_simulado
    ADD COLUMN moneda              VARCHAR(3)    NOT NULL DEFAULT 'COP',
    ADD COLUMN id_payment_intent   VARCHAR(255),
    ADD COLUMN codigo_rechazo      VARCHAR(100),
    ADD COLUMN url_pago            VARCHAR(2048),
    ADD COLUMN fecha_expiracion    TIMESTAMPTZ,
    ADD COLUMN fecha_actualizacion TIMESTAMPTZ   NOT NULL DEFAULT now();

-- Todo pago nace con una sesión de Checkout: sin ella no hay evidencia que rastrear.
ALTER TABLE pago_simulado ALTER COLUMN referencia_stripe SET NOT NULL;

ALTER TABLE pago_simulado ADD CONSTRAINT ck_pago_estado
    CHECK (estado IN ('PENDIENTE', 'APROBADO', 'RECHAZADO', 'EXPIRADO'));

-- Una reserva tiene como máximo una sesión de pago abierta (PENDIENTE o RECHAZADO): si el Huésped pide pagar de
-- nuevo, recibe la misma sesión en lugar de abrir otra que también podría pagar.
CREATE UNIQUE INDEX uq_pago_abierto_por_reserva ON pago_simulado (id_reserva)
    WHERE estado IN ('PENDIENTE', 'RECHAZADO');

CREATE TABLE evento_pago_procesado (
    id_evento  VARCHAR(255) NOT NULL,
    tipo       VARCHAR(100) NOT NULL,
    id_pago    BIGINT       NOT NULL,
    fecha_hora TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_evento_pago_procesado PRIMARY KEY (id_evento),
    CONSTRAINT fk_evento_pago FOREIGN KEY (id_pago) REFERENCES pago_simulado (id_pago) ON DELETE CASCADE
);

COMMENT ON COLUMN pago_simulado.estado IS 'PENDIENTE, APROBADO, RECHAZADO o EXPIRADO. PENDIENTE y RECHAZADO son sesiones abiertas: el Huésped aún puede pagar.';
COMMENT ON COLUMN pago_simulado.referencia_stripe IS 'Identificador de la sesión de Checkout (cs_test_...).';
COMMENT ON COLUMN pago_simulado.id_payment_intent IS 'Identificador del payment intent de Stripe (pi_...), evidencia del cobro o del rechazo.';
COMMENT ON COLUMN pago_simulado.codigo_rechazo IS 'Código de rechazo de Stripe (por ejemplo card_declined) o MONTO_NO_COINCIDE; nulo si no hubo rechazo.';
COMMENT ON COLUMN pago_simulado.fecha_expiracion IS 'Hasta cuándo la sesión de Checkout admite el pago.';
COMMENT ON TABLE evento_pago_procesado IS 'Avisos de Stripe ya aplicados: su identificador es único, así que un reenvío se reconoce y se ignora.';
