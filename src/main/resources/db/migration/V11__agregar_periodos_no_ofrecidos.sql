-- HU-021 permite conservar los periodos cerrados en el calendario sin ofrecerlos para reserva.
ALTER TABLE periodo_disponibilidad
    DROP CONSTRAINT ck_periodo_tipo;

ALTER TABLE periodo_disponibilidad
    ALTER COLUMN tipo TYPE VARCHAR(11);

ALTER TABLE periodo_disponibilidad
    ADD CONSTRAINT ck_periodo_tipo
        CHECK (tipo IN ('DISPONIBLE', 'RESERVADO', 'BLOQUEADO', 'NO_OFRECIDO'));
