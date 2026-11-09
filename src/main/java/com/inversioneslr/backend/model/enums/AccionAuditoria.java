package com.inversioneslr.backend.model.enums;

/**
 * Acciones relevantes que quedan en evento_auditoria y en los logs JSON.
 */
public enum AccionAuditoria {
    REGISTRO_USUARIO,
    INICIO_SESION,
    ACCESO_DENEGADO,
    CREAR_PUBLICACION,
    EDITAR_PUBLICACION,
    ENVIAR_A_REVISION,
    INTERVENCION_ADMIN_PUBLICACION
}
