package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.model.EventoAuditoria;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.AccionAuditoria;
import com.inversioneslr.backend.model.enums.ResultadoAuditoria;
import com.inversioneslr.backend.repository.EventoAuditoriaRepository;
import com.inversioneslr.backend.service.AuditoriaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
public class AuditoriaServiceImpl implements AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaServiceImpl.class);

    private final EventoAuditoriaRepository eventoAuditoriaRepository;

    public AuditoriaServiceImpl(EventoAuditoriaRepository eventoAuditoriaRepository) {
        this.eventoAuditoriaRepository = eventoAuditoriaRepository;
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
}
