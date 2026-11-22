package com.inversioneslr.backend.model;

import com.inversioneslr.backend.model.enums.EstadoPago;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Pago de una reserva con Stripe Checkout en modo de pruebas (tabla pago_simulado). Cada pago corresponde a una
 * sesión de Checkout; su estado lo decide solo el resultado que informa Stripe, nunca el Huésped.
 */
@Entity
@Table(name = "pago_simulado")
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pago")
    private Long idPago;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_reserva", nullable = false, updatable = false)
    private Reserva reserva;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal monto;

    @Column(name = "moneda", nullable = false, length = 3, updatable = false)
    private String moneda;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoPago estado;

    @Column(name = "referencia_stripe", nullable = false, length = 255, updatable = false)
    private String referenciaStripe;

    @Column(name = "id_payment_intent", length = 255)
    private String idPaymentIntent;

    @Column(name = "codigo_rechazo", length = 100)
    private String codigoRechazo;

    @Column(name = "url_pago", length = 2048, updatable = false)
    private String urlPago;

    @Column(name = "fecha_expiracion", updatable = false)
    private OffsetDateTime fechaExpiracion;

    @Column(name = "fecha_hora", nullable = false, updatable = false)
    private OffsetDateTime fechaHora;

    @Column(name = "fecha_actualizacion", nullable = false)
    private OffsetDateTime fechaActualizacion;

    protected Pago() {
        // Requerido por JPA
    }

    private Pago(Reserva reserva, String referenciaStripe, String urlPago, OffsetDateTime fechaExpiracion,
                 String moneda, OffsetDateTime ahora) {
        this.reserva = reserva;
        // El monto es siempre el de la reserva, que no cambia: nunca un valor que llegue en una solicitud.
        this.monto = reserva.getTotal();
        this.moneda = moneda;
        this.estado = EstadoPago.PENDIENTE;
        this.referenciaStripe = referenciaStripe;
        this.urlPago = urlPago;
        this.fechaExpiracion = fechaExpiracion;
        this.fechaHora = ahora;
        this.fechaActualizacion = ahora;
    }

    /**
     * Crea el pago PENDIENTE de una reserva, ligado a la sesión de Checkout que se abrió para cobrarlo.
     */
    public static Pago iniciar(Reserva reserva, String referenciaStripe, String urlPago,
                               OffsetDateTime fechaExpiracion, String moneda, OffsetDateTime ahora) {
        return new Pago(reserva, referenciaStripe, urlPago, fechaExpiracion, moneda, ahora);
    }

    /**
     * Si la sesión admite todavía el pago: no es final y su plazo no terminó.
     */
    public boolean sesionVigente(OffsetDateTime ahora) {
        return estado.estaAbierto() && fechaExpiracion.isAfter(ahora);
    }

    /**
     * Si el monto y la moneda cobrados son los de este pago. Se comprueba antes de confirmar la reserva.
     */
    public boolean coincideCon(BigDecimal montoCobrado, String monedaCobrada) {
        return montoCobrado != null
                && monto.compareTo(montoCobrado) == 0
                && moneda.equalsIgnoreCase(monedaCobrada);
    }

    /**
     * Stripe cobró. APROBADO es final: un aviso repetido no lo cambia (devuelve false). Se aprueba también un pago
     * RECHAZADO, porque el Huésped puede reintentar con otra tarjeta en la misma sesión.
     *
     * @return si el pago cambió a APROBADO
     */
    public boolean aprobar(String idPaymentIntentCobrado, OffsetDateTime ahora) {
        if (estado == EstadoPago.APROBADO) {
            return false;
        }
        this.estado = EstadoPago.APROBADO;
        this.idPaymentIntent = idPaymentIntentCobrado;
        this.codigoRechazo = null;
        this.fechaActualizacion = ahora;
        return true;
    }

    /**
     * Un intento falló. Solo cambia una sesión abierta: un pago ya APROBADO o EXPIRADO no se degrada por un aviso
     * tardío o desordenado.
     *
     * @return si el pago quedó RECHAZADO
     */
    public boolean rechazar(String codigo, String idPaymentIntentRechazado, OffsetDateTime ahora) {
        if (!estado.estaAbierto()) {
            return false;
        }
        this.estado = EstadoPago.RECHAZADO;
        this.codigoRechazo = codigo;
        if (idPaymentIntentRechazado != null) {
            this.idPaymentIntent = idPaymentIntentRechazado;
        }
        this.fechaActualizacion = ahora;
        return true;
    }

    /**
     * La sesión venció sin pago. Solo cambia una sesión abierta.
     *
     * @return si el pago quedó EXPIRADO
     */
    public boolean expirar(OffsetDateTime ahora) {
        if (!estado.estaAbierto()) {
            return false;
        }
        this.estado = EstadoPago.EXPIRADO;
        this.fechaActualizacion = ahora;
        return true;
    }

    public Long getIdPago() {
        return idPago;
    }

    public Reserva getReserva() {
        return reserva;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public String getMoneda() {
        return moneda;
    }

    public EstadoPago getEstado() {
        return estado;
    }

    public String getReferenciaStripe() {
        return referenciaStripe;
    }

    public String getIdPaymentIntent() {
        return idPaymentIntent;
    }

    public String getCodigoRechazo() {
        return codigoRechazo;
    }

    public String getUrlPago() {
        return urlPago;
    }

    public OffsetDateTime getFechaExpiracion() {
        return fechaExpiracion;
    }

    public OffsetDateTime getFechaHora() {
        return fechaHora;
    }

    public OffsetDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }
}
