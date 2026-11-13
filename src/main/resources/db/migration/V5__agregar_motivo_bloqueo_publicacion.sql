-- Motivo con el que el Administrador bloqueó la publicación, para que el Administrador lo lea al decidir si
-- la reactiva y el Anfitrión sepa por qué. Solo tiene valor mientras la publicación está BLOQUEADA; al
-- reactivarla se limpia y el historial queda en evento_auditoria. Es nulo en las publicaciones existentes.
ALTER TABLE publicacion ADD COLUMN motivo_bloqueo TEXT;

ALTER TABLE publicacion ADD CONSTRAINT ck_publicacion_motivo_bloqueo
    CHECK (motivo_bloqueo IS NULL OR estado = 'BLOQUEADA');

COMMENT ON COLUMN publicacion.motivo_bloqueo IS 'Motivo del bloqueo vigente; nulo si la publicación no está BLOQUEADA';
