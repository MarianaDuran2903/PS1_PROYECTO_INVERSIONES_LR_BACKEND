package com.inversioneslr.backend.mapper;

import com.inversioneslr.backend.dto.response.CatalogoItemResponse;
import com.inversioneslr.backend.dto.response.PublicacionResponse;
import com.inversioneslr.backend.dto.response.PublicacionResumenResponse;
import com.inversioneslr.backend.model.Ciudad;
import com.inversioneslr.backend.model.PoliticaCancelacion;
import com.inversioneslr.backend.model.Publicacion;
import com.inversioneslr.backend.model.TipoAlojamiento;
import org.springframework.stereotype.Component;

@Component
public class PublicacionMapper {

    public PublicacionResponse toResponse(Publicacion publicacion) {
        return new PublicacionResponse(
                publicacion.getIdPublicacion(),
                publicacion.getAnfitrion().getIdUsuario(),
                publicacion.getEstado(),
                publicacion.getTitulo(),
                publicacion.getDescripcion(),
                publicacion.getDireccionTextual(),
                publicacion.getBarrioZona(),
                toItem(publicacion.getCiudad()),
                toItem(publicacion.getTipo()),
                toItem(publicacion.getPolitica()),
                publicacion.getCapacidad(),
                publicacion.getPrecioNoche(),
                publicacion.getTarifaLimpieza(),
                publicacion.getReglas());
    }

    public PublicacionResumenResponse toResumen(Publicacion publicacion) {
        return new PublicacionResumenResponse(
                publicacion.getIdPublicacion(),
                publicacion.getTitulo(),
                publicacion.getEstado(),
                toItem(publicacion.getCiudad()),
                toItem(publicacion.getTipo()),
                publicacion.getCapacidad(),
                publicacion.getPrecioNoche());
    }

    public CatalogoItemResponse toItem(Ciudad ciudad) {
        return new CatalogoItemResponse(ciudad.getIdCiudad(), ciudad.getNombre());
    }

    public CatalogoItemResponse toItem(TipoAlojamiento tipo) {
        return new CatalogoItemResponse(tipo.getIdTipo(), tipo.getNombre());
    }

    public CatalogoItemResponse toItem(PoliticaCancelacion politica) {
        return new CatalogoItemResponse(politica.getIdPolitica(), politica.getNombre());
    }
}
