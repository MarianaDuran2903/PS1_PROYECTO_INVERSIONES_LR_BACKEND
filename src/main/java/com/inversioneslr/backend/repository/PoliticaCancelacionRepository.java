package com.inversioneslr.backend.repository;

import com.inversioneslr.backend.model.PoliticaCancelacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PoliticaCancelacionRepository extends JpaRepository<PoliticaCancelacion, Long> {

    Optional<PoliticaCancelacion> findByNombre(String nombre);
}
