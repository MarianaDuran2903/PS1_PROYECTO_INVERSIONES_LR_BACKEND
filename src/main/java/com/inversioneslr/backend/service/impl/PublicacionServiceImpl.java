package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.dto.request.CrearPublicacionRequest;
import com.inversioneslr.backend.dto.response.PublicacionResponse;
import com.inversioneslr.backend.exception.ReferenciaInvalidaException;
import com.inversioneslr.backend.mapper.PublicacionMapper;
import com.inversioneslr.backend.model.Ciudad;
import com.inversioneslr.backend.model.DatosPublicacion;
import com.inversioneslr.backend.model.PoliticaCancelacion;
import com.inversioneslr.backend.model.Publicacion;
import com.inversioneslr.backend.model.TipoAlojamiento;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.AccionAuditoria;
import com.inversioneslr.backend.repository.CiudadRepository;
import com.inversioneslr.backend.repository.PoliticaCancelacionRepository;
import com.inversioneslr.backend.repository.PublicacionRepository;
import com.inversioneslr.backend.repository.TipoAlojamientoRepository;
import com.inversioneslr.backend.repository.UsuarioRepository;
import com.inversioneslr.backend.security.UsuarioAutenticado;
import com.inversioneslr.backend.service.AuditoriaService;
import com.inversioneslr.backend.service.PublicacionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicacionServiceImpl implements PublicacionService {

    static final String ENTIDAD_PUBLICACION = "Publicacion";
    /** Política de cancelación que se asigna a toda publicación nueva. */
    static final String POLITICA_POR_DEFECTO = "Flexible";

    private final PublicacionRepository publicacionRepository;
    private final CiudadRepository ciudadRepository;
    private final TipoAlojamientoRepository tipoAlojamientoRepository;
    private final PoliticaCancelacionRepository politicaCancelacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;
    private final PublicacionMapper publicacionMapper;

    public PublicacionServiceImpl(PublicacionRepository publicacionRepository, CiudadRepository ciudadRepository,
                                  TipoAlojamientoRepository tipoAlojamientoRepository,
                                  PoliticaCancelacionRepository politicaCancelacionRepository,
                                  UsuarioRepository usuarioRepository, AuditoriaService auditoriaService,
                                  PublicacionMapper publicacionMapper) {
        this.publicacionRepository = publicacionRepository;
        this.ciudadRepository = ciudadRepository;
        this.tipoAlojamientoRepository = tipoAlojamientoRepository;
        this.politicaCancelacionRepository = politicaCancelacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.auditoriaService = auditoriaService;
        this.publicacionMapper = publicacionMapper;
    }

    @Override
    @Transactional
    public PublicacionResponse crear(CrearPublicacionRequest request, UsuarioAutenticado autenticado) {
        Ciudad ciudad = ciudadRepository.findById(request.idCiudad())
                .orElseThrow(() -> new ReferenciaInvalidaException("idCiudad", "La ciudad no existe"));
        TipoAlojamiento tipo = tipoAlojamientoRepository.findById(request.idTipo())
                .orElseThrow(() -> new ReferenciaInvalidaException("idTipo", "El tipo de alojamiento no existe"));
        PoliticaCancelacion politica = politicaCancelacionRepository.findByNombre(POLITICA_POR_DEFECTO)
                .orElseThrow(() -> new IllegalStateException(
                        "No existe la política de cancelación " + POLITICA_POR_DEFECTO));

        // El propietario es siempre el Anfitrión del token, nunca un dato del cuerpo.
        Usuario anfitrion = usuarioRepository.getReferenceById(autenticado.idUsuario());
        DatosPublicacion datos = DatosPublicacion.normalizados(request.titulo(), request.descripcion(),
                request.direccionTextual(), request.barrioZona(), request.capacidad(), request.precioNoche());

        Publicacion guardada = publicacionRepository.save(new Publicacion(anfitrion, politica, datos, ciudad, tipo));
        auditoriaService.registrarExito(anfitrion, AccionAuditoria.CREAR_PUBLICACION, ENTIDAD_PUBLICACION,
                String.valueOf(guardada.getIdPublicacion()));
        return publicacionMapper.toResponse(guardada);
    }
}
