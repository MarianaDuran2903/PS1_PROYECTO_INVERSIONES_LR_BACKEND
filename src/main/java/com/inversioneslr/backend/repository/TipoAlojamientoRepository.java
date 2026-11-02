package com.inversioneslr.backend.repository;

import com.inversioneslr.backend.model.TipoAlojamiento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TipoAlojamientoRepository extends JpaRepository<TipoAlojamiento, Long> {

    List<TipoAlojamiento> findAllByOrderByNombreAsc();
}
