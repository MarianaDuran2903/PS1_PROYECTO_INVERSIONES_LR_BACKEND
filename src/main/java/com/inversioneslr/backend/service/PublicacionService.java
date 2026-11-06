package com.inversioneslr.backend.service;

import com.inversioneslr.backend.dto.request.ActualizarPublicacionRequest;
import com.inversioneslr.backend.dto.request.CrearPublicacionRequest;
import com.inversioneslr.backend.dto.response.PaginaResponse;
import com.inversioneslr.backend.dto.response.PublicacionResponse;
import com.inversioneslr.backend.dto.response.PublicacionResumenResponse;
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

    /**
     * Lista las publicaciones del Anfitrión autenticado, de la más reciente a la más antigua. Si no tiene
     * ninguna, la página viene vacía y con un mensaje que lo informa.
     *
     * @param pagina número de página, empezando en 0
     * @param tamano cantidad de elementos por página
     */
    PaginaResponse<PublicacionResumenResponse> listarPropias(UsuarioAutenticado anfitrion, int pagina, int tamano);

    /**
     * Devuelve una publicación del Anfitrión autenticado.
     *
     * @throws com.inversioneslr.backend.exception.PublicacionNoEncontradaException si no existe
     * @throws com.inversioneslr.backend.exception.PublicacionAjenaException si pertenece a otro Anfitrión
     */
    PublicacionResponse obtenerPropia(Long idPublicacion, UsuarioAutenticado anfitrion);

    /**
     * Reemplaza la información editable de una publicación propia en BORRADOR o PAUSADA, sin cambiar su estado.
     * Si algo falla, la publicación queda como estaba.
     *
     * @throws com.inversioneslr.backend.exception.PublicacionNoEncontradaException si no existe
     * @throws com.inversioneslr.backend.exception.PublicacionAjenaException si pertenece a otro Anfitrión
     * @throws com.inversioneslr.backend.exception.EstadoNoEditableException si el estado no permite editarla
     * @throws com.inversioneslr.backend.exception.ReferenciaInvalidaException si la ciudad o el tipo no existen
     */
    PublicacionResponse actualizar(Long idPublicacion, ActualizarPublicacionRequest request,
                                   UsuarioAutenticado anfitrion);
}
