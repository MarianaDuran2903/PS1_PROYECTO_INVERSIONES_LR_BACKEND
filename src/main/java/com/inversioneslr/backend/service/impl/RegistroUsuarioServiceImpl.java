package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.dto.request.RegistroHuespedRequest;
import com.inversioneslr.backend.dto.response.UsuarioResponse;
import com.inversioneslr.backend.exception.CorreoYaRegistradoException;
import com.inversioneslr.backend.mapper.UsuarioMapper;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.AccionAuditoria;
import com.inversioneslr.backend.model.enums.Rol;
import com.inversioneslr.backend.repository.UsuarioRepository;
import com.inversioneslr.backend.service.AuditoriaService;
import com.inversioneslr.backend.service.RegistroUsuarioService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class RegistroUsuarioServiceImpl implements RegistroUsuarioService {

    static final String ENTIDAD_USUARIO = "Usuario";
    static final String MOTIVO_CORREO_DUPLICADO = "CORREO_DUPLICADO";
    private static final String INDICE_CORREO_UNICO = "uq_usuario_correo";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;
    private final UsuarioMapper usuarioMapper;

    public RegistroUsuarioServiceImpl(UsuarioRepository usuarioRepository,
                                      PasswordEncoder passwordEncoder,
                                      AuditoriaService auditoriaService,
                                      UsuarioMapper usuarioMapper) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
        this.usuarioMapper = usuarioMapper;
    }

    @Override
    @Transactional
    public UsuarioResponse registrarHuesped(RegistroHuespedRequest request) {
        String correo = normalizarCorreo(request.correo());

        if (usuarioRepository.existsByCorreoIgnoreCase(correo)) {
            throw correoDuplicado(correo);
        }

        // El rol lo fija el servidor: el visitante nunca elige su rol.
        Usuario usuario = new Usuario(
                request.nombre().strip(),
                correo,
                passwordEncoder.encode(request.contrasena()),
                Rol.HUESPED);

        Usuario guardado;
        try {
            guardado = usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException e) {
            // Dos registros simultáneos con el mismo correo: el índice único de la BD decide.
            if (esViolacionCorreoUnico(e)) {
                throw correoDuplicado(correo);
            }
            throw e;
        }

        auditoriaService.registrarExito(guardado, AccionAuditoria.REGISTRO_USUARIO,
                ENTIDAD_USUARIO, String.valueOf(guardado.getIdUsuario()));
        return usuarioMapper.toResponse(guardado);
    }

    private CorreoYaRegistradoException correoDuplicado(String correo) {
        auditoriaService.registrarFallo(correo, AccionAuditoria.REGISTRO_USUARIO,
                ENTIDAD_USUARIO, MOTIVO_CORREO_DUPLICADO);
        return new CorreoYaRegistradoException();
    }

    private static String normalizarCorreo(String correo) {
        return correo.strip().toLowerCase(Locale.ROOT);
    }

    private static boolean esViolacionCorreoUnico(DataIntegrityViolationException e) {
        String mensaje = e.getMostSpecificCause().getMessage();
        return mensaje != null && mensaje.contains(INDICE_CORREO_UNICO);
    }
}
