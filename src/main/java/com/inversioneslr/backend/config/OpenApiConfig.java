package com.inversioneslr.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Contrato OpenAPI 3 (RNF-INT-001). Disponible en /swagger-ui.html y /v3/api-docs.
 * El esquema bearerAuth activa el botón "Authorize" de Swagger para probar con el token del login.
 */
@Configuration
public class OpenApiConfig {

    public static final String ESQUEMA_BEARER = "bearerAuth";

    @Bean
    public OpenAPI marketplaceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Marketplace de Alojamientos - Inversiones LR")
                        .description("API REST del backend del Marketplace de Alojamientos")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(ESQUEMA_BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Token obtenido en POST /api/v1/auth/login")));
    }
}
