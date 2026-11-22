package com.inversioneslr.backend.repository;

import com.inversioneslr.backend.model.Pago;
import com.inversioneslr.backend.model.enums.EstadoPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    /**
     * El pago de una sesión de Checkout, que es la referencia que Stripe usa en sus avisos.
     */
    Optional<Pago> findByReferenciaStripe(String referenciaStripe);

    /**
     * La reserva a la que pertenece una sesión de Checkout, sin cargar el pago. Sirve para bloquear la reserva
     * antes de leer el pago, y así leerlo ya actualizado.
     */
    @Query("SELECT p.reserva.idReserva FROM Pago p WHERE p.referenciaStripe = :referenciaStripe")
    Optional<Long> idReservaDeReferencia(@Param("referenciaStripe") String referenciaStripe);

    /**
     * El pago de la reserva con una sesión abierta, si lo hay. Una reserva tiene a lo sumo uno (restricción de la
     * base).
     */
    Optional<Pago> findByReservaIdReservaAndEstadoIn(Long idReserva, Collection<EstadoPago> estados);

    /**
     * El pago más reciente de la reserva.
     */
    Optional<Pago> findFirstByReservaIdReservaOrderByIdPagoDesc(Long idReserva);

    /**
     * Sesiones de pago que ya se abrieron para la reserva, sin importar cómo terminaron.
     */
    long countByReservaIdReserva(Long idReserva);

    /**
     * Anota que un aviso de Stripe ya se aplicó. Devuelve 0 si ese aviso ya estaba anotado: Stripe lo reenvía
     * cuando no recibe confirmación a tiempo, y un reenvío no debe repetir su efecto. La anotación y el efecto
     * van en la misma transacción: si el efecto falla, la anotación se revierte y Stripe reintenta.
     */
    @Modifying
    @Query(value = """
            INSERT INTO evento_pago_procesado (id_evento, tipo, id_pago)
            VALUES (:idEvento, :tipo, :idPago)
            ON CONFLICT (id_evento) DO NOTHING
            """, nativeQuery = true)
    int registrarEvento(@Param("idEvento") String idEvento, @Param("tipo") String tipo,
                        @Param("idPago") Long idPago);
}
