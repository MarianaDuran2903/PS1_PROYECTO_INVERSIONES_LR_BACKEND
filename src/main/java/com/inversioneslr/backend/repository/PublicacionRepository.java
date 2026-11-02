package com.inversioneslr.backend.repository;

import com.inversioneslr.backend.model.Publicacion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicacionRepository extends JpaRepository<Publicacion, Long> {
}
