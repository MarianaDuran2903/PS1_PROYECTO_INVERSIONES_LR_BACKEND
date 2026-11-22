package com.inversioneslr.backend.service;

import com.inversioneslr.backend.dto.request.RegistroUsuarioRequest;
import com.inversioneslr.backend.dto.response.UsuarioResponse;

/**
 * Registro de cuentas. El rol lo fija el servidor según el caso de uso.
 */
public interface RegistroUsuarioService {

    /**
     * Crea una cuenta con rol HUESPED.
     *
     * @throws com.inversioneslr.backend.exception.CorreoYaRegistradoException si el correo ya existe
     */
    UsuarioResponse registrarHuesped(RegistroUsuarioRequest request);

    /**
     * Crea una cuenta con rol ANFITRION.
     *
     * @throws com.inversioneslr.backend.exception.CorreoYaRegistradoException si el correo ya existe
     */
    UsuarioResponse registrarAnfitrion(RegistroUsuarioRequest request);
}
