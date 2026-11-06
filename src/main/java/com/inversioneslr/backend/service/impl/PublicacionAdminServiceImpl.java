package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.dto.request.ActualizarPublicacionRequest;
import com.inversioneslr.backend.dto.request.EdicionAdministrativaRequest;
import com.inversioneslr.backend.dto.response.PublicacionResponse;
import com.inversioneslr.backend.exception.EstadoNoEditableException;
import com.inversioneslr.backend.exception.InformacionIncompletaException;
import com.inversioneslr.backend.exception.PublicacionNoEncontradaException;
import com.inversioneslr.backend.exception.ReferenciaInvalidaException;
import com.inversioneslr.backend.mapper.PublicacionMapper;
import com.inversioneslr.backend.model.Ciudad;
import com.inversioneslr.backend.model.DatosPublicacion;
import com.inversioneslr.backend.model.Publicacion;
import com.inversioneslr.backend.model.TipoAlojamiento;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.AccionAuditoria;
import com.inversioneslr.backend.repository.CiudadRepository;
import com.inversioneslr.backend.repository.PublicacionRepository;
import com.inversioneslr.backend.repository.TipoAlojamientoRepository;
import com.inversioneslr.backend.repository.UsuarioRepository;
import com.inversioneslr.backend.security.UsuarioAutenticado;
import com.inversioneslr.backend.service.AuditoriaService;
import com.inversioneslr.backend.service.PublicacionAdminService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicacionAdminServiceImpl implements PublicacionAdminService {

    static final String ENTIDAD_PUBLICACION = "Publicacion";
    static final String MOTIVO_PUBLICACION_ACTIVA = "PUBLICACION_ACTIVA";
    static final String MENSAJE_PUBLICACION_ACTIVA = "La publicación está activa: páusela antes de intervenirla";
    static final String MENSAJE_REGLAS_OBLIGATORIAS =
            "Las reglas son obligatorias para una publicación en revisión";

    private final PublicacionRepository publicacionRepository;
    private final CiudadRepository ciudadRepository;
    private final TipoAlojamientoRepository tipoAlojamientoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;
    private final PublicacionMapper publicacionMapper;

    public PublicacionAdminServiceImpl(PublicacionRepository publicacionRepository,
                                       CiudadRepository ciudadRepository,
                                       TipoAlojamientoRepository tipoAlojamientoRepository,
                                       UsuarioRepository usuarioRepository, AuditoriaService auditoriaService,
                                       PublicacionMapper publicacionMapper) {
        this.publicacionRepository = publicacionRepository;
        this.ciudadRepository = ciudadRepository;
        this.tipoAlojamientoRepository = tipoAlojamientoRepository;
        this.usuarioRepository = usuarioRepository;
        this.auditoriaService = auditoriaService;
        this.publicacionMapper = publicacionMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PublicacionResponse obtener(Long idPublicacion) {
        return publicacionMapper.toResponse(buscar(idPublicacion));
    }

    @Override
    @Transactional
    public PublicacionResponse intervenir(Long idPublicacion, EdicionAdministrativaRequest request,
                                          UsuarioAutenticado administrador) {
        Publicacion publicacion = buscar(idPublicacion);
        if (!publicacion.getEstado().permiteIntervencionAdministrativa()) {
            auditoriaService.registrarFallo(administrador.correo(), AccionAuditoria.INTERVENCION_ADMIN_PUBLICACION,
                    ENTIDAD_PUBLICACION, MOTIVO_PUBLICACION_ACTIVA);
            throw new EstadoNoEditableException(MENSAJE_PUBLICACION_ACTIVA);
        }

        // Todo se valida antes de tocar la entidad: si algo falla, la publicación no cambia.
        ActualizarPublicacionRequest cambios = request.cambios();
        if (publicacion.getEstado().exigeInformacionCompleta()
                && (cambios.reglas() == null || cambios.reglas().isBlank())) {
            throw new InformacionIncompletaException("cambios.reglas", MENSAJE_REGLAS_OBLIGATORIAS);
        }
        Ciudad ciudad = ciudadRepository.findById(cambios.idCiudad())
                .orElseThrow(() -> new ReferenciaInvalidaException("cambios.idCiudad", "La ciudad no existe"));
        TipoAlojamiento tipo = tipoAlojamientoRepository.findById(cambios.idTipo())
                .orElseThrow(() -> new ReferenciaInvalidaException("cambios.idTipo",
                        "El tipo de alojamiento no existe"));
        DatosPublicacion datos = DatosPublicacion.normalizados(cambios.titulo(), cambios.descripcion(),
                cambios.direccionTextual(), cambios.barrioZona(), cambios.capacidad(), cambios.precioNoche());

        publicacion.editar(datos, ciudad, tipo, cambios.tarifaLimpieza(), cambios.reglas());
        Publicacion guardada = publicacionRepository.save(publicacion);

        // Quién (el Administrador del token), cuándo (fecha del evento) y por qué (el motivo).
        Usuario admin = usuarioRepository.getReferenceById(administrador.idUsuario());
        auditoriaService.registrarExito(admin, AccionAuditoria.INTERVENCION_ADMIN_PUBLICACION, ENTIDAD_PUBLICACION,
                String.valueOf(guardada.getIdPublicacion()), request.motivo().strip());
        return publicacionMapper.toResponse(guardada);
    }

    private Publicacion buscar(Long idPublicacion) {
        return publicacionRepository.findById(idPublicacion).orElseThrow(PublicacionNoEncontradaException::new);
    }
}
