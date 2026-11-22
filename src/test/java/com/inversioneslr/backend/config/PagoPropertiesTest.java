package com.inversioneslr.backend.config;

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

class PagoPropertiesTest {

    private static ValidatorFactory fabrica;
    private static Validator validador;

    @BeforeAll
    static void abrirValidador() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void cerrarValidador() {
        fabrica.close();
    }

    private static PagoProperties propiedades(String claveSecreta, long minutosSesion) {
        return new PagoProperties("COP", minutosSesion, 5, 3,
                "https://app.test/reservas/{idReserva}/pago/resultado",
                "https://app.test/reservas/{idReserva}/pago/resultado?cancelado=true",
                new PagoProperties.Stripe(claveSecreta, "whsec_x", 300));
    }

    private static Set<String> camposInvalidos(PagoProperties propiedades) {
        return validador.validate(propiedades).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @ParameterizedTest(name = "clave \"{0}\"")
    @ValueSource(strings = {"sk_test_ficticia", "rk_test_ficticia"})
    @DisplayName("Se aceptan las claves de prueba")
    void clavesDePrueba_sonValidas(String clave) {
        assertThat(camposInvalidos(propiedades(clave, 35))).isEmpty();
    }

    @Test
    @DisplayName("Sin clave la aplicación arranca: los pagos responden 502 pero lo demás funciona")
    void sinClave_esValido() {
        assertThat(camposInvalidos(propiedades("", 35))).isEmpty();
        assertThat(camposInvalidos(propiedades(null, 35))).isEmpty();
    }

    @ParameterizedTest(name = "clave \"{0}\"")
    @ValueSource(strings = {"sk_live_ficticia", "rk_live_ficticia", "pk_test_ficticia", "sk_test_", "clave cualquiera"})
    @DisplayName("Una clave real o que no es una clave secreta de prueba impide arrancar")
    void clavesQueNoSonDePrueba_seRechazan(String clave) {
        assertThat(camposInvalidos(propiedades(clave, 35))).contains("stripe.claveSecreta");
    }

    @ParameterizedTest(name = "{0} minutos")
    @ValueSource(longs = {0, 30, 1441})
    @DisplayName("La sesión debe durar entre 31 minutos y un día: Stripe exige al menos 30")
    void duracionDeLaSesion_fueraDeRango_seRechaza(long minutos) {
        assertThat(camposInvalidos(propiedades("", minutos))).contains("minutosSesion");
    }

    @ParameterizedTest(name = "{0} minutos")
    @ValueSource(longs = {31, 35, 1440})
    void duracionDeLaSesion_dentroDeRango_esValida(long minutos) {
        assertThat(camposInvalidos(propiedades("", minutos))).isEmpty();
    }

    @Test
    void urlsDeRetorno_reemplazanLaReserva() {
        PagoProperties propiedades = propiedades("", 35);

        assertThat(propiedades.urlExitoDe(31L)).isEqualTo("https://app.test/reservas/31/pago/resultado");
        assertThat(propiedades.urlCancelacionDe(31L))
                .isEqualTo("https://app.test/reservas/31/pago/resultado?cancelado=true");
    }

    @Test
    void tieneClaves_distingueLaAusenciaDelEspacioEnBlanco() {
        assertThat(new PagoProperties.Stripe("sk_test_x", "whsec_x", 300).tieneClaveSecreta()).isTrue();
        assertThat(new PagoProperties.Stripe("sk_test_x", "whsec_x", 300).tieneClaveWebhook()).isTrue();
        assertThat(new PagoProperties.Stripe("", " ", 300).tieneClaveSecreta()).isFalse();
        assertThat(new PagoProperties.Stripe(null, null, 300).tieneClaveWebhook()).isFalse();
        assertThat(new PagoProperties.Stripe(" ", "  ", 300).tieneClaveWebhook()).isFalse();
    }
}
