package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.dto.response.PaginaResponse;
import com.inversioneslr.backend.dto.response.PublicacionResumenResponse;
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
    public PaginaResponse<PublicacionResumenResponse> listarDisponibles(int pagina, int tamano) {
        // Solo las ACTIVAS se ofrecen: una pausada o bloqueada nunca llega a la lista.
        Page<Publicacion> resultado = publicacionRepository.findByEstado(EstadoPublicacion.ACTIVA,
                PageRequest.of(pagina, tamano, ORDEN_MAS_RECIENTES));
        log.info("Consulta pública de alojamientos {} {} {} {}",
                kv("accion", "CONSULTAR_ALOJAMIENTOS_DISPONIBLES"),
                kv("entidad", ENTIDAD_PUBLICACION),
                kv("resultado", "EXITOSO"),
                kv("totalElementos", resultado.getTotalElements()));
        return PaginaResponse.de(resultado, publicacionMapper::toResumen, MENSAJE_SIN_ALOJAMIENTOS);
    }
}
