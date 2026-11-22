package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.config.JwtProperties;
import com.inversioneslr.backend.model.NombreCompleto;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.Rol;
import com.inversioneslr.backend.service.TokenService.TokenGenerado;
import com.inversioneslr.backend.service.TokenService.TokenValido;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    private static final String SECRETO = "clave-de-pruebas-solo-para-tests-0123456789";
    private static final Instant AHORA = Instant.parse("2026-10-28T12:00:00Z");

    private final JwtTokenService servicio = new JwtTokenService(
            new JwtProperties(SECRETO, 60), Clock.fixed(AHORA, ZoneOffset.UTC));

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = new Usuario(new NombreCompleto("Carlos", null, "Gómez", null),
                "carlos@correo.com", "hash", Rol.ANFITRION);
        ReflectionTestUtils.setField(usuario, "idUsuario", 5L);
    }

    @Test
    void generar_tokenConIdCorreoRolYExpiracionDe60Minutos() {
        TokenGenerado generado = servicio.generar(usuario);

        assertThat(generado.expiraEn()).isEqualTo(AHORA.plus(Duration.ofMinutes(60)));
        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRETO.getBytes(StandardCharsets.UTF_8)))
                .clock(() -> java.util.Date.from(AHORA))
                .build()
                .parseSignedClaims(generado.token())
                .getPayload();
        assertThat(claims.getSubject()).isEqualTo("5");
        assertThat(claims.get(JwtTokenService.CLAIM_CORREO, String.class)).isEqualTo("carlos@correo.com");
        assertThat(claims.get(JwtTokenService.CLAIM_ROL, String.class)).isEqualTo("ANFITRION");
    }

    @Test
    void validar_tokenRecienGenerado_devuelveIdYExpiracion() {
        TokenGenerado generado = servicio.generar(usuario);

        Optional<TokenValido> valido = servicio.validar(generado.token());

        assertThat(valido).contains(new TokenValido(5L, AHORA.plus(Duration.ofMinutes(60))));
    }

    @Test
    void validar_tokenVencido_devuelveVacio() {
        String token = servicio.generar(usuario).token();
        JwtTokenService dosHorasDespues = new JwtTokenService(new JwtProperties(SECRETO, 60),
                Clock.fixed(AHORA.plus(Duration.ofHours(2)), ZoneOffset.UTC));

        assertThat(dosHorasDespues.validar(token)).isEmpty();
    }

    @Test
    void validar_tokenFirmadoConOtraClave_devuelveVacio() {
        JwtTokenService otraClave = new JwtTokenService(
                new JwtProperties("otra-clave-distinta-para-firmar-tokens-xyz", 60), Clock.fixed(AHORA, ZoneOffset.UTC));
        String token = otraClave.generar(usuario).token();

        assertThat(servicio.validar(token)).isEmpty();
    }

    @Test
    void validar_tokenAlterado_devuelveVacio() {
        String token = servicio.generar(usuario).token();
        String[] partes = token.split("\\.");
        // Cambia el contenido (por ejemplo, para hacerse pasar por otro usuario) sin poder refirmar.
        String alterado = partes[0] + "." + partes[1].substring(0, partes[1].length() - 2) + "AA." + partes[2];

        assertThat(servicio.validar(alterado)).isEmpty();
    }

    @Test
    void validar_textoQueNoEsToken_devuelveVacio() {
        assertThat(servicio.validar("no-es-un-token")).isEmpty();
        assertThat(servicio.validar("")).isEmpty();
    }
}
