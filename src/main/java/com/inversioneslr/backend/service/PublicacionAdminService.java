package com.inversioneslr.backend.service;

import com.inversioneslr.backend.dto.request.EdicionAdministrativaRequest;
import com.inversioneslr.backend.dto.response.PublicacionResponse;
import com.inversioneslr.backend.security.UsuarioAutenticado;

/**
 * Intervención del Administrador sobre publicaciones de cualquier Anfitrión.
 */
public interface PublicacionAdminService {

    /**
     * Devuelve cualquier publicación, para revisarla o precargar el formulario de intervención.
     *
     * @throws com.inversioneslr.backend.exception.PublicacionNoEncontradaException si no existe
     */
    PublicacionResponse obtener(Long idPublicacion);

    /**
     * Reemplaza la información editable de una publicación sin cambiar su estado ni su propietario, y registra
     * quién intervino, cuándo y por qué. Si algo falla, la publicación queda como estaba.
     *
     * @throws com.inversioneslr.backend.exception.PublicacionNoEncontradaException si no existe
     * @throws com.inversioneslr.backend.exception.EstadoNoEditableException si está ACTIVA
     * @throws com.inversioneslr.backend.exception.InformacionIncompletaException si quedaría sin reglas estando
     *         en revisión
     * @throws com.inversioneslr.backend.exception.ReferenciaInvalidaException si la ciudad o el tipo no existen
     */
    PublicacionResponse intervenir(Long idPublicacion, EdicionAdministrativaRequest request,
                                   UsuarioAutenticado administrador);
}
