package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.dto.request.FiltroAlojamientosRequest;
import com.inversioneslr.backend.dto.response.AlojamientoDetalleResponse;
import com.inversioneslr.backend.dto.response.CatalogoItemResponse;
import com.inversioneslr.backend.dto.response.PaginaResponse;
import com.inversioneslr.backend.dto.response.AlojamientoResumenResponse;
import com.inversioneslr.backend.exception.AlojamientoNoDisponibleException;
import com.inversioneslr.backend.mapper.PublicacionMapper;
import com.inversioneslr.backend.model.Publicacion;
import com.inversioneslr.backend.model.enums.EstadoPublicacion;
import com.inversioneslr.backend.repository.PublicacionRepository;
import com.inversioneslr.backend.service.AlojamientoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
public class AlojamientoServiceImpl implements AlojamientoService {

    static final String ENTIDAD_PUBLICACION = "Publicacion";
    static final String MENSAJE_SIN_ALOJAMIENTOS = "No hay alojamientos disponibles por el momento";
    /** La tabla no guarda la fecha de creación; el identificador crece con cada publicación nueva. */
    private static final Sort ORDEN_MAS_RECIENTES = Sort.by(Sort.Direction.DESC, "idPublicacion");

    private static final Logger log = LoggerFactory.getLogger(AlojamientoServiceImpl.class);

    private final PublicacionRepository publicacionRepository;
    private final PublicacionMapper publicacionMapper;

    public AlojamientoServiceImpl(PublicacionRepository publicacionRepository, PublicacionMapper publicacionMapper) {
        this.publicacionRepository = publicacionRepository;
        this.publicacionMapper = publicacionMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public AlojamientoDetalleResponse obtenerDetalle(Long idPublicacion) {
        Publicacion publicacion = publicacionRepository
                .findByIdPublicacionAndEstado(
                        idPublicacion,
                        EstadoPublicacion.ACTIVA)
                .orElseThrow(AlojamientoNoDisponibleException::new);

        List<CatalogoItemResponse> servicios =
                publicacionRepository.listarServicios(idPublicacion)
                        .stream()
                        .map(servicio -> new CatalogoItemResponse(
                                servicio.getId(),
                                servicio.getNombre()))
                        .toList();

        List<AlojamientoDetalleResponse.ImagenResponse> imagenes =
                publicacionRepository.listarImagenes(idPublicacion)
                        .stream()
                        .map(imagen ->
                                new AlojamientoDetalleResponse.ImagenResponse(
                                        imagen.getOrden(),
                                        imagen.getRuta()))
                        .toList();

        log.info("Consulta pública de detalle de alojamiento {} {} {} {}",
                kv("accion", "CONSULTAR_DETALLE_ALOJAMIENTO"),
                kv("entidad", ENTIDAD_PUBLICACION),
                kv("idPublicacion", idPublicacion),
                kv("resultado", "EXITOSO"));

        return publicacionMapper.toDetallePublico(
                publicacion,
                servicios,
                imagenes);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<AlojamientoResumenResponse> listarDisponibles(int pagina, int tamano) {
        return listarDisponibles(
                pagina, tamano,
                new FiltroAlojamientosRequest(
                        null, null, null, null,
                        null, null, null, null));
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<AlojamientoResumenResponse> listarDisponibles(int pagina, int tamano, FiltroAlojamientosRequest filtros) {

        String idsServicios = filtros.idsServicios() == null
                || filtros.idsServicios().isEmpty()
                ? null
                : filtros.idsServicios().stream()
                .distinct()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        boolean sinFiltros = filtros.idCiudad() == null
                && filtros.fechaLlegada() == null
                && filtros.fechaSalida() == null
                && filtros.huespedes() == null
                && filtros.idTipo() == null
                && filtros.precioMinimo() == null
                && filtros.precioMaximo() == null
                && idsServicios == null;

        Page<Publicacion> resultado;

        if (sinFiltros) {
            resultado = publicacionRepository.findByEstado(
                    EstadoPublicacion.ACTIVA,
                    PageRequest.of(pagina, tamano, ORDEN_MAS_RECIENTES));
        } else {
            resultado = publicacionRepository.buscarDisponibles(
                    filtros.idCiudad(),
                    filtros.fechaLlegada(),
                    filtros.fechaSalida(),
                    filtros.huespedes(),
                    filtros.idTipo(),
                    filtros.precioMinimo(),
                    filtros.precioMaximo(),
                    idsServicios,
                    PageRequest.of(pagina, tamano));
        }

        log.info("Consulta pública de alojamientos {} {} {} {}",
                kv("accion", "CONSULTAR_ALOJAMIENTOS_DISPONIBLES"),
                kv("entidad", ENTIDAD_PUBLICACION),
                kv("resultado", "EXITOSO"),
                kv("totalElementos", resultado.getTotalElements()));

        String mensajeVacio = sinFiltros
                ? MENSAJE_SIN_ALOJAMIENTOS
                : "No hay alojamientos que coincidan con los filtros seleccionados";

        Map<Long, List<CatalogoItemResponse>> serviciosPorPublicacion = obtenerServiciosDePagina(resultado);

        return PaginaResponse.de(
                resultado,
                publicacion -> publicacionMapper.toResumenPublico(publicacion, serviciosPorPublicacion.getOrDefault(publicacion.getIdPublicacion(), List.of())),
                mensajeVacio);
    }

    private Map<Long, List<CatalogoItemResponse>> obtenerServiciosDePagina(Page<Publicacion> pagina) {
        if (pagina.isEmpty()) {
            return Map.of();
        }

        List<Long> idsPublicaciones = pagina.getContent()
                .stream()
                .map(Publicacion::getIdPublicacion)
                .toList();

        return publicacionRepository
                .listarServiciosDePublicaciones(idsPublicaciones)
                .stream()
                .collect(Collectors.groupingBy(
                        PublicacionRepository.ServicioPublicacionLectura::getIdPublicacion,
                        Collectors.mapping(servicio -> new CatalogoItemResponse(servicio.getId(),servicio.getNombre()), Collectors.toList())));
    }
}
