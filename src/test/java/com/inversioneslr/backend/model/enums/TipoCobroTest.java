package com.inversioneslr.backend.model.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TipoCobroTest {

    @ParameterizedTest(name = "{0} noches a {1} por noche -> {2}")
    @CsvSource({
            "1, 20000.00, 20000.00",
            "3, 20000.00, 60000.00",
            "7, 15500.50, 108503.50"
    })
    void porNoche_multiplicaElPrecioPorLasNoches(long noches, String precio, String esperado) {
        assertThat(TipoCobro.POR_NOCHE.costoPara(new BigDecimal(precio), noches)).isEqualByComparingTo(esperado);
    }

    @ParameterizedTest(name = "{0} noches -> el precio una sola vez")
    @CsvSource({"1", "3", "30"})
    void porEstancia_cobraElPrecioUnaSolaVez(long noches) {
        assertThat(TipoCobro.POR_ESTANCIA.costoPara(new BigDecimal("80000.00"), noches))
                .isEqualByComparingTo("80000.00");
    }

    @Test
    void costoPara_redondeaADosDecimalesHaciaArribaEnLaMitad() {
        assertThat(TipoCobro.POR_NOCHE.costoPara(new BigDecimal("100.005"), 1)).isEqualTo(new BigDecimal("100.01"));
        assertThat(TipoCobro.POR_ESTANCIA.costoPara(new BigDecimal("100.005"), 5)).isEqualTo(new BigDecimal("100.01"));
    }

    @Test
    void costoPara_devuelveSiempreEscalaDos() {
        assertThat(TipoCobro.POR_NOCHE.costoPara(new BigDecimal("20000"), 3).scale()).isEqualTo(2);
        assertThat(TipoCobro.POR_ESTANCIA.costoPara(new BigDecimal("80000"), 3).scale()).isEqualTo(2);
    }
}
