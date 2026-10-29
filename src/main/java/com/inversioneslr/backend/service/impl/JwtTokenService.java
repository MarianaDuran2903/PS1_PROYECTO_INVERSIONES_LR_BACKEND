package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.config.JwtProperties;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.service.TokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

/**
 * JWT firmado con HS256. Contiene el id del usuario (sub), su correo y su rol.
 */
@Service
public class JwtTokenService implements TokenService {

    static final String CLAIM_CORREO = "correo";
    static final String CLAIM_ROL = "rol";

    private final SecretKey clave;
    private final Duration duracion;
    private final Clock clock;

    public JwtTokenService(JwtProperties propiedades, Clock clock) {
        this.clave = Keys.hmacShaKeyFor(propiedades.secreto().getBytes(StandardCharsets.UTF_8));
        this.duracion = Duration.ofMinutes(propiedades.expiracionMinutos());
        this.clock = clock;
    }

    @Override
    public TokenGenerado generar(Usuario usuario) {
        // El JWT guarda las fechas en segundos: se trunca para que expiraEn coincida con el token.
        Instant ahora = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiraEn = ahora.plus(duracion);
        String token = Jwts.builder()
                .subject(String.valueOf(usuario.getIdUsuario()))
                .claim(CLAIM_CORREO, usuario.getCorreo())
                .claim(CLAIM_ROL, usuario.getRol().name())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(expiraEn))
                .signWith(clave)
                .compact();
        return new TokenGenerado(token, ahora, expiraEn);
    }

    @Override
    public Optional<TokenValido> validar(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(clave)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new TokenValido(Long.valueOf(claims.getSubject()), claims.getExpiration().toInstant()));
        } catch (JwtException | IllegalArgumentException e) {
            // NumberFormatException (sub no numérico) es un IllegalArgumentException.
            return Optional.empty();
        }
    }
}
