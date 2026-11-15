package com.inversioneslr.backend.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FiltroAlojamientosRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    private static final LocalDate LLEGADA = LocalDate.of(2030, 6, 10);
    private static final LocalDate SALIDA = LocalDate.of(2030, 6, 13);

    @BeforeAll
    static void configurarValidador() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void cerrarValidador() {
        factory.close();
    }

    @Test
    void sinFiltros_esValido() {
        assertThat(validator.validate(
                new FiltroAlojamientosRequest(null, null, null, null)))
                .isEmpty();
    }

    @Test
    void filtrosCompletosValidos_sonAceptados() {
        assertThat(validator.validate(
                new FiltroAlojamientosRequest(3L, LLEGADA, SALIDA, 4)))
                .isEmpty();
    }

    @Test
    void ciudadYHuespedesSinFechas_sonValidos() {
        assertThat(validator.validate(
                new FiltroAlojamientosRequest(3L, null, null, 2)))
                .isEmpty();
    }

    @Test
    void fechasSinCiudad_sonValidas() {
        assertThat(validator.validate(
                new FiltroAlojamientosRequest(null, LLEGADA, SALIDA, null)))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void ciudadNoPositiva_esRechazada(long idCiudad) {
        assertCampoInvalido(
                new FiltroAlojamientosRequest(idCiudad, null, null, null),
                "idCiudad");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void huespedesNoPositivos_sonRechazados(int huespedes) {
        assertCampoInvalido(
                new FiltroAlojamientosRequest(null, null, null, huespedes),
                "huespedes");
    }

    @Test
    void soloLlegada_esRechazada() {
        assertCampoInvalido(
                new FiltroAlojamientosRequest(null, LLEGADA, null, null),
                "fechasCompletas");
    }

    @Test
    void soloSalida_esRechazada() {
        assertCampoInvalido(
                new FiltroAlojamientosRequest(null, null, SALIDA, null),
                "fechasCompletas");
    }

    @Test
    void salidaIgualALlegada_esRechazada() {
        assertCampoInvalido(
                new FiltroAlojamientosRequest(null, LLEGADA, LLEGADA, null),
                "rangoFechasValido");
    }

    @Test
    void salidaAnteriorALlegada_esRechazada() {
        assertCampoInvalido(
                new FiltroAlojamientosRequest(null, SALIDA, LLEGADA, null),
                "rangoFechasValido");
    }

    private void assertCampoInvalido(FiltroAlojamientosRequest filtro, String campo) {
        assertThat(validator.validate(filtro))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly(campo);
    }
}