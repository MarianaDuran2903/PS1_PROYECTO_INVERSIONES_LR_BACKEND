package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.config.AdminInicialProperties;
import com.inversioneslr.backend.model.NombreCompleto;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.Rol;
import com.inversioneslr.backend.repository.UsuarioRepository;
import com.inversioneslr.backend.service.AdministradorInicialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
public class AdministradorInicialServiceImpl implements AdministradorInicialService {

    private static final Logger log = LoggerFactory.getLogger(AdministradorInicialServiceImpl.class);
    static final NombreCompleto NOMBRE_ADMINISTRADOR = new NombreCompleto("Administrador", null, "Plataforma", null);
    private static final String ACCION = "CREAR_ADMINISTRADOR_INICIAL";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminInicialProperties propiedades;

    public AdministradorInicialServiceImpl(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                                           AdminInicialProperties propiedades) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.propiedades = propiedades;
    }

    @Override
    @Transactional
    public boolean crearSiNoExiste() {
        if (usuarioRepository.existsByRol(Rol.ADMINISTRADOR)) {
            return false;
        }
        if (!propiedades.configurado()) {
            log.warn("No hay Administrador y faltan ADMIN_CORREO o ADMIN_CONTRASENA {} {}",
                    kv("accion", ACCION), kv("resultado", "OMITIDO"));
            return false;
        }
        String correo = propiedades.correo().strip().toLowerCase(Locale.ROOT);
        if (usuarioRepository.existsByCorreoIgnoreCase(correo)) {
            log.warn("El correo del Administrador inicial ya pertenece a otra cuenta {} {} {}",
                    kv("accion", ACCION), kv("resultado", "FALLIDO"), kv("usuario", correo));
            return false;
        }
        Usuario administrador = usuarioRepository.save(new Usuario(NOMBRE_ADMINISTRADOR, correo,
                passwordEncoder.encode(propiedades.contrasena()), Rol.ADMINISTRADOR));
        log.info("Administrador inicial creado {} {} {} {}",
                kv("accion", ACCION), kv("resultado", "EXITOSO"), kv("usuario", correo),
                kv("idEntidad", administrador.getIdUsuario()));
        return true;
    }
}
