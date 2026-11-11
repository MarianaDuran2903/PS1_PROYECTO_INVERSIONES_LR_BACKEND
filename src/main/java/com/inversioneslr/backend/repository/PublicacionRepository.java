package com.inversioneslr.backend.repository;

import com.inversioneslr.backend.model.Publicacion;
import com.inversioneslr.backend.model.enums.EstadoPublicacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
