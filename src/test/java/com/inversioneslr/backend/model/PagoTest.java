package com.inversioneslr.backend.model;

import com.inversioneslr.backend.model.enums.EstadoPago;
import com.inversioneslr.backend.model.enums.Rol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PagoTest {

    private static final OffsetDateTime AHORA = OffsetDateTime.parse("2030-06-01T10:00:00Z");
    private static final OffsetDateTime VENCE = AHORA.plusMinutes(35);

    private Reserva reserva;

    @BeforeEach
    void setUp() {
        Usuario huesped = new Usuario(new NombreCompleto("Ana", null, "Pérez", null), "ana@correo.com", "hash",
                Rol.HUESPED);
        PoliticaCancelacion politica = BeanUtils.instantiateClass(PoliticaCancelacion.class);
        ReflectionTestUtils.setField(politica, "horasAnticipacionMin", 24);
        ReflectionTestUtils.setField(politica, "porcentajeDevolucion", new BigDecimal("100.00"));
        Publicacion publicacion = BeanUtils.instantiateClass(Publicacion.class);
        DesglosePrecio precio = DesglosePrecio.calcular(new BigDecimal("250000.00"), new BigDecimal("50000.00"), 3,
                BigDecimal.ZERO, new BigDecimal("10.00"));
        reserva = Reserva.preReservar(huesped, publicacion, LocalDate.parse("2030-06-10"),
                LocalDate.parse("2030-06-13"), 2, "clave-1", precio, politica, AHORA.plusMinutes(30));
    }

    private Pago pago() {
        return Pago.iniciar(reserva, "cs_test_1", "https://pago.test/cs_test_1", VENCE, "COP", AHORA);
    }

    @Test
    @DisplayName("Nace PENDIENTE con el total de la reserva y la sesión de Checkout")
    void iniciar_tomaElMontoDeLaReserva() {
        Pago pago = pago();

        assertThat(pago.getEstado()).isEqualTo(EstadoPago.PENDIENTE);
        assertThat(pago.getReserva()).isSameAs(reserva);
        assertThat(pago.getMonto()).isEqualByComparingTo("875000.00");
        assertThat(pago.getMoneda()).isEqualTo("COP");
        assertThat(pago.getReferenciaStripe()).isEqualTo("cs_test_1");
        assertThat(pago.getUrlPago()).isEqualTo("https://pago.test/cs_test_1");
        assertThat(pago.getFechaExpiracion()).isEqualTo(VENCE);
        assertThat(pago.getFechaHora()).isEqualTo(AHORA);
        assertThat(pago.getFechaActualizacion()).isEqualTo(AHORA);
        assertThat(pago.getIdPaymentIntent()).isNull();
        assertThat(pago.getCodigoRechazo()).isNull();
    }

    @Test
    void sesionVigente_soloMientrasEstaAbiertaYNoVencio() {
        Pago pago = pago();

        assertThat(pago.sesionVigente(AHORA)).isTrue();
        assertThat(pago.sesionVigente(VENCE.minusNanos(1))).isTrue();
        assertThat(pago.sesionVigente(VENCE)).isFalse();

        pago.rechazar("card_declined", "pi_1", AHORA);
        assertThat(pago.sesionVigente(AHORA)).isTrue();

        pago.aprobar("pi_1", AHORA);
        assertThat(pago.sesionVigente(AHORA)).isFalse();
    }

    @Test
    void coincideCon_comparaMontoYMonedaSinImportarLaEscalaNiLaCapitalizacion() {
        Pago pago = pago();

        assertThat(pago.coincideCon(new BigDecimal("875000.00"), "COP")).isTrue();
        assertThat(pago.coincideCon(new BigDecimal("875000"), "cop")).isTrue();
        assertThat(pago.coincideCon(new BigDecimal("874999.99"), "COP")).isFalse();
        assertThat(pago.coincideCon(new BigDecimal("875000.00"), "USD")).isFalse();
        assertThat(pago.coincideCon(null, "COP")).isFalse();
    }

    @Test
    void aprobar_unPagoPendiente_quedaAprobadoConSuEvidencia() {
        Pago pago = pago();
        OffsetDateTime despues = AHORA.plusMinutes(3);

        boolean cambio = pago.aprobar("pi_1", despues);

        assertThat(cambio).isTrue();
        assertThat(pago.getEstado()).isEqualTo(EstadoPago.APROBADO);
        assertThat(pago.getIdPaymentIntent()).isEqualTo("pi_1");
        assertThat(pago.getCodigoRechazo()).isNull();
        assertThat(pago.getFechaActualizacion()).isEqualTo(despues);
    }

    @Test
    @DisplayName("Un pago rechazado se aprueba si el Huésped reintenta con otra tarjeta, y borra el rechazo")
    void aprobar_unPagoRechazado_quedaAprobadoSinCodigoDeRechazo() {
        Pago pago = pago();
        pago.rechazar("card_declined", "pi_1", AHORA);

        assertThat(pago.aprobar("pi_1", AHORA.plusMinutes(1))).isTrue();

        assertThat(pago.getEstado()).isEqualTo(EstadoPago.APROBADO);
        assertThat(pago.getCodigoRechazo()).isNull();
    }

    @Test
    @DisplayName("APROBADO es final: aprobar de nuevo no cambia nada")
    void aprobar_unPagoAprobado_noCambia() {
        Pago pago = pago();
        pago.aprobar("pi_1", AHORA);

        assertThat(pago.aprobar("pi_2", AHORA.plusMinutes(5))).isFalse();

        assertThat(pago.getIdPaymentIntent()).isEqualTo("pi_1");
        assertThat(pago.getFechaActualizacion()).isEqualTo(AHORA);
    }

    @Test
    void rechazar_unPagoPendiente_guardaElCodigoYElPaymentIntent() {
        Pago pago = pago();

        boolean cambio = pago.rechazar("insufficient_funds", "pi_1", AHORA.plusMinutes(1));

        assertThat(cambio).isTrue();
        assertThat(pago.getEstado()).isEqualTo(EstadoPago.RECHAZADO);
        assertThat(pago.getCodigoRechazo()).isEqualTo("insufficient_funds");
        assertThat(pago.getIdPaymentIntent()).isEqualTo("pi_1");
        assertThat(pago.getFechaActualizacion()).isEqualTo(AHORA.plusMinutes(1));
    }

    @Test
    @DisplayName("Un segundo intento fallido actualiza el código; sin payment intent conserva el anterior")
    void rechazar_unPagoRechazado_actualizaElCodigo() {
        Pago pago = pago();
        pago.rechazar("card_declined", "pi_1", AHORA);

        assertThat(pago.rechazar("expired_card", null, AHORA.plusMinutes(1))).isTrue();

        assertThat(pago.getCodigoRechazo()).isEqualTo("expired_card");
        assertThat(pago.getIdPaymentIntent()).isEqualTo("pi_1");
    }

    @Test
    @DisplayName("Un rechazo tardío no degrada un pago aprobado ni uno expirado")
    void rechazar_unPagoFinal_noCambia() {
        Pago aprobado = pago();
        aprobado.aprobar("pi_1", AHORA);
        Pago expirado = pago();
        expirado.expirar(AHORA);

        assertThat(aprobado.rechazar("card_declined", "pi_2", AHORA.plusMinutes(1))).isFalse();
        assertThat(expirado.rechazar("card_declined", "pi_2", AHORA.plusMinutes(1))).isFalse();

        assertThat(aprobado.getEstado()).isEqualTo(EstadoPago.APROBADO);
        assertThat(aprobado.getCodigoRechazo()).isNull();
        assertThat(expirado.getEstado()).isEqualTo(EstadoPago.EXPIRADO);
        assertThat(expirado.getCodigoRechazo()).isNull();
    }

    @Test
    void expirar_unaSesionAbierta_quedaExpirada() {
        Pago pendiente = pago();
        Pago rechazado = pago();
        rechazado.rechazar("card_declined", "pi_1", AHORA);

        assertThat(pendiente.expirar(VENCE)).isTrue();
        assertThat(rechazado.expirar(VENCE)).isTrue();

        assertThat(pendiente.getEstado()).isEqualTo(EstadoPago.EXPIRADO);
        assertThat(rechazado.getEstado()).isEqualTo(EstadoPago.EXPIRADO);
        assertThat(pendiente.getFechaActualizacion()).isEqualTo(VENCE);
    }

    @Test
    void expirar_unPagoAprobado_noCambia() {
        Pago pago = pago();
        pago.aprobar("pi_1", AHORA);

        assertThat(pago.expirar(VENCE)).isFalse();

        assertThat(pago.getEstado()).isEqualTo(EstadoPago.APROBADO);
    }
}
