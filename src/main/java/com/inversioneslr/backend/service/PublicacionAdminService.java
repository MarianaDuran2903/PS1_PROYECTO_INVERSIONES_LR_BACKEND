package com.inversioneslr.backend.service;

import com.inversioneslr.backend.dto.request.BloqueoPublicacionRequest;
import com.inversioneslr.backend.dto.request.EdicionAdministrativaRequest;
import com.inversioneslr.backend.dto.request.ReactivacionPublicacionRequest;
import com.inversioneslr.backend.dto.request.RechazoPublicacionRequest;
import com.inversioneslr.backend.dto.response.PaginaResponse;
import com.inversioneslr.backend.dto.response.PublicacionResponse;
import com.inversioneslr.backend.model.enums.EstadoPublicacion;
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

    /**
     * Lista, de la más antigua a la más reciente y paginadas, las publicaciones en PENDIENTE_REVISION con la
     * información completa para decidir sobre cada una. Si no hay ninguna, la lista viene vacía y el campo
     * mensaje lo informa.
     */
    PaginaResponse<PublicacionResponse> listarPendientes(UsuarioAutenticado administrador, int pagina, int tamano);

    /**
     * Lista, de la más reciente a la más antigua y paginadas, las publicaciones de cualquier Anfitrión con la
     * información completa, incluido el motivo del bloqueo. Con un estado, devuelve solo las de ese estado; con
     * estado nulo, todas. Si no hay ninguna, la lista viene vacía y el campo mensaje lo informa.
     */
    PaginaResponse<PublicacionResponse> listar(UsuarioAutenticado administrador, EstadoPublicacion estado,
                                               int pagina, int tamano);

    /**
     * Aprueba una publicación pendiente: pasa a ACTIVA y la decisión queda registrada con el Administrador.
     *
     * @throws com.inversioneslr.backend.exception.PublicacionNoEncontradaException si no existe
     * @throws com.inversioneslr.backend.exception.TransicionNoPermitidaException si no está en
     *         PENDIENTE_REVISION
     * @throws com.inversioneslr.backend.exception.InformacionMinimaIncompletaException si le falta información
     *         mínima
     */
    PublicacionResponse aprobar(Long idPublicacion, UsuarioAutenticado administrador);

    /**
     * Rechaza una publicación pendiente: vuelve a BORRADOR para que el Anfitrión la corrija, y el motivo queda
     * registrado con el Administrador.
     *
     * @throws com.inversioneslr.backend.exception.PublicacionNoEncontradaException si no existe
     * @throws com.inversioneslr.backend.exception.TransicionNoPermitidaException si no está en
     *         PENDIENTE_REVISION
     */
    PublicacionResponse rechazar(Long idPublicacion, RechazoPublicacionRequest request,
                                 UsuarioAutenticado administrador);

    /**
     * Bloquea una publicación engañosa o incumplidora: pasa a BLOQUEADA, deja de ofrecerse en las búsquedas, el
     * motivo se guarda en la publicación y queda registrado con el Administrador y la fecha.
     *
     * @throws com.inversioneslr.backend.exception.PublicacionNoEncontradaException si no existe
     * @throws com.inversioneslr.backend.exception.TransicionNoPermitidaException si es un borrador o ya está
     *         bloqueada
     */
    PublicacionResponse bloquear(Long idPublicacion, BloqueoPublicacionRequest request,
                                 UsuarioAutenticado administrador);

    /**
     * Reactiva una publicación pausada (tras revisar el ajuste del Anfitrión) o bloqueada (según el motivo del
     * bloqueo): vuelve a ACTIVA, el motivo del bloqueo se limpia y la decisión queda registrada con el
     * Administrador, el estado de origen y el motivo.
     *
     * @throws com.inversioneslr.backend.exception.PublicacionNoEncontradaException si no existe
     * @throws com.inversioneslr.backend.exception.TransicionNoPermitidaException si no está PAUSADA ni BLOQUEADA
     * @throws com.inversioneslr.backend.exception.InformacionMinimaIncompletaException si le falta información
     *         mínima para estar activa
     */
    PublicacionResponse reactivar(Long idPublicacion, ReactivacionPublicacionRequest request,
                                  UsuarioAutenticado administrador);
}
