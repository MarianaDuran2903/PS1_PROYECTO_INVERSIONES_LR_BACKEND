package com.inversioneslr.backend.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reglas del formulario de registro (deben coincidir con las del frontend).
 */
class RegistroUsuarioRequestValidationTest {

    private static final String CONTRASENA = "Segura#123";

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void crearValidador() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void cerrarValidador() {
        factory.close();
    }

    @Test
    void datosValidos_noTienenErrores() {
        assertThat(camposConError(new RegistroUsuarioRequest(
                "María", "José", "Rodríguez", "O'Neill", "maria@correo.com", CONTRASENA, CONTRASENA)))
                .isEmpty();
    }

    @Test
    void opcionalesNulosOVacios_sonValidos() {
        assertThat(camposConError(new RegistroUsuarioRequest(
                "Ana", null, "Pérez", "   ", "ana@correo.com", CONTRASENA, CONTRASENA)))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"María José", "Ana-Lucía", "D'Angelo", "Ñusta", " Ana "})
    void nombresConLetrasEspaciosGuionOApostrofe_sonValidos(String nombre) {
        assertThat(camposConError(conPrimerNombre(nombre))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Ana2", "Ana_", "Ana@", "-Ana", "Ana--Lucía", "Ana  María"})
    void nombresConCaracteresNoPermitidos_sonInvalidos(String nombre) {
        assertThat(camposConError(conPrimerNombre(nombre))).containsExactly("primerNombre");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void primerNombreVacioOSoloEspacios_esInvalido(String nombre) {
        assertThat(camposConError(conPrimerNombre(nombre))).contains("primerNombre");
    }

    @Test
    void nombreDeMasDe50Caracteres_esInvalido() {
        assertThat(camposConError(conPrimerNombre("A".repeat(51)))).containsExactly("primerNombre");
    }

    @Test
    void segundoApellidoConNumeros_esInvalido() {
        assertThat(camposConError(new RegistroUsuarioRequest(
                "Ana", null, "Pérez", "Gómez3", "ana@correo.com", CONTRASENA, CONTRASENA)))
                .containsExactly("segundoApellido");
    }

    @Test
    void primerApellidoAusente_esInvalido() {
        assertThat(camposConError(new RegistroUsuarioRequest(
                "Ana", null, null, null, "ana@correo.com", CONTRASENA, CONTRASENA)))
                .containsExactly("primerApellido");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Segura#1", "Segura#12345", "Segura#123456", "Ñandú$2024", "aB3!aB3!"})
    @DisplayName("Contraseñas de 8 a 64 caracteres con mayúscula, minúscula, número y especial son válidas")
    void contrasenasSeguras_sonValidas(String contrasena) {
        assertThat(camposConError(conContrasena(contrasena))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Segura#",        // 7 caracteres
            "segura#123",     // sin mayúscula
            "SEGURA#123",     // sin minúscula
            "Segura#abc",     // sin número
            "Segura1234",     // sin carácter especial
            "Segu ra#12"      // con espacio
    })
    void contrasenasQueNoCumplen_sonInvalidas(String contrasena) {
        assertThat(camposConError(conContrasena(contrasena))).containsOnly("contrasena");
    }

    @Test
    void contrasenaDe64Caracteres_esValida() {
        String contrasena = "Aa1#" + "x".repeat(60);
        assertThat(contrasena).hasSize(64);
        assertThat(camposConError(conContrasena(contrasena))).isEmpty();
    }

    @Test
    void contrasenaDe65Caracteres_esInvalida() {
        String contrasena = "Aa1#" + "x".repeat(61);
        assertThat(camposConError(conContrasena(contrasena))).containsOnly("contrasena");
    }

    @Test
    @DisplayName("Una contraseña de 64 caracteres que supera los 72 bytes de BCrypt es inválida")
    void contrasenaDeMasDe72Bytes_esInvalida() {
        String contrasena = "Aa1#" + "ñ".repeat(60); // 64 caracteres, 124 bytes en UTF-8
        assertThat(contrasena).hasSize(64);

        Set<ConstraintViolation<RegistroUsuarioRequest>> errores = validator.validate(conContrasena(contrasena));

        assertThat(errores).singleElement()
                .satisfies(error -> {
                    assertThat(error.getPropertyPath()).hasToString("contrasena");
                    assertThat(error.getMessage()).contains("demasiado larga");
                });
    }

    @Test
    void confirmacionDistinta_esInvalidaEnElCampoConfirmacion() {
        assertThat(camposConError(new RegistroUsuarioRequest(
                "Ana", null, "Pérez", null, "ana@correo.com", CONTRASENA, "Otra#1234")))
                .containsExactly("confirmarContrasena");
    }

    @Test
    void confirmacionAusente_esInvalida() {
        assertThat(camposConError(new RegistroUsuarioRequest(
                "Ana", null, "Pérez", null, "ana@correo.com", CONTRASENA, null)))
                .containsExactly("confirmarContrasena");
    }

    @Test
    void correoInvalido_esInvalido() {
        assertThat(camposConError(new RegistroUsuarioRequest(
                "Ana", null, "Pérez", null, "no-es-correo", CONTRASENA, CONTRASENA)))
                .containsExactly("correo");
    }

    @Test
    void toString_noExponeContrasenas() {
        assertThat(conContrasena(CONTRASENA).toString()).doesNotContain(CONTRASENA);
    }

    private static RegistroUsuarioRequest conPrimerNombre(String primerNombre) {
        return new RegistroUsuarioRequest(primerNombre, null, "Pérez", null, "ana@correo.com", CONTRASENA, CONTRASENA);
    }

    private static RegistroUsuarioRequest conContrasena(String contrasena) {
        return new RegistroUsuarioRequest("Ana", null, "Pérez", null, "ana@correo.com", contrasena, contrasena);
    }

    private static Set<String> camposConError(RegistroUsuarioRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }
}
