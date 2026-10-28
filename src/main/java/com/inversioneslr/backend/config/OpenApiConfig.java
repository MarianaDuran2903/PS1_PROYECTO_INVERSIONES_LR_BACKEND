package com.inversioneslr.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Contrato OpenAPI 3 (RNF-INT-001). Disponible en /swagger-ui.html y /v3/api-docs.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI marketplaceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Marketplace de Alojamientos - Inversiones LR")
                .description("API REST del backend del Marketplace de Alojamientos")
                .version("v1"));
    }
}
