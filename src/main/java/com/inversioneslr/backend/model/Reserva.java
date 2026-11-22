package com.inversioneslr.backend.model;

import com.inversioneslr.backend.model.enums.EstadoReserva;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Reserva de un alojamiento por un Huésped (tabla reserva). Nace PENDIENTE_PAGO, con el precio y la política de
 * cancelación que regían al crearla, y ocupa las fechas hasta que se paga o vence.
 */
@Entity
@Table(name = "reserva")
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_reserva")
    private Long idReserva;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_huesped", nullable = false, updatable = false)
    private Usuario huesped;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_publicacion", nullable = false, updatable = false)
    private Publicacion publicacion;

    @Column(name = "fecha_llegada", nullable = false, updatable = false)
    private LocalDate fechaLlegada;

    @Column(name = "fecha_salida", nullable = false, updatable = false)
    private LocalDate fechaSalida;

    @Column(name = "numero_huespedes", nullable = false, updatable = false)
    private Integer numeroHuespedes;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoReserva estado;

    @Column(name = "clave_idempotencia", nullable = false, length = 100, updatable = false)
    private String claveIdempotencia;

    @Column(name = "precio_base", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal precioBase;

    @Column(name = "porcentaje_descuento_aplicado", nullable = false, precision = 5, scale = 2, updatable = false)
    private BigDecimal porcentajeDescuentoAplicado;

    @Column(name = "valor_descuento", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal valorDescuento;

    @Column(name = "tarifa_limpieza", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal tarifaLimpieza;

    @Column(name = "porcentaje_tarifa_servicio", nullable = false, precision = 5, scale = 2, updatable = false)
    private BigDecimal porcentajeTarifaServicio;

    @Column(name = "tarifa_servicio", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal tarifaServicio;

    @Column(name = "total", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal total;

    @Column(name = "horas_anticipacion_aplicada", nullable = false, updatable = false)
    private Integer horasAnticipacionAplicada;

    @Column(name = "porcentaje_devolucion_aplicado", nullable = false, precision = 5, scale = 2, updatable = false)
    private BigDecimal porcentajeDevolucionAplicado;

    @Column(name = "fecha_expiracion")
    private OffsetDateTime fechaExpiracion;

    protected Reserva() {
        // Requerido por JPA
    }

    private Reserva(Usuario huesped, Publicacion publicacion, LocalDate fechaLlegada, LocalDate fechaSalida,
                    int numeroHuespedes, String claveIdempotencia, DesglosePrecio precio,
                    PoliticaCancelacion politica, OffsetDateTime fechaExpiracion) {
        this.huesped = huesped;
        this.publicacion = publicacion;
        this.fechaLlegada = fechaLlegada;
        this.fechaSalida = fechaSalida;
        this.numeroHuespedes = numeroHuespedes;
        this.estado = EstadoReserva.PENDIENTE_PAGO;
        this.claveIdempotencia = claveIdempotencia;
        this.precioBase = precio.precioBase();
        this.porcentajeDescuentoAplicado = precio.porcentajeDescuento();
        this.valorDescuento = precio.valorDescuento();
        this.tarifaLimpieza = precio.tarifaLimpieza();
        this.porcentajeTarifaServicio = precio.porcentajeTarifaServicio();
        this.tarifaServicio = precio.tarifaServicio();
        this.total = precio.total();
        this.horasAnticipacionAplicada = politica.getHorasAnticipacionMin();
        this.porcentajeDevolucionAplicado = politica.getPorcentajeDevolucion();
        this.fechaExpiracion = fechaExpiracion;
    }

    /**
     * Crea la reserva PENDIENTE_PAGO. Copia el precio y la política de cancelación vigentes: lo que el Huésped
     * vio al reservar es lo que se le cobra y lo que se le devuelve, aunque después cambien.
     */
    public static Reserva preReservar(Usuario huesped, Publicacion publicacion, LocalDate fechaLlegada,
                                      LocalDate fechaSalida, int numeroHuespedes, String claveIdempotencia,
                                      DesglosePrecio precio, PoliticaCancelacion politica,
                                      OffsetDateTime fechaExpiracion) {
        return new Reserva(huesped, publicacion, fechaLlegada, fechaSalida, numeroHuespedes, claveIdempotencia,
                precio, politica, fechaExpiracion);
    }

    public long noches() {
        return ChronoUnit.DAYS.between(fechaLlegada, fechaSalida);
    }

    public boolean estaPendienteDePago() {
        return estado == EstadoReserva.PENDIENTE_PAGO;
    }

    /**
     * Si ya espera el pago y su plazo terminó en el instante dado.
     */
    public boolean estaVencida(OffsetDateTime ahora) {
        return estaPendienteDePago() && !ahora.isBefore(fechaExpiracion);
    }

    /**
     * El plazo para pagar terminó: queda CANCELADA y deja de ocupar fechas. El servicio libera además el periodo
     * del calendario.
     */
    public void vencer() {
        if (!estaPendienteDePago()) {
            throw new IllegalStateException("La reserva no está pendiente de pago: " + estado);
        }
        this.estado = EstadoReserva.CANCELADA;
    }

    /**
     * El pago fue aprobado: la reserva queda CONFIRMADA y conserva las fechas que ya ocupaba.
     */
    public void confirmar() {
        if (!estaPendienteDePago()) {
            throw new IllegalStateException("La reserva no está pendiente de pago: " + estado);
        }
        this.estado = EstadoReserva.CONFIRMADA;
    }

    /**
     * Alarga el plazo para pagar, para que la reserva no venza mientras el Huésped paga en la pasarela. Nunca lo
     * acorta: un plazo anterior al actual se ignora.
     */
    public void extenderPlazoHasta(OffsetDateTime nuevoPlazo) {
        if (!estaPendienteDePago()) {
            throw new IllegalStateException("La reserva no está pendiente de pago: " + estado);
        }
        if (nuevoPlazo.isAfter(fechaExpiracion)) {
            this.fechaExpiracion = nuevoPlazo;
        }
    }

    /**
     * Si la solicitud repetida con la misma clave de idempotencia pide exactamente lo mismo que esta reserva.
     */
    public boolean coincideCon(Long idPublicacion, LocalDate llegada, LocalDate salida, int huespedes) {
        return publicacion.getIdPublicacion().equals(idPublicacion)
                && fechaLlegada.equals(llegada)
                && fechaSalida.equals(salida)
                && numeroHuespedes == huespedes;
    }

    public Long getIdReserva() {
        return idReserva;
    }

    public Usuario getHuesped() {
        return huesped;
    }

    public Publicacion getPublicacion() {
        return publicacion;
    }

    public LocalDate getFechaLlegada() {
        return fechaLlegada;
    }

    public LocalDate getFechaSalida() {
        return fechaSalida;
    }

    public Integer getNumeroHuespedes() {
        return numeroHuespedes;
    }

    public EstadoReserva getEstado() {
        return estado;
    }

    public String getClaveIdempotencia() {
        return claveIdempotencia;
    }

    public BigDecimal getPrecioBase() {
        return precioBase;
    }

    public BigDecimal getPorcentajeDescuentoAplicado() {
        return porcentajeDescuentoAplicado;
    }

    public BigDecimal getValorDescuento() {
        return valorDescuento;
    }

    public BigDecimal getTarifaLimpieza() {
        return tarifaLimpieza;
    }

    public BigDecimal getPorcentajeTarifaServicio() {
        return porcentajeTarifaServicio;
    }

    public BigDecimal getTarifaServicio() {
        return tarifaServicio;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public Integer getHorasAnticipacionAplicada() {
        return horasAnticipacionAplicada;
    }

    public BigDecimal getPorcentajeDevolucionAplicado() {
        return porcentajeDevolucionAplicado;
    }

    public OffsetDateTime getFechaExpiracion() {
        return fechaExpiracion;
    }
}
