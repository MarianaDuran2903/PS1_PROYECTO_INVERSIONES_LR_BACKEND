package com.inversioneslr.backend.service;

import com.inversioneslr.backend.dto.response.UsuarioResponse;

public interface PerfilService {

    UsuarioResponse consultarPerfil(Long idUsuario);

}
