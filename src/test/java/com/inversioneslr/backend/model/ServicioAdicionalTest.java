package com.inversioneslr.backend.model;

import com.inversioneslr.backend.model.enums.TipoCobro;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ServicioAdicionalTest {

    @Test
    void crear_conservaLosDatosYSuIdentificador() {
        ServicioAdicional servicio = new ServicioAdicional(12L, "Desayuno", new BigDecimal("20000.00"),
                TipoCobro.POR_NOCHE);

        assertThat(servicio.getIdPublicacion()).isEqualTo(12L);
        assertThat(servicio.getNombre()).isEqualTo("Desayuno");
        assertThat(servicio.getPrecio()).isEqualByComparingTo("20000.00");
        assertThat(servicio.getTipoCobro()).isEqualTo(TipoCobro.POR_NOCHE);
    }

    @Test
    void costoPara_unServicioPorNoche_seMultiplicaPorLasNoches() {
        ServicioAdicional desayuno = new ServicioAdicional(12L, "Desayuno", new BigDecimal("20000.00"),
                TipoCobro.POR_NOCHE);

        assertThat(desayuno.costoPara(3)).isEqualByComparingTo("60000.00");
    }

    @Test
    void costoPara_unServicioPorEstancia_seCobraUnaVez() {
        ServicioAdicional traslado = new ServicioAdicional(12L, "Traslado", new BigDecimal("80000.00"),
                TipoCobro.POR_ESTANCIA);

        assertThat(traslado.costoPara(3)).isEqualByComparingTo("80000.00");
    }

    @Test
    void identificador_distingueLaPublicacionYElNombre() {
        ServicioAdicionalId id = new ServicioAdicionalId(12L, "Desayuno");

        assertThat(id).isEqualTo(new ServicioAdicionalId(12L, "Desayuno"))
                .hasSameHashCodeAs(new ServicioAdicionalId(12L, "Desayuno"))
                .isNotEqualTo(new ServicioAdicionalId(13L, "Desayuno"))
                .isNotEqualTo(new ServicioAdicionalId(12L, "Traslado"));
    }
}
