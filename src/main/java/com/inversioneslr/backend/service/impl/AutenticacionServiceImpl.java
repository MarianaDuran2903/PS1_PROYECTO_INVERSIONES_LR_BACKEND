package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.dto.request.LoginRequest;
import com.inversioneslr.backend.dto.response.LoginResponse;
import com.inversioneslr.backend.dto.response.SesionResponse;
import com.inversioneslr.backend.exception.CredencialesInvalidasException;
import com.inversioneslr.backend.mapper.UsuarioMapper;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.AccionAuditoria;
import com.inversioneslr.backend.repository.UsuarioRepository;
import com.inversioneslr.backend.security.UsuarioAutenticado;
import com.inversioneslr.backend.service.AuditoriaService;
import com.inversioneslr.backend.service.AutenticacionService;
import com.inversioneslr.backend.service.TokenService;
import com.inversioneslr.backend.service.TokenService.TokenGenerado;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AutenticacionServiceImpl implements AutenticacionService {

    static final String ENTIDAD_USUARIO = "Usuario";
    static final String MOTIVO_CREDENCIALES_INVALIDAS = "CREDENCIALES_INVALIDAS";
    static final String MOTIVO_CUENTA_DESACTIVADA = "CUENTA_DESACTIVADA";
    static final String MOTIVO_CUENTA_RESTRINGIDA = "CUENTA_RESTRINGIDA";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuditoriaService auditoriaService;
    private final UsuarioMapper usuarioMapper;
    // Se compara contra este hash cuando el correo no existe, para que la respuesta tarde lo mismo
    // y no revele por tiempo qué correos están registrados.
    private final String hashSimulado;

    public AutenticacionServiceImpl(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                                    TokenService tokenService, AuditoriaService auditoriaService,
                                    UsuarioMapper usuarioMapper) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.auditoriaService = auditoriaService;
        this.usuarioMapper = usuarioMapper;
        // Valor aleatorio por arranque: no hay ninguna contraseña fija en el código.
        this.hashSimulado = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    @Transactional
    public LoginResponse iniciarSesion(LoginRequest request) {
        String correo = request.correo().strip().toLowerCase(Locale.ROOT);
        Optional<Usuario> encontrado = usuarioRepository.findByCorreoIgnoreCase(correo);

        if (encontrado.isEmpty()) {
            passwordEncoder.matches(request.contrasena(), hashSimulado);
            throw rechazar(null, correo, MOTIVO_CREDENCIALES_INVALIDAS);
        }
        Usuario usuario = encontrado.get();
        if (!passwordEncoder.matches(request.contrasena(), usuario.getContrasenaHash())) {
            throw rechazar(usuario.getIdUsuario(), correo, MOTIVO_CREDENCIALES_INVALIDAS);
        }
        if (!usuario.isCuentaActiva()) {
            throw rechazar(usuario.getIdUsuario(), correo, MOTIVO_CUENTA_DESACTIVADA);
        }
        if (usuario.isRestringido()) {
            throw rechazar(usuario.getIdUsuario(), correo, MOTIVO_CUENTA_RESTRINGIDA);
        }

        TokenGenerado token = tokenService.generar(usuario);
        auditoriaService.registrarExito(usuario, AccionAuditoria.INICIO_SESION, ENTIDAD_USUARIO,
                String.valueOf(usuario.getIdUsuario()));
        return new LoginResponse(token.token(), LoginResponse.TIPO_BEARER, token.segundosDeValidez(),
                usuarioMapper.toResponse(usuario));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UsuarioAutenticado> autenticarToken(String token) {
        return tokenService.validar(token).flatMap(valido -> usuarioRepository.findById(valido.idUsuario())
                .filter(usuario -> usuario.isCuentaActiva() && !usuario.isRestringido())
                .map(usuario -> new UsuarioAutenticado(
                        usuario.getIdUsuario(), usuario.getCorreo(), usuario.getRol(), valido.expiraEn())));
    }

    @Override
    @Transactional(readOnly = true)
    public SesionResponse obtenerSesion(UsuarioAutenticado autenticado) {
        // El filtro JWT ya verificó que el usuario existe y está habilitado.
        Usuario usuario = usuarioRepository.findById(autenticado.idUsuario()).orElseThrow();
        return new SesionResponse(usuarioMapper.toResponse(usuario), autenticado.expiraEn());
    }

    private CredencialesInvalidasException rechazar(Long idUsuario, String correo, String motivo) {
        auditoriaService.registrarIntentoFallido(idUsuario, correo, AccionAuditoria.INICIO_SESION,
                ENTIDAD_USUARIO, idUsuario == null ? null : String.valueOf(idUsuario), motivo);
        return new CredencialesInvalidasException();
    }
}
