package com.inversioneslr.backend.service;

import com.inversioneslr.backend.dto.request.RestablecerContrasenaRequest;
import com.inversioneslr.backend.dto.request.SolicitudRecuperacionRequest;
import com.inversioneslr.backend.dto.response.MensajeResponse;

/**
 * Recuperación de contraseña simulada: no se envía un correo real, el enlace queda en el log del servidor.
 */
public interface RecuperacionContrasenaService {

    /**
     * Registra la solicitud si el correo pertenece a una cuenta activa. La respuesta es la misma exista o no
     * el correo, para no revelar qué cuentas están registradas.
     */
    MensajeResponse solicitar(SolicitudRecuperacionRequest request);

    /**
     * Establece la nueva contraseña con un token vigente y lo deja usado.
     *
     * @throws com.inversioneslr.backend.exception.TokenRecuperacionInvalidoException si el token no existe,
     *         venció o ya se usó
     */
    MensajeResponse restablecer(RestablecerContrasenaRequest request);
}
