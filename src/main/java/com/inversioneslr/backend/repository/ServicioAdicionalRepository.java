package com.inversioneslr.backend.repository;

import com.inversioneslr.backend.model.ServicioAdicional;
import com.inversioneslr.backend.model.ServicioAdicionalId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ServicioAdicionalRepository extends JpaRepository<ServicioAdicional, ServicioAdicionalId> {

    /**
     * Los servicios de la publicación que tienen alguno de esos nombres. Un nombre que la publicación no ofrece
     * simplemente no aparece en el resultado.
     */
    List<ServicioAdicional> findByIdIdPublicacionAndIdNombreIn(Long idPublicacion, Collection<String> nombres);
}
