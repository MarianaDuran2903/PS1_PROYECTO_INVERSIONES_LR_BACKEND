package com.inversioneslr.backend.repository;

import com.inversioneslr.backend.model.Ciudad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CiudadRepository extends JpaRepository<Ciudad, Long> {

    List<Ciudad> findAllByOrderByNombreAsc();
}
