package com.inversioneslr.backend.security;

import com.inversioneslr.backend.model.enums.Rol;

import java.time.Instant;

/**
 * Usuario de la sesión actual (principal de Spring Security). El rol se toma de la base de datos
 * en cada petición, no del token, para que un cambio de estado aplique de inmediato.
 */
public record UsuarioAutenticado(Long idUsuario, String correo, Rol rol, Instant expiraEn) {
}
