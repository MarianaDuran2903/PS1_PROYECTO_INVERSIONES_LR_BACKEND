package com.inversioneslr.backend.service;

import com.inversioneslr.backend.dto.request.RegistroUsuarioRequest;
import com.inversioneslr.backend.dto.response.UsuarioResponse;

/**
 * Registro de cuentas (RF-USR-001). El rol lo fija el servidor según el caso de uso.
 */
public interface RegistroUsuarioService {

    /**
     * Crea una cuenta con rol HUESPED (HU-001).
     *
     * @throws com.inversioneslr.backend.exception.CorreoYaRegistradoException si el correo ya existe
     */
    UsuarioResponse registrarHuesped(RegistroUsuarioRequest request);

    /**
     * Crea una cuenta con rol ANFITRION (HU-002).
     *
     * @throws com.inversioneslr.backend.exception.CorreoYaRegistradoException si el correo ya existe
     */
    UsuarioResponse registrarAnfitrion(RegistroUsuarioRequest request);
}
