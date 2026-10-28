package com.inversioneslr.backend.mapper;

import com.inversioneslr.backend.dto.response.UsuarioResponse;
import com.inversioneslr.backend.model.Usuario;
import org.springframework.stereotype.Component;

@Component
public class UsuarioMapper {

    public UsuarioResponse toResponse(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getIdUsuario(),
                usuario.getNombre(),
                usuario.getCorreo(),
                usuario.getRol(),
                usuario.isCuentaActiva());
    }
}
