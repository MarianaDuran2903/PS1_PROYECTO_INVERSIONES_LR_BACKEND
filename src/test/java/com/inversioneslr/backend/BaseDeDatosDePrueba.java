package com.inversioneslr.backend;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Limpieza de los datos que crean las pruebas de integración. Las pruebas que comparten contexto usan la misma
 * base de datos, así que cada una deja las tablas transaccionales vacías antes de empezar. Los catálogos de V2
 * (ciudades, tipos, políticas) no se tocan.
 */
public final class BaseDeDatosDePrueba {

    private BaseDeDatosDePrueba() {
    }

    public static void limpiar(JdbcTemplate jdbcTemplate) {
        // CASCADE vacía también las tablas que dependen de estas (imágenes, reservas, etc.).
        jdbcTemplate.execute("TRUNCATE TABLE evento_auditoria, publicacion, usuario RESTART IDENTITY CASCADE");
    }
}
