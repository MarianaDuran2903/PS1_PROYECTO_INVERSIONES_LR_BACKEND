package com.inversioneslr.backend.model;

import com.inversioneslr.backend.model.enums.TipoPeriodo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.BeanUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PeriodoDisponibilidadTest {

    private final Publicacion publicacion = BeanUtils.instantiateClass(Publicacion.class);

    private PeriodoDisponibilidad disponible(String inicio, String fin) {
        return PeriodoDisponibilidad.disponible(publicacion, LocalDate.parse(inicio), LocalDate.parse(fin));
    }

    @ParameterizedTest(name = "tramo 10-20 y estadía {0} a {1} -> {2} noches")
    @CsvSource({
            "2030-06-12, 2030-06-15, 3",
            "2030-06-05, 2030-06-12, 2",
            "2030-06-18, 2030-06-25, 2",
            "2030-06-05, 2030-06-25, 10",
            "2030-06-10, 2030-06-20, 10",
            // La salida no cuenta como noche ocupada: el tramo que termina el 10 no toca la noche del 10.
            "2030-06-01, 2030-06-10, 0",
            "2030-06-20, 2030-06-25, 0",
            "2030-07-01, 2030-07-05, 0"
    })
    void nochesEnComun_cuentaSoloLasNochesQueSeTocan(String llegada, String salida, long esperadas) {
        PeriodoDisponibilidad periodo = disponible("2030-06-10", "2030-06-20");

        assertThat(periodo.nochesEnComun(LocalDate.parse(llegada), LocalDate.parse(salida))).isEqualTo(esperadas);
    }

    @Test
    void cubren_unTramoQueContieneLaEstadia_laCubre() {
        assertThat(PeriodoDisponibilidad.cubren(List.of(disponible("2030-06-01", "2030-07-01")),
                LocalDate.parse("2030-06-10"), LocalDate.parse("2030-06-13"))).isTrue();
    }

    @Test
    void cubren_dosTramosContiguos_laCubrenJuntos() {
        List<PeriodoDisponibilidad> tramos = List.of(disponible("2030-06-01", "2030-06-11"),
                disponible("2030-06-11", "2030-07-01"));

        assertThat(PeriodoDisponibilidad.cubren(tramos, LocalDate.parse("2030-06-10"),
                LocalDate.parse("2030-06-13"))).isTrue();
    }

    @Test
    void cubren_unTramoQueTerminaJustoEnLaSalida_laCubre() {
        assertThat(PeriodoDisponibilidad.cubren(List.of(disponible("2030-06-10", "2030-06-13")),
                LocalDate.parse("2030-06-10"), LocalDate.parse("2030-06-13"))).isTrue();
    }

    @Test
    void cubren_conUnHuecoEnMedio_noLaCubren() {
        List<PeriodoDisponibilidad> tramos = List.of(disponible("2030-06-01", "2030-06-11"),
                disponible("2030-06-12", "2030-07-01"));

        assertThat(PeriodoDisponibilidad.cubren(tramos, LocalDate.parse("2030-06-10"),
                LocalDate.parse("2030-06-13"))).isFalse();
    }

    @ParameterizedTest(name = "tramo 10-13 y estadía {0} a {1}")
    @CsvSource({
            "2030-06-09, 2030-06-13",
            "2030-06-10, 2030-06-14",
            "2030-06-01, 2030-06-30"
    })
    void cubren_cuandoLaEstadiaSeSaleDelTramo_noLaCubren(String llegada, String salida) {
        assertThat(PeriodoDisponibilidad.cubren(List.of(disponible("2030-06-10", "2030-06-13")),
                LocalDate.parse(llegada), LocalDate.parse(salida))).isFalse();
    }

    @Test
    void cubren_sinTramos_noLaCubren() {
        assertThat(PeriodoDisponibilidad.cubren(List.of(), LocalDate.parse("2030-06-10"),
                LocalDate.parse("2030-06-13"))).isFalse();
    }

    @Test
    void cubren_unaEstadiaSinNoches_nuncaSeCubre() {
        assertThat(PeriodoDisponibilidad.cubren(List.of(disponible("2030-06-01", "2030-07-01")),
                LocalDate.parse("2030-06-10"), LocalDate.parse("2030-06-10"))).isFalse();
    }

    @Test
    void disponible_nace_sinReserva() {
        PeriodoDisponibilidad periodo = disponible("2030-06-10", "2030-06-20");

        assertThat(periodo.getTipo()).isEqualTo(TipoPeriodo.DISPONIBLE);
        assertThat(periodo.getReserva()).isNull();
        assertThat(periodo.getPublicacion()).isSameAs(publicacion);
        assertThat(periodo.getFechaInicio()).isEqualTo(LocalDate.parse("2030-06-10"));
        assertThat(periodo.getFechaFin()).isEqualTo(LocalDate.parse("2030-06-20"));
    }

    @Test
    void reservado_quedaAsociadoALaReserva() {
        Reserva reserva = BeanUtils.instantiateClass(Reserva.class);

        PeriodoDisponibilidad periodo = PeriodoDisponibilidad.reservado(publicacion, reserva,
                LocalDate.parse("2030-06-10"), LocalDate.parse("2030-06-13"));

        assertThat(periodo.getTipo()).isEqualTo(TipoPeriodo.RESERVADO);
        assertThat(periodo.getReserva()).isSameAs(reserva);
    }

    @Test
    void liberar_unPeriodoReservado_vuelveADisponibleYSoltaLaReserva() {
        Reserva reserva = BeanUtils.instantiateClass(Reserva.class);
        PeriodoDisponibilidad periodo = PeriodoDisponibilidad.reservado(publicacion, reserva,
                LocalDate.parse("2030-06-10"), LocalDate.parse("2030-06-13"));

        periodo.liberar();

        assertThat(periodo.getTipo()).isEqualTo(TipoPeriodo.DISPONIBLE);
        assertThat(periodo.getReserva()).isNull();
        assertThat(periodo.getFechaInicio()).isEqualTo(LocalDate.parse("2030-06-10"));
        assertThat(periodo.getFechaFin()).isEqualTo(LocalDate.parse("2030-06-13"));
    }

    @Test
    void cerrarComoNoOfrecido_unPeriodoDisponible_loMarcaSinCambiarElRango() {
        PeriodoDisponibilidad periodo = disponible("2030-06-10", "2030-06-20");

        periodo.cerrarComoNoOfrecido();

        assertThat(periodo.getTipo()).isEqualTo(TipoPeriodo.NO_OFRECIDO);
        assertThat(periodo.getReserva()).isNull();
        assertThat(periodo.getFechaInicio()).isEqualTo(LocalDate.parse("2030-06-10"));
        assertThat(periodo.getFechaFin()).isEqualTo(LocalDate.parse("2030-06-20"));
    }

    @Test
    void cerrarComoNoOfrecido_unPeriodoReservado_noSePermite() {
        Reserva reserva = BeanUtils.instantiateClass(Reserva.class);
        PeriodoDisponibilidad periodo = PeriodoDisponibilidad.reservado(publicacion, reserva,
                LocalDate.parse("2030-06-10"), LocalDate.parse("2030-06-20"));

        assertThatThrownBy(periodo::cerrarComoNoOfrecido)
                .isInstanceOf(IllegalStateException.class);
        assertThat(periodo.getTipo()).isEqualTo(TipoPeriodo.RESERVADO);
        assertThat(periodo.getReserva()).isSameAs(reserva);
    }

    @Test
    void liberar_unPeriodoDisponible_noSePermite() {
        PeriodoDisponibilidad periodo = disponible("2030-06-10", "2030-06-20");

        assertThatThrownBy(periodo::liberar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DISPONIBLE");
    }

    @ParameterizedTest(name = "{0} a {1}")
    @CsvSource({"2030-06-10, 2030-06-10", "2030-06-10, 2030-06-09"})
    void crear_conFinNoPosteriorAlInicio_seRechaza(String inicio, String fin) {
        assertThatThrownBy(() -> disponible(inicio, fin))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
