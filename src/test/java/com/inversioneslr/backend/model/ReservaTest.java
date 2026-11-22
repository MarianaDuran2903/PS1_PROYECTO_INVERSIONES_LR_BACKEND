package com.inversioneslr.backend.model;

import com.inversioneslr.backend.model.enums.EstadoReserva;
import com.inversioneslr.backend.model.enums.Rol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservaTest {

    private static final LocalDate LLEGADA = LocalDate.parse("2030-06-10");
    private static final LocalDate SALIDA = LocalDate.parse("2030-06-13");
    private static final OffsetDateTime VENCE = OffsetDateTime.parse("2030-06-01T10:30:00Z");

    private Publicacion publicacion;
    private PoliticaCancelacion politica;
    private Usuario huesped;
    private DesglosePrecio precio;

    @BeforeEach
    void setUp() {
        huesped = new Usuario(new NombreCompleto("Ana", null, "Pérez", null), "ana@correo.com", "hash", Rol.HUESPED);
        politica = BeanUtils.instantiateClass(PoliticaCancelacion.class);
        ReflectionTestUtils.setField(politica, "horasAnticipacionMin", 24);
        ReflectionTestUtils.setField(politica, "porcentajeDevolucion", new BigDecimal("100.00"));
        publicacion = BeanUtils.instantiateClass(Publicacion.class);
        ReflectionTestUtils.setField(publicacion, "idPublicacion", 12L);
        precio = DesglosePrecio.calcular(new BigDecimal("250000.00"), new BigDecimal("50000.00"), 3,
                BigDecimal.ZERO, new BigDecimal("10.00"));
    }

    private Reserva preReserva() {
        return Reserva.preReservar(huesped, publicacion, LLEGADA, SALIDA, 2, "clave-1", precio, politica, VENCE);
    }

    @Test
    @DisplayName("Nace PENDIENTE_PAGO con el precio, la política y el vencimiento recibidos")
    void preReservar_copiaElPrecioLaPoliticaYElVencimiento() {
        Reserva reserva = preReserva();

        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
        assertThat(reserva.getHuesped()).isSameAs(huesped);
        assertThat(reserva.getPublicacion()).isSameAs(publicacion);
        assertThat(reserva.getFechaLlegada()).isEqualTo(LLEGADA);
        assertThat(reserva.getFechaSalida()).isEqualTo(SALIDA);
        assertThat(reserva.getNumeroHuespedes()).isEqualTo(2);
        assertThat(reserva.getClaveIdempotencia()).isEqualTo("clave-1");
        assertThat(reserva.getPrecioBase()).isEqualByComparingTo("750000.00");
        assertThat(reserva.getPorcentajeDescuentoAplicado()).isEqualByComparingTo("0");
        assertThat(reserva.getValorDescuento()).isEqualByComparingTo("0.00");
        assertThat(reserva.getTarifaLimpieza()).isEqualByComparingTo("50000.00");
        assertThat(reserva.getPorcentajeTarifaServicio()).isEqualByComparingTo("10.00");
        assertThat(reserva.getTarifaServicio()).isEqualByComparingTo("75000.00");
        assertThat(reserva.getTotal()).isEqualByComparingTo("875000.00");
        assertThat(reserva.getHorasAnticipacionAplicada()).isEqualTo(24);
        assertThat(reserva.getPorcentajeDevolucionAplicado()).isEqualByComparingTo("100.00");
        assertThat(reserva.getFechaExpiracion()).isEqualTo(VENCE);
        assertThat(reserva.estaPendienteDePago()).isTrue();
    }

    @Test
    void noches_esLaDiferenciaEntreSalidaYLlegada() {
        assertThat(preReserva().noches()).isEqualTo(3);
    }

    @Test
    @DisplayName("Una política que cambie después no altera la reserva ya creada")
    void preReservar_conservaLaPoliticaDeCuandoSeCreo() {
        Reserva reserva = preReserva();

        ReflectionTestUtils.setField(politica, "horasAnticipacionMin", 168);
        ReflectionTestUtils.setField(politica, "porcentajeDevolucion", new BigDecimal("50.00"));

        assertThat(reserva.getHorasAnticipacionAplicada()).isEqualTo(24);
        assertThat(reserva.getPorcentajeDevolucionAplicado()).isEqualByComparingTo("100.00");
    }

    @Test
    void vencer_unaReservaPendiente_quedaCancelada() {
        Reserva reserva = preReserva();

        reserva.vencer();

        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(reserva.estaPendienteDePago()).isFalse();
    }

    @ParameterizedTest(name = "estado {0}")
    @EnumSource(value = EstadoReserva.class, names = "PENDIENTE_PAGO", mode = EnumSource.Mode.EXCLUDE)
    void vencer_unaReservaQueNoEstaPendiente_noSePermite(EstadoReserva estado) {
        Reserva reserva = preReserva();
        ReflectionTestUtils.setField(reserva, "estado", estado);

        assertThatThrownBy(reserva::vencer)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(estado.name());
        assertThat(reserva.getEstado()).isEqualTo(estado);
    }

    @Test
    @DisplayName("Vence en el instante exacto de la expiración, no antes")
    void estaVencida_comparaConElInstanteDeExpiracion() {
        Reserva reserva = preReserva();

        assertThat(reserva.estaVencida(VENCE.minusNanos(1))).isFalse();
        assertThat(reserva.estaVencida(VENCE)).isTrue();
        assertThat(reserva.estaVencida(VENCE.plusMinutes(5))).isTrue();
    }

    @Test
    void estaVencida_unaReservaQueNoEstaPendiente_nuncaVence() {
        Reserva reserva = preReserva();
        ReflectionTestUtils.setField(reserva, "estado", EstadoReserva.CONFIRMADA);

        assertThat(reserva.estaVencida(VENCE.plusDays(30))).isFalse();
    }

    @Test
    void coincideCon_lamismaSolicitud_esVerdadero() {
        assertThat(preReserva().coincideCon(12L, LLEGADA, SALIDA, 2)).isTrue();
    }

    @Test
    void coincideCon_cualquierDatoDistinto_esFalso() {
        Reserva reserva = preReserva();

        assertThat(reserva.coincideCon(13L, LLEGADA, SALIDA, 2)).isFalse();
        assertThat(reserva.coincideCon(12L, LLEGADA.plusDays(1), SALIDA, 2)).isFalse();
        assertThat(reserva.coincideCon(12L, LLEGADA, SALIDA.plusDays(1), 2)).isFalse();
        assertThat(reserva.coincideCon(12L, LLEGADA, SALIDA, 3)).isFalse();
    }
}
