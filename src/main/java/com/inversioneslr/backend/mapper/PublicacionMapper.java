package com.inversioneslr.backend.mapper;

import com.inversioneslr.backend.dto.response.*;
import com.inversioneslr.backend.model.Ciudad;
import com.inversioneslr.backend.model.PoliticaCancelacion;
import com.inversioneslr.backend.model.Publicacion;
import com.inversioneslr.backend.model.TipoAlojamiento;
import com.inversioneslr.backend.model.Servicio;
import org.springframework.stereotype.Component;

import java.util.List;

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
                publicacion.getReglas(),
                publicacion.getMotivoBloqueo());
    }

    public AlojamientoDetalleResponse toDetallePublico(
            Publicacion publicacion,
            List<CatalogoItemResponse> servicios,
            List<AlojamientoDetalleResponse.ImagenResponse> imagenes) {

        PoliticaCancelacion politica = publicacion.getPolitica();

        return new AlojamientoDetalleResponse(
                publicacion.getIdPublicacion(),
                publicacion.getTitulo(),
                publicacion.getDescripcion(),
                publicacion.getEstado(),
                toItem(publicacion.getCiudad()),
                publicacion.getBarrioZona(),
                toItem(publicacion.getTipo()),
                publicacion.getCapacidad(),
                publicacion.getPrecioNoche(),
                publicacion.getReglas(),
                new AlojamientoDetalleResponse.PoliticaResponse(
                        politica.getIdPolitica(),
                        politica.getNombre(),
                        politica.getHorasAnticipacionMin(),
                        politica.getPorcentajeDevolucion()),
                List.copyOf(servicios),
                List.copyOf(imagenes));
    }

    public AlojamientoResumenResponse toResumenPublico(
            Publicacion publicacion,
            List<CatalogoItemResponse> servicios) {
        return new AlojamientoResumenResponse(
                publicacion.getIdPublicacion(),
                publicacion.getTitulo(),
                publicacion.getEstado(),
                toItem(publicacion.getCiudad()),
                toItem(publicacion.getTipo()),
                publicacion.getCapacidad(),
                publicacion.getPrecioNoche(),
                servicios);
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

    public CatalogoItemResponse toItem(Servicio servicio) {
        return new CatalogoItemResponse(
                servicio.getIdServicio(),
                servicio.getNombre());
    }
}
