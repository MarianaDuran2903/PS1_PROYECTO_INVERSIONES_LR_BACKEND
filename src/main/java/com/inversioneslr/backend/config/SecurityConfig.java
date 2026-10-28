package com.inversioneslr.backend.config;

import com.inversioneslr.backend.security.JwtAuthFilter;
import com.inversioneslr.backend.security.NoAutenticadoEntryPoint;
import com.inversioneslr.backend.service.AutenticacionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Seguridad (RNF-SEG-001, RNF-SEG-002): API sin estado con JWT, rutas públicas explícitas y todo lo demás
 * autenticado. Cada endpoint exige su rol con @PreAuthorize (RF-USR-003).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] RUTAS_DOCUMENTACION = {
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };
    private static final String[] RUTAS_PUBLICAS_POST = {
            "/api/v1/auth/registro/huesped", "/api/v1/auth/registro/anfitrion", "/api/v1/auth/login"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AutenticacionService autenticacionService,
                                                   NoAutenticadoEntryPoint noAutenticadoEntryPoint) throws Exception {
        return http
                // CSRF desactivado de forma segura: la API es sin estado, no usa sesiones ni cookies, y la
                // autenticación viaja en el encabezado Authorization (JWT), que el navegador no envía solo.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, RUTAS_PUBLICAS_POST).permitAll()
                        .requestMatchers(RUTAS_DOCUMENTACION).permitAll()
                        .requestMatchers("/actuator/health", "/error").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthFilter(autenticacionService), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e.authenticationEntryPoint(noAutenticadoEntryPoint))
                .build();
    }

    /**
     * El frontend corre en otro origen (por ejemplo http://localhost:4200): sin CORS el navegador bloquea
     * las peticiones con el encabezado Authorization.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration configuracion = new CorsConfiguration();
        configuracion.setAllowedOrigins(corsProperties.origenesPermitidos());
        configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuracion.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, "Idempotency-Key"));
        configuracion.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", configuracion);
        return fuente;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
