package com.inversioneslr.backend.service;

import com.inversioneslr.backend.model.Usuario;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Emisión y validación de los tokens de sesión (RF-USR-002).
 */
public interface TokenService {

    TokenGenerado generar(Usuario usuario);

    /**
     * @return vacío si el token está mal formado, alterado, firmado con otra clave o vencido
     */
    Optional<TokenValido> validar(String token);

    record TokenGenerado(String token, Instant emitidoEn, Instant expiraEn) {

        public long segundosDeValidez() {
            return Duration.between(emitidoEn, expiraEn).toSeconds();
        }
    }

    record TokenValido(Long idUsuario, Instant expiraEn) {
    }
}
