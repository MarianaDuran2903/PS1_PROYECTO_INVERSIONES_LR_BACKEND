package com.inversioneslr.backend.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
                new FiltroAlojamientosRequest(
                        null, null, null, null,
                        null, null, null, null)))
                .isEmpty();
    }

    @Test
    void filtrosCompletosValidos_sonAceptados() {
        assertThat(validator.validate(
                new FiltroAlojamientosRequest(
                        3L, LLEGADA, SALIDA, 4,
                        null, null, null, null)))
                .isEmpty();
    }

    @Test
    void ciudadYHuespedesSinFechas_sonValidos() {
        assertThat(validator.validate(
                new FiltroAlojamientosRequest(
                        3L, null, null, 2,
                        null, null, null, null)))
                .isEmpty();
    }

    @Test
    void fechasSinCiudad_sonValidas() {
        assertThat(validator.validate(
                new FiltroAlojamientosRequest(
                        null, LLEGADA, SALIDA, null,
                        null, null, null, null)))
                .isEmpty();
    }

    @Test
    void caracteristicasValidas_sonAceptadas() {
        FiltroAlojamientosRequest filtro = new FiltroAlojamientosRequest(
                null, null, null, 4,
                1L, BigDecimal.ZERO, new BigDecimal("300000.00"),
                List.of(1L, 2L));

        assertThat(validator.validate(filtro)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void tipoNoPositivo_esRechazado(long idTipo) {
        assertCampoInvalido(
                new FiltroAlojamientosRequest(
                        null, null, null, null,
                        idTipo, null, null, null),
                "idTipo");
    }

    @Test
    void precioMinimoNegativo_esRechazado() {
        assertCampoInvalido(
                new FiltroAlojamientosRequest(
                        null, null, null, null,
                        null, new BigDecimal("-1"), null, null),
                "precioMinimo");
    }

    @Test
    void precioMaximoNegativo_esRechazado() {
        assertCampoInvalido(
                new FiltroAlojamientosRequest(
                        null, null, null, null,
                        null, null, new BigDecimal("-1"), null),
                "precioMaximo");
    }

    @Test
    void precioConMasDeDosDecimales_esRechazado() {
        assertCampoInvalido(
                new FiltroAlojamientosRequest(
                        null, null, null, null,
                        null, new BigDecimal("100.001"), null, null),
                "precioMinimo");
    }

    @Test
    void rangoPreciosInvertido_esRechazado() {
        assertCampoInvalido(
                new FiltroAlojamientosRequest(
                        null, null, null, null,
                        null, new BigDecimal("300000"),
                        new BigDecimal("100000"), null),
                "rangoPreciosValido");
    }

    @Test
    void preciosIguales_sonValidos() {
        BigDecimal precio = new BigDecimal("250000.00");

        assertThat(validator.validate(
                new FiltroAlojamientosRequest(
                        null, null, null, null,
                        null, precio, precio, null)))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void servicioNoPositivo_esRechazado(long idServicio) {
        FiltroAlojamientosRequest filtro = new FiltroAlojamientosRequest(
                null, null, null, null,
                null, null, null, List.of(idServicio));

        assertThat(validator.validate(filtro))
                .hasSize(1)
                .allSatisfy(error ->
                        assertThat(error.getPropertyPath().toString())
                                .startsWith("idsServicios"));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void ciudadNoPositiva_esRechazada(long idCiudad) {
        assertCampoInvalido(
                new FiltroAlojamientosRequest(
                        idCiudad, null, null, null,
                        null, null, null, null),
                "idCiudad");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void huespedesNoPositivos_sonRechazados(int huespedes) {
        assertCampoInvalido(
                new FiltroAlojamientosRequest(
                        null, null, null, huespedes,
                        null, null, null, null),
                "huespedes");
    }

    @Test
    void soloLlegada_esRechazada() {
        assertCampoInvalido(
                new FiltroAlojamientosRequest(
                        null, LLEGADA, null, null,
                        null, null, null, null),
                "fechasCompletas");
    }

    @Test
    void soloSalida_esRechazada() {
        assertCampoInvalido(
                new FiltroAlojamientosRequest(
                        null, null, SALIDA, null,
                        null, null, null, null),
                "fechasCompletas");
    }

    @Test
    void salidaIgualALlegada_esRechazada() {
        assertCampoInvalido(
                new FiltroAlojamientosRequest(
                        null, LLEGADA, LLEGADA, null,
                        null, null, null, null),
                "rangoFechasValido");
    }

    @Test
    void salidaAnteriorALlegada_esRechazada() {
        assertCampoInvalido(
                new FiltroAlojamientosRequest(
                        null, SALIDA, LLEGADA, null,
                        null, null, null, null),
                "rangoFechasValido");
    }

    @Test
    void constructorDelFiltro_esResolublePorSpring() {
        var constructor =
                BeanUtils.getResolvableConstructor(FiltroAlojamientosRequest.class);

        assertThat(constructor.getParameterCount()).isEqualTo(8);
    }

    private void assertCampoInvalido(FiltroAlojamientosRequest filtro, String campo) {
        assertThat(validator.validate(filtro))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly(campo);
    }
}