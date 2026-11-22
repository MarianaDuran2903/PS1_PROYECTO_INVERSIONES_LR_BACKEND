package com.inversioneslr.backend.repository;

import com.inversioneslr.backend.model.PeriodoDisponibilidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PeriodoDisponibilidadRepository
        extends JpaRepository<PeriodoDisponibilidad, Long> {

    @Query("""
            SELECT periodo
            FROM PeriodoDisponibilidad periodo
            WHERE periodo.publicacion.idPublicacion = :idPublicacion
            ORDER BY periodo.fechaInicio ASC, periodo.fechaFin ASC
            """)
    List<PeriodoDisponibilidad> buscarCalendarioDePublicacion(
            @Param("idPublicacion") Long idPublicacion);
}