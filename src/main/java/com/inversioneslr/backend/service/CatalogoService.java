package com.inversioneslr.backend.service;

import com.inversioneslr.backend.dto.response.CatalogoItemResponse;

import java.util.List;

/**
 * Catálogos de solo lectura que usa el formulario de publicaciones.
 */
public interface CatalogoService {

    List<CatalogoItemResponse> listarCiudades();

    List<CatalogoItemResponse> listarTiposAlojamiento();
}
