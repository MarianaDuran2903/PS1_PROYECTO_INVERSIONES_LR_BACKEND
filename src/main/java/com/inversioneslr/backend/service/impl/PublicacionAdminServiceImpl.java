package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.dto.request.ActualizarPublicacionRequest;
import com.inversioneslr.backend.dto.request.BloqueoPublicacionRequest;
import com.inversioneslr.backend.dto.request.EdicionAdministrativaRequest;
import com.inversioneslr.backend.dto.request.ReactivacionPublicacionRequest;
import com.inversioneslr.backend.dto.request.RechazoPublicacionRequest;
import com.inversioneslr.backend.dto.response.PaginaResponse;
import com.inversioneslr.backend.dto.response.PublicacionResponse;
import com.inversioneslr.backend.exception.EstadoNoEditableException;
import com.inversioneslr.backend.exception.InformacionIncompletaException;
import com.inversioneslr.backend.exception.InformacionMinimaIncompletaException;
import com.inversioneslr.backend.exception.PublicacionNoEncontradaException;
import com.inversioneslr.backend.exception.ReferenciaInvalidaException;
import com.inversioneslr.backend.exception.TransicionNoPermitidaException;
import com.inversioneslr.backend.mapper.PublicacionMapper;
import com.inversioneslr.backend.model.Ciudad;
import com.inversioneslr.backend.model.DatosPublicacion;
import com.inversioneslr.backend.model.Publicacion;
import com.inversioneslr.backend.model.TipoAlojamiento;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.AccionAuditoria;
import com.inversioneslr.backend.model.enums.EstadoPublicacion;
import com.inversioneslr.backend.repository.CiudadRepository;
import com.inversioneslr.backend.repository.PublicacionRepository;
import com.inversioneslr.backend.repository.TipoAlojamientoRepository;
import com.inversioneslr.backend.repository.UsuarioRepository;
import com.inversioneslr.backend.security.UsuarioAutenticado;
import com.inversioneslr.backend.service.AuditoriaService;
import com.inversioneslr.backend.service.PublicacionAdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
public class PublicacionAdminServiceImpl implements PublicacionAdminService {

    static final String ENTIDAD_PUBLICACION = "Publicacion";
    static final String MOTIVO_PUBLICACION_ACTIVA = "PUBLICACION_ACTIVA";
    static final String MENSAJE_PUBLICACION_ACTIVA = "La publicación está activa: páusela antes de intervenirla";
    static final String MENSAJE_REGLAS_OBLIGATORIAS =
            "Las reglas son obligatorias para una publicación en revisión";
    static final String MOTIVO_ESTADO_NO_PERMITIDO = "ESTADO_NO_PERMITIDO";
    static final String MOTIVO_INFORMACION_INCOMPLETA = "INFORMACION_INCOMPLETA";
    static final String MENSAJE_INFORMACION_INCOMPLETA =
            "La publicación no tiene la información mínima para activarse";
    static final String MENSAJE_SIN_PENDIENTES = "No hay publicaciones pendientes de revisión";
    static final String MENSAJE_SIN_PUBLICACIONES = "No hay publicaciones para mostrar";
    /** Al gestionar, lo más reciente va primero. */
    private static final Sort ORDEN_MAS_RECIENTES_PRIMERO = Sort.by(Sort.Direction.DESC, "idPublicacion");
    /** Las pendientes se atienden en orden de llegada: el identificador crece con cada publicación nueva. */
    private static final Sort ORDEN_MAS_ANTIGUAS_PRIMERO = Sort.by(Sort.Direction.ASC, "idPublicacion");

    private static final Logger log = LoggerFactory.getLogger(PublicacionAdminServiceImpl.class);

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

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<PublicacionResponse> listarPendientes(UsuarioAutenticado administrador, int pagina,
                                                                int tamano) {
        Page<Publicacion> resultado = publicacionRepository.findByEstado(EstadoPublicacion.PENDIENTE_REVISION,
                PageRequest.of(pagina, tamano, ORDEN_MAS_ANTIGUAS_PRIMERO));
        log.info("Consulta de publicaciones pendientes {} {} {} {} {}",
                kv("usuario", administrador.correo()),
                kv("accion", "CONSULTAR_PUBLICACIONES_PENDIENTES"),
                kv("entidad", ENTIDAD_PUBLICACION),
                kv("resultado", "EXITOSO"),
                kv("totalElementos", resultado.getTotalElements()));
        return PaginaResponse.de(resultado, publicacionMapper::toResponse, MENSAJE_SIN_PENDIENTES);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<PublicacionResponse> listar(UsuarioAutenticado administrador, EstadoPublicacion estado,
                                                      int pagina, int tamano) {
        PageRequest solicitud = PageRequest.of(pagina, tamano, ORDEN_MAS_RECIENTES_PRIMERO);
        Page<Publicacion> resultado = estado == null
                ? publicacionRepository.findAllBy(solicitud)
                : publicacionRepository.findByEstado(estado, solicitud);
        log.info("Consulta administrativa de publicaciones {} {} {} {} {} {}",
                kv("usuario", administrador.correo()),
                kv("accion", "CONSULTAR_PUBLICACIONES_ADMIN"),
                kv("entidad", ENTIDAD_PUBLICACION),
                kv("estado", estado == null ? "TODOS" : estado.name()),
                kv("resultado", "EXITOSO"),
                kv("totalElementos", resultado.getTotalElements()));
        return PaginaResponse.de(resultado, publicacionMapper::toResponse, MENSAJE_SIN_PUBLICACIONES);
    }

    @Override
    @Transactional
    public PublicacionResponse aprobar(Long idPublicacion, UsuarioAutenticado administrador) {
        Publicacion publicacion = buscarPendiente(idPublicacion, administrador, AccionAuditoria.APROBAR_PUBLICACION);
        Map<String, String> faltantes = publicacion.informacionFaltante();
        if (!faltantes.isEmpty()) {
            auditoriaService.registrarFallo(administrador.correo(), AccionAuditoria.APROBAR_PUBLICACION,
                    ENTIDAD_PUBLICACION, MOTIVO_INFORMACION_INCOMPLETA);
            throw new InformacionMinimaIncompletaException(MENSAJE_INFORMACION_INCOMPLETA, faltantes);
        }

        EstadoPublicacion anterior = publicacion.getEstado();
        publicacion.aprobar();
        Publicacion guardada = publicacionRepository.save(publicacion);
        Usuario admin = usuarioRepository.getReferenceById(administrador.idUsuario());
        auditoriaService.registrarExito(admin, AccionAuditoria.APROBAR_PUBLICACION, ENTIDAD_PUBLICACION,
                String.valueOf(guardada.getIdPublicacion()), anterior + " -> " + guardada.getEstado());
        return publicacionMapper.toResponse(guardada);
    }

    @Override
    @Transactional
    public PublicacionResponse rechazar(Long idPublicacion, RechazoPublicacionRequest request,
                                        UsuarioAutenticado administrador) {
        Publicacion publicacion = buscarPendiente(idPublicacion, administrador, AccionAuditoria.RECHAZAR_PUBLICACION);

        publicacion.rechazar();
        Publicacion guardada = publicacionRepository.save(publicacion);
        Usuario admin = usuarioRepository.getReferenceById(administrador.idUsuario());
        auditoriaService.registrarExito(admin, AccionAuditoria.RECHAZAR_PUBLICACION, ENTIDAD_PUBLICACION,
                String.valueOf(guardada.getIdPublicacion()), request.motivo().strip());
        return publicacionMapper.toResponse(guardada);
    }

    @Override
    @Transactional
    public PublicacionResponse bloquear(Long idPublicacion, BloqueoPublicacionRequest request,
                                        UsuarioAutenticado administrador) {
        Publicacion publicacion = buscar(idPublicacion);
        EstadoPublicacion anterior = publicacion.getEstado();
        if (!anterior.puedePasarA(EstadoPublicacion.BLOQUEADA)) {
            auditoriaService.registrarFallo(administrador.correo(), AccionAuditoria.BLOQUEAR_PUBLICACION,
                    ENTIDAD_PUBLICACION, MOTIVO_ESTADO_NO_PERMITIDO);
            throw TransicionNoPermitidaException.alBloquear(anterior);
        }

        publicacion.bloquear(request.motivo());
        Publicacion guardada = publicacionRepository.save(publicacion);
        // Quién (el Administrador del token), cuándo (fecha del evento) y por qué (el motivo).
        Usuario admin = usuarioRepository.getReferenceById(administrador.idUsuario());
        auditoriaService.registrarExito(admin, AccionAuditoria.BLOQUEAR_PUBLICACION, ENTIDAD_PUBLICACION,
                String.valueOf(guardada.getIdPublicacion()), request.motivo().strip());
        return publicacionMapper.toResponse(guardada);
    }

    @Override
    @Transactional
    public PublicacionResponse reactivar(Long idPublicacion, ReactivacionPublicacionRequest request,
                                         UsuarioAutenticado administrador) {
        Publicacion publicacion = buscar(idPublicacion);
        EstadoPublicacion anterior = publicacion.getEstado();
        if (!anterior.permiteReactivacion()) {
            auditoriaService.registrarFallo(administrador.correo(), AccionAuditoria.REACTIVAR_PUBLICACION,
                    ENTIDAD_PUBLICACION, MOTIVO_ESTADO_NO_PERMITIDO);
            throw TransicionNoPermitidaException.alReactivar(anterior);
        }
        // Durante la pausa o el bloqueo la información pudo quedar incompleta; una ACTIVA no puede estarlo.
        Map<String, String> faltantes = publicacion.informacionFaltante();
        if (!faltantes.isEmpty()) {
            auditoriaService.registrarFallo(administrador.correo(), AccionAuditoria.REACTIVAR_PUBLICACION,
                    ENTIDAD_PUBLICACION, MOTIVO_INFORMACION_INCOMPLETA);
            throw new InformacionMinimaIncompletaException(MENSAJE_INFORMACION_INCOMPLETA, faltantes);
        }

        publicacion.reactivar();
        Publicacion guardada = publicacionRepository.save(publicacion);
        Usuario admin = usuarioRepository.getReferenceById(administrador.idUsuario());
        auditoriaService.registrarExito(admin, AccionAuditoria.REACTIVAR_PUBLICACION, ENTIDAD_PUBLICACION,
                String.valueOf(guardada.getIdPublicacion()),
                anterior + " -> " + guardada.getEstado() + ": " + request.motivo().strip());
        return publicacionMapper.toResponse(guardada);
    }

    /**
     * Busca la publicación y verifica que esté pendiente de revisión; si está en otro estado, el intento
     * queda registrado como fallido.
     */
    private Publicacion buscarPendiente(Long idPublicacion, UsuarioAutenticado administrador,
                                        AccionAuditoria accion) {
        Publicacion publicacion = buscar(idPublicacion);
        if (!publicacion.estaPendienteDeRevision()) {
            auditoriaService.registrarFallo(administrador.correo(), accion, ENTIDAD_PUBLICACION,
                    MOTIVO_ESTADO_NO_PERMITIDO);
            throw TransicionNoPermitidaException.alRevisar(publicacion.getEstado());
        }
        return publicacion;
    }

    private Publicacion buscar(Long idPublicacion) {
        return publicacionRepository.findById(idPublicacion).orElseThrow(PublicacionNoEncontradaException::new);
    }
}
