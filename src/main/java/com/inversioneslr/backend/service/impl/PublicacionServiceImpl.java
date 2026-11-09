package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.dto.request.ActualizarPublicacionRequest;
import com.inversioneslr.backend.dto.request.CrearPublicacionRequest;
import com.inversioneslr.backend.dto.response.PaginaResponse;
import com.inversioneslr.backend.dto.response.PublicacionResponse;
import com.inversioneslr.backend.dto.response.PublicacionResumenResponse;
import com.inversioneslr.backend.exception.EstadoNoEditableException;
import com.inversioneslr.backend.exception.InformacionMinimaIncompletaException;
import com.inversioneslr.backend.exception.TransicionNoPermitidaException;
import com.inversioneslr.backend.exception.PublicacionAjenaException;
import com.inversioneslr.backend.exception.PublicacionNoEncontradaException;
import com.inversioneslr.backend.exception.ReferenciaInvalidaException;
import com.inversioneslr.backend.mapper.PublicacionMapper;
import com.inversioneslr.backend.model.Ciudad;
import com.inversioneslr.backend.model.DatosPublicacion;
import com.inversioneslr.backend.model.PoliticaCancelacion;
import com.inversioneslr.backend.model.Publicacion;
import com.inversioneslr.backend.model.TipoAlojamiento;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.AccionAuditoria;
import com.inversioneslr.backend.model.enums.EstadoPublicacion;
import com.inversioneslr.backend.repository.CiudadRepository;
import com.inversioneslr.backend.repository.PoliticaCancelacionRepository;
import com.inversioneslr.backend.repository.PublicacionRepository;
import com.inversioneslr.backend.repository.TipoAlojamientoRepository;
import com.inversioneslr.backend.repository.UsuarioRepository;
import com.inversioneslr.backend.security.UsuarioAutenticado;
import com.inversioneslr.backend.service.AuditoriaService;
import com.inversioneslr.backend.service.PublicacionService;
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
public class PublicacionServiceImpl implements PublicacionService {

    static final String ENTIDAD_PUBLICACION = "Publicacion";
    /** Política de cancelación que se asigna a toda publicación nueva. */
    static final String POLITICA_POR_DEFECTO = "Flexible";
    static final String MOTIVO_NO_ES_PROPIETARIO = "NO_ES_PROPIETARIO";
    static final String MOTIVO_ESTADO_NO_EDITABLE = "ESTADO_NO_EDITABLE";
    static final String MOTIVO_ESTADO_NO_PERMITIDO = "ESTADO_NO_PERMITIDO";
    static final String MOTIVO_INFORMACION_INCOMPLETA = "INFORMACION_INCOMPLETA";
    static final String MENSAJE_SIN_PUBLICACIONES = "No tiene publicaciones registradas";
    /** La tabla no guarda la fecha de creación; el identificador crece con cada publicación nueva. */
    private static final Sort ORDEN_MAS_RECIENTES = Sort.by(Sort.Direction.DESC, "idPublicacion");

    private static final Logger log = LoggerFactory.getLogger(PublicacionServiceImpl.class);

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
        Ciudad ciudad = buscarCiudad(request.idCiudad());
        TipoAlojamiento tipo = buscarTipo(request.idTipo());
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

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<PublicacionResumenResponse> listarPropias(UsuarioAutenticado autenticado, int pagina,
                                                                    int tamano) {
        // El filtro es siempre el Anfitrión del token: no hay forma de pedir las publicaciones de otro.
        Page<Publicacion> resultado = publicacionRepository.findByAnfitrionIdUsuario(autenticado.idUsuario(),
                PageRequest.of(pagina, tamano, ORDEN_MAS_RECIENTES));
        log.info("Consulta de publicaciones propias {} {} {} {} {}",
                kv("usuario", autenticado.correo()),
                kv("accion", "CONSULTAR_PUBLICACIONES_PROPIAS"),
                kv("entidad", ENTIDAD_PUBLICACION),
                kv("resultado", "EXITOSO"),
                kv("totalElementos", resultado.getTotalElements()));
        return PaginaResponse.de(resultado, publicacionMapper::toResumen, MENSAJE_SIN_PUBLICACIONES);
    }

    @Override
    @Transactional(readOnly = true)
    public PublicacionResponse obtenerPropia(Long idPublicacion, UsuarioAutenticado autenticado) {
        return publicacionMapper.toResponse(buscarPropia(idPublicacion, autenticado));
    }

    @Override
    @Transactional
    public PublicacionResponse actualizar(Long idPublicacion, ActualizarPublicacionRequest request,
                                          UsuarioAutenticado autenticado) {
        Publicacion publicacion = buscarPropia(idPublicacion, autenticado);
        if (!publicacion.getEstado().permiteEdicionDelAnfitrion()) {
            auditoriaService.registrarFallo(autenticado.correo(), AccionAuditoria.EDITAR_PUBLICACION,
                    ENTIDAD_PUBLICACION, MOTIVO_ESTADO_NO_EDITABLE);
            throw new EstadoNoEditableException(publicacion.getEstado());
        }

        // Las referencias se resuelven antes de tocar la entidad: si alguna falla, la publicación no cambia.
        Ciudad ciudad = buscarCiudad(request.idCiudad());
        TipoAlojamiento tipo = buscarTipo(request.idTipo());
        DatosPublicacion datos = DatosPublicacion.normalizados(request.titulo(), request.descripcion(),
                request.direccionTextual(), request.barrioZona(), request.capacidad(), request.precioNoche());

        publicacion.editar(datos, ciudad, tipo, request.tarifaLimpieza(), request.reglas());
        Publicacion guardada = publicacionRepository.save(publicacion);
        auditoriaService.registrarExito(guardada.getAnfitrion(), AccionAuditoria.EDITAR_PUBLICACION,
                ENTIDAD_PUBLICACION, String.valueOf(guardada.getIdPublicacion()));
        return publicacionMapper.toResponse(guardada);
    }

    @Override
    @Transactional
    public PublicacionResponse solicitarActivacion(Long idPublicacion, UsuarioAutenticado autenticado) {
        Publicacion publicacion = buscarPropia(idPublicacion, autenticado);
        EstadoPublicacion anterior = publicacion.getEstado();
        if (!anterior.puedePasarA(EstadoPublicacion.PENDIENTE_REVISION)) {
            auditoriaService.registrarFallo(autenticado.correo(), AccionAuditoria.ENVIAR_A_REVISION,
                    ENTIDAD_PUBLICACION, MOTIVO_ESTADO_NO_PERMITIDO);
            throw TransicionNoPermitidaException.alEnviarARevision(anterior);
        }
        Map<String, String> faltantes = publicacion.informacionFaltante();
        if (!faltantes.isEmpty()) {
            auditoriaService.registrarFallo(autenticado.correo(), AccionAuditoria.ENVIAR_A_REVISION,
                    ENTIDAD_PUBLICACION, MOTIVO_INFORMACION_INCOMPLETA);
            throw new InformacionMinimaIncompletaException(faltantes);
        }

        publicacion.solicitarRevision();
        Publicacion guardada = publicacionRepository.save(publicacion);
        auditoriaService.registrarExito(guardada.getAnfitrion(), AccionAuditoria.ENVIAR_A_REVISION,
                ENTIDAD_PUBLICACION, String.valueOf(guardada.getIdPublicacion()),
                anterior + " -> " + guardada.getEstado());
        return publicacionMapper.toResponse(guardada);
    }

    /**
     * Busca la publicación y verifica que sea del Anfitrión autenticado. El intento sobre una ajena queda
     * registrado como acceso denegado.
     */
    private Publicacion buscarPropia(Long idPublicacion, UsuarioAutenticado autenticado) {
        Publicacion publicacion = publicacionRepository.findById(idPublicacion)
                .orElseThrow(PublicacionNoEncontradaException::new);
        if (!publicacion.perteneceA(autenticado.idUsuario())) {
            auditoriaService.registrarIntentoFallido(autenticado.idUsuario(), autenticado.correo(),
                    AccionAuditoria.ACCESO_DENEGADO, ENTIDAD_PUBLICACION, String.valueOf(idPublicacion),
                    MOTIVO_NO_ES_PROPIETARIO);
            throw new PublicacionAjenaException();
        }
        return publicacion;
    }

    private Ciudad buscarCiudad(Long idCiudad) {
        return ciudadRepository.findById(idCiudad)
                .orElseThrow(() -> new ReferenciaInvalidaException("idCiudad", "La ciudad no existe"));
    }

    private TipoAlojamiento buscarTipo(Long idTipo) {
        return tipoAlojamientoRepository.findById(idTipo)
                .orElseThrow(() -> new ReferenciaInvalidaException("idTipo", "El tipo de alojamiento no existe"));
    }
}
