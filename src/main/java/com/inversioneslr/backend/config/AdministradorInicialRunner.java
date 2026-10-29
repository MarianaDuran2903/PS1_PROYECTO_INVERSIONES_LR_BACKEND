package com.inversioneslr.backend.config;

import com.inversioneslr.backend.service.AdministradorInicialService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Al arrancar, crea el Administrador inicial si no existe (después de que Flyway aplicó las migraciones).
 */
@Component
public class AdministradorInicialRunner implements ApplicationRunner {

    private final AdministradorInicialService administradorInicialService;

    public AdministradorInicialRunner(AdministradorInicialService administradorInicialService) {
        this.administradorInicialService = administradorInicialService;
    }

    @Override
    public void run(ApplicationArguments args) {
        administradorInicialService.crearSiNoExiste();
    }
}
