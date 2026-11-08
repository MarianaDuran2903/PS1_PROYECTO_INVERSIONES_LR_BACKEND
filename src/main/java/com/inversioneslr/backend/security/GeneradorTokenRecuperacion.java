package com.inversioneslr.backend.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Tokens de recuperación de contraseña: 32 bytes aleatorios en Base64 URL (43 caracteres). En la base solo se
 * guarda su hash SHA-256, así que un volcado de la tabla no permite usarlos.
 */
@Component
public class GeneradorTokenRecuperacion {

    private static final int BYTES_TOKEN = 32;

    private final SecureRandom aleatorio = new SecureRandom();

    public String generar() {
        byte[] bytes = new byte[BYTES_TOKEN];
        aleatorio.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 del token en hexadecimal (64 caracteres).
     */
    public String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no ofrece SHA-256", e);
        }
    }
}
