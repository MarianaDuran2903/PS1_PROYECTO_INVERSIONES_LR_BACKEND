package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.dto.request.ActualizarEstadoCuentaRequest;
import com.inversioneslr.backend.dto.request.ActualizarPerfilRequest;
import com.inversioneslr.backend.exception.EstadoCuentaInvalidoException;
import com.inversioneslr.backend.model.NombreCompleto;
import com.inversioneslr.backend.model.enums.AccionAuditoria;
import com.inversioneslr.backend.service.AuditoriaService;
import com.inversioneslr.backend.dto.response.UsuarioResponse;
import com.inversioneslr.backend.mapper.UsuarioMapper;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.repository.UsuarioRepository;
import com.inversioneslr.backend.service.PerfilService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PerfilServiceImpl implements PerfilService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioMapper usuarioMapper;
    private final AuditoriaService auditoriaService;

    public PerfilServiceImpl(
            UsuarioRepository usuarioRepository,
            UsuarioMapper usuarioMapper,
            AuditoriaService auditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioMapper = usuarioMapper;
        this.auditoriaService = auditoriaService;
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioResponse consultarPerfil(Long idUsuario) {
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .filter(actual -> actual.isCuentaActiva() && !actual.isRestringido())
                .orElseThrow(() ->
                        new BadCredentialsException("Debe iniciar sesión"));

        return usuarioMapper.toResponse(usuario);
    }

    @Override
    @Transactional
    public UsuarioResponse actualizarPerfil(Long idUsuario, ActualizarPerfilRequest request) {
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .filter(actual -> actual.isCuentaActiva() && !actual.isRestringido())
                .orElseThrow(() ->
                        new BadCredentialsException("Debe iniciar sesión"));

        usuario.setNombre(NombreCompleto.normalizado(
                request.primerNombre(),
                request.segundoNombre(),
                request.primerApellido(),
                request.segundoApellido()
        ));

        Usuario actualizado = usuarioRepository.save(usuario);

        auditoriaService.registrarExito(
                actualizado,
                AccionAuditoria.ACTUALIZACION_PERFIL,
                "Usuario",
                String.valueOf(actualizado.getIdUsuario())
        );

        return usuarioMapper.toResponse(actualizado);
    }

    @Override
    @Transactional
    public UsuarioResponse cambiarEstado(Long idUsuario, ActualizarEstadoCuentaRequest request) {
        if (request.cuentaActiva()) {
            throw new EstadoCuentaInvalidoException(
                    "La reactivación requiere iniciar sesión con tus credenciales.");
        }

        Usuario usuario = usuarioRepository.findById(idUsuario)
                .filter(actual -> actual.isCuentaActiva() && !actual.isRestringido())
                .orElseThrow(() ->
                        new BadCredentialsException("Debe iniciar sesión"));

        usuario.setCuentaActiva(false);

        Usuario actualizado = usuarioRepository.save(usuario);

        auditoriaService.registrarExito(
                actualizado,
                AccionAuditoria.DESACTIVACION_CUENTA,
                "Usuario",
                String.valueOf(actualizado.getIdUsuario())
        );

        return usuarioMapper.toResponse(actualizado);
    }
}
