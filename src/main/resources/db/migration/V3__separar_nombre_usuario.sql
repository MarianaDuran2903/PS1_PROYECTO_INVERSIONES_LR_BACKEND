/*
  Autor       : Brayan David Herrera Acero
  Fecha       : 2026-10-28
  Descripción : Separa usuario.nombre en primer_nombre, segundo_nombre, primer_apellido y
                segundo_apellido, según los datos que pide el formulario de registro (HU-001, HU-002).
                Primer nombre y primer apellido son obligatorios; los segundos son opcionales.
  Cambios     :
    20261028- se creó el script para separar el nombre del usuario (MAR-132)
*/

ALTER TABLE usuario
    ADD COLUMN primer_nombre    VARCHAR(50),
    ADD COLUMN segundo_nombre   VARCHAR(50),
    ADD COLUMN primer_apellido  VARCHAR(50),
    ADD COLUMN segundo_apellido VARCHAR(50);

-- Datos existentes: la primera palabra pasa a primer_nombre y el resto a primer_apellido.
-- Si el nombre tenía una sola palabra, se repite en primer_apellido para cumplir NOT NULL.
UPDATE usuario
   SET primer_nombre   = left(split_part(btrim(nombre), ' ', 1), 50),
       primer_apellido = left(COALESCE(
                             NULLIF(btrim(substr(btrim(nombre), length(split_part(btrim(nombre), ' ', 1)) + 1)), ''),
                             split_part(btrim(nombre), ' ', 1)), 50);

ALTER TABLE usuario
    ALTER COLUMN primer_nombre   SET NOT NULL,
    ALTER COLUMN primer_apellido SET NOT NULL,
    ADD CONSTRAINT ck_usuario_primer_nombre    CHECK (btrim(primer_nombre) <> ''),
    ADD CONSTRAINT ck_usuario_primer_apellido  CHECK (btrim(primer_apellido) <> ''),
    ADD CONSTRAINT ck_usuario_segundo_nombre   CHECK (segundo_nombre IS NULL OR btrim(segundo_nombre) <> ''),
    ADD CONSTRAINT ck_usuario_segundo_apellido CHECK (segundo_apellido IS NULL OR btrim(segundo_apellido) <> ''),
    DROP COLUMN nombre;

COMMENT ON COLUMN usuario.primer_nombre IS 'Obligatorio. Solo letras, espacios, guion o apóstrofe (validado en el backend).';
COMMENT ON COLUMN usuario.segundo_nombre IS 'Opcional. Nulo cuando no se registra.';
COMMENT ON COLUMN usuario.primer_apellido IS 'Obligatorio. Solo letras, espacios, guion o apóstrofe (validado en el backend).';
COMMENT ON COLUMN usuario.segundo_apellido IS 'Opcional. Nulo cuando no se registra.';
