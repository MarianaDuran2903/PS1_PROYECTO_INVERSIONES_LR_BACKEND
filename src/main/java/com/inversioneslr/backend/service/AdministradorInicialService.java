package com.inversioneslr.backend.service;

/**
 * Carga del Administrador como dato inicial: no se registra por formulario.
 */
public interface AdministradorInicialService {

    /**
     * Crea el Administrador con ADMIN_CORREO y ADMIN_CONTRASENA si todavía no existe ninguno.
     *
     * @return true si lo creó; false si ya existía uno, faltan las variables o el correo está en uso
     */
    boolean crearSiNoExiste();
}
