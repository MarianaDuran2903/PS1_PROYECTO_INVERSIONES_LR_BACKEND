package com.inversioneslr.backend.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ConfiguracionPlataformaTest {

    private ConfiguracionPlataforma configuracion;

    @BeforeEach
    void setUp() {
        configuracion = BeanUtils.instantiateClass(ConfiguracionPlataforma.class);
        ReflectionTestUtils.setField(configuracion, "porcentajeTarifaServicio", new BigDecimal("10.00"));
        ReflectionTestUtils.setField(configuracion, "porcentajeDescuento", new BigDecimal("15.00"));
        ReflectionTestUtils.setField(configuracion, "nochesMinimasDescuento", 7);
    }

    @ParameterizedTest(name = "{0} noches -> {1}% de descuento")
    @CsvSource({"1, 0", "6, 0", "7, 15", "8, 15", "30, 15"})
    void porcentajeDescuentoPara_aplicaElDescuentoDesdeElMinimoDeNoches(long noches, String esperado) {
        assertThat(configuracion.porcentajeDescuentoPara(noches)).isEqualByComparingTo(esperado);
    }

    @Test
    void getters_exponenLosParametrosConfigurados() {
        assertThat(configuracion.getPorcentajeTarifaServicio()).isEqualByComparingTo("10.00");
        assertThat(configuracion.getPorcentajeDescuento()).isEqualByComparingTo("15.00");
        assertThat(configuracion.getNochesMinimasDescuento()).isEqualTo(7);
    }
}
