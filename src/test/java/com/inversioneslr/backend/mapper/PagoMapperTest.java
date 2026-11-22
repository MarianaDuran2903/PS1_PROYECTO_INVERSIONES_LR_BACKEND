package com.inversioneslr.backend.mapper;

import com.inversioneslr.backend.dto.response.PagoResponse;
import com.inversioneslr.backend.model.DesglosePrecio;
import com.inversioneslr.backend.model.NombreCompleto;
import com.inversioneslr.backend.model.Pago;
import com.inversioneslr.backend.model.PoliticaCancelacion;
import com.inversioneslr.backend.model.Publicacion;
import com.inversioneslr.backend.model.Reserva;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.EstadoPago;
import com.inversioneslr.backend.model.enums.EstadoReserva;
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

class PagoMapperTest {

    private static final OffsetDateTime AHORA = OffsetDateTime.parse("2030-06-01T10:00:00Z");
    private static final OffsetDateTime VENCE = AHORA.plusMinutes(35);

    private final PagoMapper mapper = new PagoMapper();
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
        ReflectionTestUtils.setField(reserva, "idReserva", 31L);
    }

    private Pago pago() {
        Pago pago = Pago.iniciar(reserva, "cs_test_1", "https://pago.test/cs_test_1", VENCE, "COP", AHORA);
        ReflectionTestUtils.setField(pago, "idPago", 7L);
        return pago;
    }

    @Test
    @DisplayName("Un pago pendiente con la sesión vigente trae la URL de pago y el estado de la reserva")
    void toResponse_pendienteVigente_incluyeLaUrl() {
        PagoResponse respuesta = mapper.toResponse(pago(), AHORA);

        assertThat(respuesta.idPago()).isEqualTo(7L);
        assertThat(respuesta.idReserva()).isEqualTo(31L);
        assertThat(respuesta.estado()).isEqualTo(EstadoPago.PENDIENTE);
        assertThat(respuesta.estadoReserva()).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
        assertThat(respuesta.monto()).isEqualByComparingTo("875000.00");
        assertThat(respuesta.moneda()).isEqualTo("COP");
        assertThat(respuesta.urlPago()).isEqualTo("https://pago.test/cs_test_1");
        assertThat(respuesta.fechaExpiracion()).isEqualTo(VENCE);
        assertThat(respuesta.codigoRechazo()).isNull();
        assertThat(respuesta.motivoRechazo()).isNull();
    }

    @Test
    @DisplayName("Un rechazo trae el mensaje para el Huésped y conserva la URL para reintentar")
    void toResponse_rechazadoVigente_traduceElMotivoYMantieneLaUrl() {
        Pago pago = pago();
        pago.rechazar("insufficient_funds", "pi_1", AHORA);

        PagoResponse respuesta = mapper.toResponse(pago, AHORA.plusMinutes(1));

        assertThat(respuesta.estado()).isEqualTo(EstadoPago.RECHAZADO);
        assertThat(respuesta.codigoRechazo()).isEqualTo("insufficient_funds");
        assertThat(respuesta.motivoRechazo()).isEqualTo("La tarjeta no tiene fondos suficientes.");
        assertThat(respuesta.urlPago()).isEqualTo("https://pago.test/cs_test_1");
    }

    @Test
    @DisplayName("Una sesión vencida, aprobada o expirada ya no expone la URL de pago")
    void toResponse_sesionQueYaNoAdmitePago_ocultaLaUrl() {
        Pago vencida = pago();
        Pago aprobado = pago();
        aprobado.aprobar("pi_1", AHORA);
        Pago expirado = pago();
        expirado.expirar(AHORA);

        assertThat(mapper.toResponse(vencida, VENCE).urlPago()).isNull();
        assertThat(mapper.toResponse(aprobado, AHORA).urlPago()).isNull();
        assertThat(mapper.toResponse(expirado, AHORA).urlPago()).isNull();
    }
}
