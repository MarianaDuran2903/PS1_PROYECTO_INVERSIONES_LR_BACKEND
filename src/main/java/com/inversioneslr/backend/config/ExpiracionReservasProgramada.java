package com.inversioneslr.backend.config;

import com.inversioneslr.backend.service.ExpiracionReservasService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Tarea que vence las reservas que nadie pagó a tiempo. Reservar ya vence por su cuenta las de la publicación
 * pedida; esta tarea limpia el resto para que las fechas vuelvan a aparecer en las búsquedas. Se apaga con
 * {@code reservas.expiracion.habilitada=false}, por ejemplo en las pruebas.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "reservas.expiracion", name = "habilitada", havingValue = "true",
        matchIfMissing = true)
public class ExpiracionReservasProgramada {

    private static final Logger log = LoggerFactory.getLogger(ExpiracionReservasProgramada.class);

    private final ExpiracionReservasService expiracionReservasService;

    public ExpiracionReservasProgramada(ExpiracionReservasService expiracionReservasService) {
        this.expiracionReservasService = expiracionReservasService;
    }

    @Scheduled(fixedDelayString = "${reservas.expiracion.intervalo-ms:60000}",
            initialDelayString = "${reservas.expiracion.intervalo-ms:60000}")
    public void vencerReservasSinPago() {
        try {
            int vencidas = expiracionReservasService.vencerPendientes();
            if (vencidas > 0) {
                log.info("Reservas vencidas sin pago {} {} {}",
                        kv("accion", "VENCER_RESERVAS"),
                        kv("resultado", "EXITOSO"),
                        kv("cantidad", vencidas));
            }
        } catch (RuntimeException error) {
            // Una falla puntual no debe detener la tarea: la siguiente ejecución lo intenta de nuevo.
            log.error("No se pudieron vencer las reservas sin pago {} {}",
                    kv("accion", "VENCER_RESERVAS"),
                    kv("resultado", "FALLIDO"),
                    error);
        }
    }
}
