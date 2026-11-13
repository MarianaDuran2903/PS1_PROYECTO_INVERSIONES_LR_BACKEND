package com.inversioneslr.backend.service;

import com.inversioneslr.backend.dto.response.PaginaResponse;
import com.inversioneslr.backend.dto.response.PublicacionResumenResponse;

/**
 * Consulta pública de los alojamientos que se ofrecen a Visitantes y Huéspedes.
 */
public interface AlojamientoService {

    /**
     * Lista los alojamientos disponibles para nuevas reservas, del más reciente al más antiguo. Solo aparecen
     * las publicaciones ACTIVAS: borradores, en revisión, pausadas y bloqueadas no se presentan. Si no hay
     * ninguna, la página viene vacía y con un mensaje que lo informa.
     *
     * @param pagina número de página, empezando en 0
     * @param tamano cantidad de elementos por página
     */
    PaginaResponse<PublicacionResumenResponse> listarDisponibles(int pagina, int tamano);
}
