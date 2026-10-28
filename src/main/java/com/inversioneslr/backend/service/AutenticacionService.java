package com.inversioneslr.backend.service;

import com.inversioneslr.backend.dto.request.LoginRequest;
import com.inversioneslr.backend.dto.response.LoginResponse;
import com.inversioneslr.backend.dto.response.SesionResponse;
import com.inversioneslr.backend.security.UsuarioAutenticado;

import java.util.Optional;

/**
 * Inicio de sesión y sesiones activas (RF-USR-002, RF-USR-003).
 */
public interface AutenticacionService {

    /**
     * Valida las credenciales y emite el token de sesión.
     *
     * @throws com.inversioneslr.backend.exception.CredencialesInvalidasException con mensaje genérico si el
     *         correo no existe, la contraseña no coincide o la cuenta está desactivada o restringida
     */
    LoginResponse iniciarSesion(LoginRequest request);

    /**
     * Resuelve el usuario de un token. Vacío si el token no es válido, el usuario ya no existe o su cuenta
     * está desactivada o restringida.
     */
    Optional<UsuarioAutenticado> autenticarToken(String token);

    SesionResponse obtenerSesion(UsuarioAutenticado usuario);
}
