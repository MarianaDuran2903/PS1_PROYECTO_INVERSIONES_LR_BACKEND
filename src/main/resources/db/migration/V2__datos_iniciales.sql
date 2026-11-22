/*
  Autor       : Brayan David Herrera Acero
  Fecha       : 2026-10-25
  Descripción : Datos iniciales de ejemplo para desarrollo: configuración de la plataforma, políticas
                de cancelación, ciudades, tipos de alojamiento y servicios. Los valores son de ejemplo
                y el equipo debe ajustarlos. Se puede ejecutar varias veces sin duplicar filas.
                Requiere haber ejecutado antes 01_marketplace_ddl_postgresql.sql.
  Cambios     :
    20261025- se ajustó el script para cargar los catálogos y la configuración inicial del MER v0.3
*/

-- Porcentajes configurables (valores de ejemplo).
INSERT INTO configuracion_plataforma (id_configuracion, porcentaje_tarifa_servicio, porcentaje_descuento, noches_minimas_descuento)
VALUES (1, 10.00, 10.00, 7)
ON CONFLICT (id_configuracion) DO NOTHING;

-- El enunciado exige al menos una política de cancelación (valores de ejemplo).
INSERT INTO politica_cancelacion (nombre, horas_anticipacion_min, porcentaje_devolucion) VALUES
    ('Flexible', 24, 100.00),
    ('Estricta', 168, 50.00)
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO ciudad (nombre) VALUES
    ('Bogotá'),
    ('Medellín'),
    ('Cartagena'),
    ('Cali')
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO tipo_alojamiento (nombre) VALUES
    ('Apartamento'),
    ('Casa'),
    ('Habitación'),
    ('Cabaña')
ON CONFLICT (nombre) DO NOTHING;

-- Servicios (amenities) del glosario del caso.
INSERT INTO servicio (nombre) VALUES
    ('Wi-Fi'),
    ('Parqueadero'),
    ('Cocina')
ON CONFLICT (nombre) DO NOTHING;
