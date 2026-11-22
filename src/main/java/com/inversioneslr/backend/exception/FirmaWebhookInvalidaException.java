package com.inversioneslr.backend.exception;

/**
 * El aviso de pago no trae una firma válida de la pasarela: falta, está mal formada, es antigua o no coincide.
 * Se responde 400 y el aviso no produce ningún efecto.
 */
public class FirmaWebhookInvalidaException extends RuntimeException {

    public FirmaWebhookInvalidaException() {
        super("La firma del aviso no es válida");
    }
}
