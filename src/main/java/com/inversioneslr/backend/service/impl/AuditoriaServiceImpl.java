package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.model.EventoAuditoria;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.AccionAuditoria;
import com.inversioneslr.backend.model.enums.ResultadoAuditoria;
import com.inversioneslr.backend.repository.EventoAuditoriaRepository;
import com.inversioneslr.backend.repository.UsuarioRepository;
import com.inversioneslr.backend.service.AuditoriaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
public class AuditoriaServiceImpl implements AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaServiceImpl.class);

    private final EventoAuditoriaRepository eventoAuditoriaRepository;
    private final UsuarioRepository usuarioRepository;

    public AuditoriaServiceImpl(EventoAuditoriaRepository eventoAuditoriaRepository,
                                UsuarioRepository usuarioRepository) {
        this.eventoAuditoriaRepository = eventoAuditoriaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public void registrarExito(Usuario usuario, AccionAuditoria accion, String tipoEntidad, String idEntidad) {
        eventoAuditoriaRepository.save(
                new EventoAuditoria(usuario, accion, tipoEntidad, idEntidad, ResultadoAuditoria.EXITOSO));
        log.info("Evento de auditoria {} {}",
                kv("accion", accion),
                kv("resultado", ResultadoAuditoria.EXITOSO),
                kv("usuario", usuario.getCorreo()),
                kv("entidad", tipoEntidad),
                kv("idEntidad", idEntidad),
                kv("rol", usuario.getRol()));
    }

    @Override
    public void registrarFallo(String usuario, AccionAuditoria accion, String tipoEntidad, String motivo) {
        log.warn("Evento de auditoria {} {}",
                kv("accion", accion),
                kv("resultado", ResultadoAuditoria.FALLIDO),
                kv("usuario", usuario),
                kv("entidad", tipoEntidad),
                kv("motivo", motivo));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarIntentoFallido(Long idUsuario, String usuario, AccionAuditoria accion, String tipoEntidad,
                                        String idEntidad, String motivo) {
        Usuario referencia = idUsuario == null ? null : usuarioRepository.getReferenceById(idUsuario);
        eventoAuditoriaRepository.save(
                new EventoAuditoria(referencia, accion, tipoEntidad, idEntidad, ResultadoAuditoria.FALLIDO));
        log.warn("Evento de auditoria {} {}",
                kv("accion", accion),
                kv("resultado", ResultadoAuditoria.FALLIDO),
                kv("usuario", usuario),
                kv("entidad", tipoEntidad),
                kv("idEntidad", idEntidad),
                kv("motivo", motivo));
    }
}
