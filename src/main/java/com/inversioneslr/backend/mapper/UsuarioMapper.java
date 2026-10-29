package com.inversioneslr.backend.mapper;

import com.inversioneslr.backend.dto.response.UsuarioResponse;
import com.inversioneslr.backend.model.NombreCompleto;
import com.inversioneslr.backend.model.Usuario;
import org.springframework.stereotype.Component;

@Component
public class UsuarioMapper {

    public UsuarioResponse toResponse(Usuario usuario) {
        NombreCompleto nombre = usuario.getNombre();
        return new UsuarioResponse(
                usuario.getIdUsuario(),
                nombre.primerNombre(),
                nombre.segundoNombre(),
                nombre.primerApellido(),
                nombre.segundoApellido(),
                usuario.getCorreo(),
                usuario.getRol(),
                usuario.isCuentaActiva());
    }
}
