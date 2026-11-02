package com.inversioneslr.backend.service;

import com.inversioneslr.backend.dto.request.CrearPublicacionRequest;
import com.inversioneslr.backend.dto.response.PublicacionResponse;
import com.inversioneslr.backend.security.UsuarioAutenticado;

/**
 * Gestión de publicaciones por el Anfitrión.
 */
public interface PublicacionService {

    /**
     * Crea una publicación en BORRADOR asociada al Anfitrión autenticado.
     *
     * @throws com.inversioneslr.backend.exception.ReferenciaInvalidaException si la ciudad o el tipo no existen
     */
    PublicacionResponse crear(CrearPublicacionRequest request, UsuarioAutenticado anfitrion);
}
