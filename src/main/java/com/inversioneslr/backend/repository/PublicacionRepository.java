package com.inversioneslr.backend.repository;

import com.inversioneslr.backend.model.Publicacion;
import com.inversioneslr.backend.model.enums.EstadoPublicacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface PublicacionRepository extends JpaRepository<Publicacion, Long> {

    /**
     * Publicaciones de un Anfitrión, con la ciudad y el tipo cargados en la misma consulta.
     */
    @EntityGraph(attributePaths = {"ciudad", "tipo"})
    Page<Publicacion> findByAnfitrionIdUsuario(Long idAnfitrion, Pageable pageable);

    /**
     * Publicaciones en un estado dado, con ciudad, tipo y política cargados en la misma consulta para
     * devolver la información completa sin consultas adicionales por fila.
     */
    @EntityGraph(attributePaths = {"ciudad", "tipo", "politica"})
    Page<Publicacion> findByEstado(EstadoPublicacion estado, Pageable pageable);

    /**
     * Todas las publicaciones, de cualquier estado y Anfitrión, con ciudad, tipo y política cargados en la
     * misma consulta.
     */
    @EntityGraph(attributePaths = {"ciudad", "tipo", "politica"})
    Page<Publicacion> findAllBy(Pageable pageable);

    /**
     * La suma de noches disponibles es válida porque la base impide periodos
     * superpuestos por publicación. Permite unir periodos contiguos, pero no huecos.
     */
    String FILTRO_BUSQUEDA_PUBLICA = """
            FROM publicacion p
            WHERE p.estado = 'ACTIVA'
              AND (CAST(:idCiudad AS bigint) IS NULL
                   OR p.id_ciudad = CAST(:idCiudad AS bigint))
              AND (CAST(:huespedes AS integer) IS NULL
                   OR p.capacidad >= CAST(:huespedes AS integer))
              AND (
                CAST(:fechaLlegada AS date) IS NULL
                OR (
                  COALESCE((
                    SELECT SUM(
                      LEAST(d.fecha_fin, CAST(:fechaSalida AS date))
                      - GREATEST(d.fecha_inicio, CAST(:fechaLlegada AS date))
                    )
                    FROM periodo_disponibilidad d
                    WHERE d.id_publicacion = p.id_publicacion
                      AND d.tipo = 'DISPONIBLE'
                      AND d.fecha_inicio < CAST(:fechaSalida AS date)
                      AND d.fecha_fin > CAST(:fechaLlegada AS date)
                  ), 0) = (
                    CAST(:fechaSalida AS date) - CAST(:fechaLlegada AS date)
                  )
                  AND NOT EXISTS (
                    SELECT 1
                    FROM reserva r
                    WHERE r.id_publicacion = p.id_publicacion
                      AND r.estado IN ('CONFIRMADA', 'REVISION_MANUAL')
                      AND r.fecha_llegada < CAST(:fechaSalida AS date)
                      AND r.fecha_salida > CAST(:fechaLlegada AS date)
                  )
                )
              )
            """;

    @Query(
            value = "SELECT p.* " + FILTRO_BUSQUEDA_PUBLICA
                    + " ORDER BY p.id_publicacion DESC",
            countQuery = "SELECT COUNT(*) " + FILTRO_BUSQUEDA_PUBLICA,
            nativeQuery = true
    )
    Page<Publicacion> buscarDisponibles(
            @Param("idCiudad") Long idCiudad,
            @Param("fechaLlegada") LocalDate fechaLlegada,
            @Param("fechaSalida") LocalDate fechaSalida,
            @Param("huespedes") Integer huespedes,
            Pageable pageable);
}
