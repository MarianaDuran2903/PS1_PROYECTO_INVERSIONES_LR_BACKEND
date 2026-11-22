package com.inversioneslr.backend.security;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GeneradorTokenRecuperacionTest {

    private final GeneradorTokenRecuperacion generador = new GeneradorTokenRecuperacion();

    @Test
    void generar_produceTokensBase64UrlDe43CaracteresSinRepetirse() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String token = generador.generar();
            assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
            tokens.add(token);
        }
        assertThat(tokens).hasSize(100);
    }

    @Test
    void hash_esSha256EnHexadecimal() {
        assertThat(generador.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void hash_esDeterministaYDistintoDelToken() {
        String token = generador.generar();

        assertThat(generador.hash(token)).isEqualTo(generador.hash(token)).isNotEqualTo(token).hasSize(64);
    }
}
