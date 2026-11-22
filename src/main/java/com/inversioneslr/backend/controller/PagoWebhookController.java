package com.inversioneslr.backend.controller;

import com.inversioneslr.backend.dto.response.ErrorResponse;
import com.inversioneslr.backend.service.PagoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/pagos")
@Tag(name = "Pagos", description = "Pago de una reserva con Stripe en modo de pruebas")
public class PagoWebhookController {

    static final String ENCABEZADO_FIRMA = "Stripe-Signature";

    private final PagoService pagoService;

    public PagoWebhookController(PagoService pagoService) {
        this.pagoService = pagoService;
    }

    /**
     * Ruta pública: la llama Stripe, no un usuario. Su protección es la firma del aviso, que se calcula sobre el
     * contenido exacto, por eso se recibe como bytes sin interpretar.
     */
    @PostMapping("/webhook")
    @Operation(summary = "Aviso de resultado de pago (webhook de Stripe)",
            description = "Lo llama Stripe, no el frontend. Verifica la firma del encabezado Stripe-Signature y "
                    + "aplica el resultado: pago aprobado (confirma la reserva), rechazado o sesión vencida. Un "
                    + "aviso repetido no tiene efecto. Responde 200 también a los avisos que no interesan, para "
                    + "que Stripe no los reenvíe.")
    @ApiResponse(responseCode = "200", description = "Aviso recibido")
    @ApiResponse(responseCode = "400", description = "Falta la firma o no es válida; el aviso no tiene efecto",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "502", description = "Los pagos no están configurados",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<Void> recibir(
            @RequestBody byte[] cuerpo,
            @RequestHeader(name = ENCABEZADO_FIRMA, required = false) String firma) {
        pagoService.procesarEvento(new String(cuerpo, StandardCharsets.UTF_8), firma);
        return ResponseEntity.ok().build();
    }
}
