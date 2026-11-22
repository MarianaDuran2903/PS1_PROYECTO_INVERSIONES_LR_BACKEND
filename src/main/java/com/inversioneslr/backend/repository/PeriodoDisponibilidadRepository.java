package com.inversioneslr.backend.repository;

import com.inversioneslr.backend.model.PeriodoDisponibilidad;
import com.inversioneslr.backend.model.enums.TipoPeriodo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PeriodoDisponibilidadRepository extends JpaRepository<PeriodoDisponibilidad, Long> {

    /** Todos los tramos del calendario de una publicación, ordenados cronológicamente. */
    @Query("""
            SELECT p FROM PeriodoDisponibilidad p
            WHERE p.publicacion.idPublicacion = :idPublicacion
            ORDER BY p.fechaInicio ASC, p.fechaFin ASC
            """)
    List<PeriodoDisponibilidad> buscarCalendarioDePublicacion(
            @Param("idPublicacion") Long idPublicacion);

    /**
     * Tramos de un tipo que tocan alguna noche de [llegada, salida), de la fecha más temprana a la más tardía.
     */
    @Query("""
            SELECT p FROM PeriodoDisponibilidad p
            WHERE p.publicacion.idPublicacion = :idPublicacion
              AND p.tipo = :tipo
              AND p.fechaInicio < :salida
              AND p.fechaFin > :llegada
            ORDER BY p.fechaInicio
            """)
    List<PeriodoDisponibilidad> buscarQueCruzan(@Param("idPublicacion") Long idPublicacion,
                                                @Param("tipo") TipoPeriodo tipo,
                                                @Param("llegada") LocalDate llegada,
                                                @Param("salida") LocalDate salida);

    /**
     * El tramo RESERVADO que una reserva ocupa, si lo tiene.
     */
    Optional<PeriodoDisponibilidad> findByReservaIdReserva(Long idReserva);

    /**
     * Pospone hasta el commit la verificación de que los tramos no se superponen, para poder cambiar un tramo
     * por otros sin que importe el orden en que se escriben. La base lo permite porque ex_periodo_sin_solape es
     * DEFERRABLE (V1). Vale solo para la transacción actual.
     */
    @Modifying
    @Query(value = "SET CONSTRAINTS ex_periodo_sin_solape DEFERRED", nativeQuery = true)
    void diferirVerificacionDeSolape();
}
