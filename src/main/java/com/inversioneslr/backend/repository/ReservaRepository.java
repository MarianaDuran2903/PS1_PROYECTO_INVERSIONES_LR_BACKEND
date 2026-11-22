package com.inversioneslr.backend.repository;

import com.inversioneslr.backend.model.Reserva;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    /**
     * La reserva con su fila bloqueada hasta el fin de la transacción. Todo cambio del pago de una reserva toma
     * este bloqueo primero (el aviso de Stripe, la consulta del Huésped, el vencimiento), así no se pisan y
     * siempre bloquean en el mismo orden.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reserva r WHERE r.idReserva = :idReserva")
    Optional<Reserva> bloquearPorId(@Param("idReserva") Long idReserva);

    /**
     * La reserva que un Huésped creó con esa clave de idempotencia, si existe. La clave es única por Huésped.
     */
    Optional<Reserva> findByHuespedIdUsuarioAndClaveIdempotencia(Long idHuesped, String claveIdempotencia);

    /**
     * Reservas que siguen esperando el pago y cuyo plazo ya terminó, bloqueadas para vencerlas. SKIP LOCKED
     * deja a otra instancia las que ya está procesando, así la tarea programada no se estorba a sí misma.
     */
    @Query(value = """
            SELECT r.*
            FROM reserva r
            WHERE r.estado = 'PENDIENTE_PAGO'
              AND r.fecha_expiracion <= :ahora
            ORDER BY r.fecha_expiracion, r.id_reserva
            LIMIT :limite
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Reserva> bloquearPendientesVencidas(@Param("ahora") OffsetDateTime ahora, @Param("limite") int limite);

    /**
     * Igual que {@link #bloquearPendientesVencidas}, limitado a una publicación. Se usa al reservar para que una
     * reserva vencida cuya tarea aún no corrió no estorbe a quien pide esas mismas fechas.
     */
    @Query(value = """
            SELECT r.*
            FROM reserva r
            WHERE r.id_publicacion = :idPublicacion
              AND r.estado = 'PENDIENTE_PAGO'
              AND r.fecha_expiracion <= :ahora
            ORDER BY r.fecha_expiracion, r.id_reserva
            FOR UPDATE
            """, nativeQuery = true)
    List<Reserva> bloquearPendientesVencidasDe(@Param("idPublicacion") Long idPublicacion,
                                               @Param("ahora") OffsetDateTime ahora);
}
