package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.dto.response.CatalogoItemResponse;
import com.inversioneslr.backend.mapper.PublicacionMapper;
import com.inversioneslr.backend.repository.CiudadRepository;
import com.inversioneslr.backend.repository.TipoAlojamientoRepository;
import com.inversioneslr.backend.service.CatalogoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CatalogoServiceImpl implements CatalogoService {

    private final CiudadRepository ciudadRepository;
    private final TipoAlojamientoRepository tipoAlojamientoRepository;
    private final PublicacionMapper publicacionMapper;

    public CatalogoServiceImpl(CiudadRepository ciudadRepository, TipoAlojamientoRepository tipoAlojamientoRepository,
                               PublicacionMapper publicacionMapper) {
        this.ciudadRepository = ciudadRepository;
        this.tipoAlojamientoRepository = tipoAlojamientoRepository;
        this.publicacionMapper = publicacionMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogoItemResponse> listarCiudades() {
        return ciudadRepository.findAllByOrderByNombreAsc().stream().map(publicacionMapper::toItem).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogoItemResponse> listarTiposAlojamiento() {
        return tipoAlojamientoRepository.findAllByOrderByNombreAsc().stream().map(publicacionMapper::toItem).toList();
    }
}
