-- ============================================================================
-- V9: tipos de cobro de los servicios adicionales.
--
-- V1 dejó tipo_cobro como texto libre, con los valores "por definir". La cotización necesita saber cómo se cobra
-- cada servicio, así que se fijan dos:
--   * POR_NOCHE: el precio se cobra por cada noche de la estadía;
--   * POR_ESTANCIA: el precio se cobra una sola vez.
-- ============================================================================

ALTER TABLE servicio_adicional ADD CONSTRAINT ck_servicio_adicional_tipo_cobro
    CHECK (tipo_cobro IN ('POR_NOCHE', 'POR_ESTANCIA'));

COMMENT ON COLUMN servicio_adicional.tipo_cobro IS 'POR_NOCHE: precio x noches de la estadía. POR_ESTANCIA: el precio una sola vez.';
