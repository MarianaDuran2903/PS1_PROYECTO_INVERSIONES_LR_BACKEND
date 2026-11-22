package com.inversioneslr.backend.repository;

import com.inversioneslr.backend.model.Publicacion;
import com.inversioneslr.backend.model.enums.EstadoPublicacion;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

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

    @EntityGraph(attributePaths = {"ciudad", "tipo", "politica"})
    Optional<Publicacion> findByIdPublicacionAndEstado(Long idPublicacion, EstadoPublicacion estado);

    /**
     * La publicación en el estado dado, con su fila bloqueada hasta el final de la transacción. Reservar entra
     * de a una persona por publicación: quien llega después espera y ya ve el calendario que dejó el anterior.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Publicacion p WHERE p.idPublicacion = :idPublicacion AND p.estado = :estado")
    Optional<Publicacion> bloquearPorIdYEstado(@Param("idPublicacion") Long idPublicacion,
                                               @Param("estado") EstadoPublicacion estado);

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
              AND (CAST(:idTipo AS bigint) IS NULL
                   OR p.id_tipo = CAST(:idTipo AS bigint))
              AND (CAST(:precioMinimo AS numeric) IS NULL
                   OR p.precio_noche >= CAST(:precioMinimo AS numeric))
              AND (CAST(:precioMaximo AS numeric) IS NULL
                   OR p.precio_noche <= CAST(:precioMaximo AS numeric))
              AND NOT EXISTS (
                 SELECT 1
                    FROM unnest(string_to_array(CAST(:idsServicios AS text), ',')) AS requerido(id_servicio)
                    WHERE NOT EXISTS (
                        SELECT 1
                           FROM publicacion_servicio ps
                           WHERE ps.id_publicacion = p.id_publicacion
                              AND ps.id_servicio = CAST(requerido.id_servicio AS bigint)
                              )
                            )
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
                      AND r.estado IN ('PENDIENTE_PAGO', 'CONFIRMADA', 'REVISION_MANUAL')
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
            @Param("idTipo") Long idTipo,
            @Param("precioMinimo") BigDecimal precioMinimo,
            @Param("precioMaximo") BigDecimal precioMaximo,
            @Param("idsServicios") String idsServicios,
            Pageable pageable);

    @Query(value = """
            SELECT s.id_servicio AS id, s.nombre AS nombre
            FROM publicacion_servicio ps
            JOIN servicio s ON s.id_servicio = ps.id_servicio
            WHERE ps.id_publicacion = :idPublicacion
            ORDER BY s.nombre, s.id_servicio
            """, nativeQuery = true)
    List<ServicioLectura> listarServicios(@Param("idPublicacion") Long idPublicacion);

    @Query(value = """
            SELECT orden AS orden, ruta AS ruta
            FROM imagen_publicacion
            WHERE id_publicacion = :idPublicacion
            ORDER BY orden
            """, nativeQuery = true)
    List<ImagenLectura> listarImagenes(@Param("idPublicacion") Long idPublicacion);

    @Query(value = """
            SELECT ps.id_publicacion AS "idPublicacion",
                   s.id_servicio AS id,
                   s.nombre AS nombre
            FROM publicacion_servicio ps
            JOIN servicio s ON s.id_servicio = ps.id_servicio
            WHERE ps.id_publicacion IN (:idsPublicaciones)
            ORDER BY ps.id_publicacion, s.nombre, s.id_servicio
            """, nativeQuery = true)
    List<ServicioPublicacionLectura> listarServiciosDePublicaciones(
            @Param("idsPublicaciones") List<Long> idsPublicaciones);

    interface ServicioLectura {
        Long getId();
        String getNombre();
    }

    interface ImagenLectura {
        Integer getOrden();
        String getRuta();
    }

    interface ServicioPublicacionLectura {
        Long getIdPublicacion();
        Long getId();
        String getNombre();
    }
}
