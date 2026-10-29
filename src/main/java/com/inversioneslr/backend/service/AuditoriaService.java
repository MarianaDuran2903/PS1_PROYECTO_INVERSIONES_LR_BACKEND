package com.inversioneslr.backend.service;

import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.AccionAuditoria;

/**
 * Trazabilidad de acciones relevantes (RNF-AUD-001, RF-TRZ-*).
 */
public interface AuditoriaService {

    /**
     * Guarda el evento en evento_auditoria y lo escribe en el log JSON con resultado EXITOSO.
     */
    void registrarExito(Usuario usuario, AccionAuditoria accion, String tipoEntidad, String idEntidad);

    /**
     * Escribe en el log JSON un intento fallido. No se persiste porque la operación se revierte.
     */
    void registrarFallo(String usuario, AccionAuditoria accion, String tipoEntidad, String motivo);

    /**
     * Guarda en evento_auditoria un intento rechazado (inicio de sesión fallido, acceso denegado) y lo escribe
     * en el log JSON con resultado FALLIDO. Se guarda en su propia transacción para que quede registrado
     * aunque la operación que lo provocó se revierta.
     *
     * @param idUsuario usuario que hizo el intento, o nulo si no se identificó
     * @param usuario   correo del intento, solo para el log
     */
    void registrarIntentoFallido(Long idUsuario, String usuario, AccionAuditoria accion, String tipoEntidad,
                                 String idEntidad, String motivo);
}
