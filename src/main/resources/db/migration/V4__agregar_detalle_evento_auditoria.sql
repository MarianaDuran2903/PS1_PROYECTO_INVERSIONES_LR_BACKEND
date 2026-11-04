-- Información adicional de un evento de auditoría, como el motivo de una intervención administrativa.
-- Es nula para los eventos que no la necesitan, incluidos los registrados antes de esta migración.
ALTER TABLE evento_auditoria ADD COLUMN detalle TEXT;

COMMENT ON COLUMN evento_auditoria.detalle IS 'Información adicional del evento, como el motivo de una intervención administrativa';
