package com.inversioneslr.backend.security;

import com.inversioneslr.backend.service.AutenticacionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Lee el encabezado "Authorization: Bearer <token>" y, si el token es válido, deja al usuario autenticado
 * con su rol (ROLE_HUESPED, ROLE_ANFITRION o ROLE_ADMINISTRADOR). Si no hay token o no es válido, la petición
 * sigue sin autenticar y Spring Security responde 401 en las rutas protegidas.
 * No se declara como @Component para que solo corra dentro de la cadena de seguridad.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String PREFIJO_BEARER = "Bearer ";
    static final String PREFIJO_ROL = "ROLE_";

    private final AutenticacionService autenticacionService;

    public JwtAuthFilter(AutenticacionService autenticacionService) {
        this.autenticacionService = autenticacionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String encabezado = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (encabezado != null && encabezado.startsWith(PREFIJO_BEARER)) {
            String token = encabezado.substring(PREFIJO_BEARER.length()).strip();
            autenticacionService.autenticarToken(token).ifPresent(usuario -> {
                var autenticacion = new UsernamePasswordAuthenticationToken(usuario, null,
                        List.of(new SimpleGrantedAuthority(PREFIJO_ROL + usuario.rol().name())));
                autenticacion.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(autenticacion);
            });
        }
        chain.doFilter(request, response);
    }
}
