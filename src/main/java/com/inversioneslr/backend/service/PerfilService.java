package com.inversioneslr.backend.service;

import com.inversioneslr.backend.dto.request.ActualizarPerfilRequest;
import com.inversioneslr.backend.dto.response.UsuarioResponse;

public interface PerfilService {

    UsuarioResponse consultarPerfil(Long idUsuario);
    UsuarioResponse actualizarPerfil(Long idUsuario, ActualizarPerfilRequest request);

}
