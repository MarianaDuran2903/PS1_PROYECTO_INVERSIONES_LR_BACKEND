package com.inversioneslr.backend.service;

import com.inversioneslr.backend.dto.request.RegistroHuespedRequest;
import com.inversioneslr.backend.dto.response.UsuarioResponse;

/**
 * Registro de cuentas (RF-USR-001).
 */
public interface RegistroUsuarioService {

    /**
     * Crea una cuenta con rol HUESPED.
     *
     * @throws com.inversioneslr.backend.exception.CorreoYaRegistradoException si el correo ya existe
     */
    UsuarioResponse registrarHuesped(RegistroHuespedRequest request);
}
