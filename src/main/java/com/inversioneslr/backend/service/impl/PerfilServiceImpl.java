package com.inversioneslr.backend.service.impl;

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

    public PerfilServiceImpl(
            UsuarioRepository usuarioRepository,
            UsuarioMapper usuarioMapper) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioMapper = usuarioMapper;
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

}
