package com.inversioneslr.backend.model.enums;

/**
 * Acciones relevantes que quedan en evento_auditoria y en los logs JSON.
 */
public enum AccionAuditoria {
    REGISTRO_USUARIO,
    INICIO_SESION,
    ACCESO_DENEGADO,
    ACTUALIZACION_PERFIL,
    DESACTIVACION_CUENTA,
    ACTIVACION_CUENTA,
    CREAR_PUBLICACION,
    EDITAR_PUBLICACION,
    INTERVENCION_ADMIN_PUBLICACION,
}
