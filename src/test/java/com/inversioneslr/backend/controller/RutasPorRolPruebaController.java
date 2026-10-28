package com.inversioneslr.backend.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints que existen SOLO en las pruebas: uno por rol, protegidos igual que lo estarán las funcionalidades
 * reales de las próximas historias. Permiten verificar HU-003 (criterios 3 a 6) sin inventar funcionalidades.
 */
@RestController
@RequestMapping("/api/v1/prueba-roles")
public class RutasPorRolPruebaController {

    @GetMapping("/huesped")
    @PreAuthorize("hasRole('HUESPED')")
    public String huesped() {
        return "funcionalidad de Huésped";
    }

    @GetMapping("/anfitrion")
    @PreAuthorize("hasRole('ANFITRION')")
    public String anfitrion() {
        return "funcionalidad de Anfitrión";
    }

    @GetMapping("/administrador")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public String administrador() {
        return "funcionalidad de Administrador";
    }
}
