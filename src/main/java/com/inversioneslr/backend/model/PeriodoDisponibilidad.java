package com.inversioneslr.backend.model;

import com.inversioneslr.backend.model.enums.TipoPeriodo;
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

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Tramo del calendario de una publicación (tabla periodo_disponibilidad). Va de fechaInicio, incluida, a
 * fechaFin, excluida, y los tramos de una misma publicación no se superponen. Un tramo RESERVADO pertenece a
 * una reserva; los demás no.
 */
@Entity
@Table(name = "periodo_disponibilidad")
public class PeriodoDisponibilidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_periodo")
    private Long idPeriodo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_publicacion", nullable = false, updatable = false)
    private Publicacion publicacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_reserva")
    private Reserva reserva;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 11)
    private TipoPeriodo tipo;

    protected PeriodoDisponibilidad() {
        // Requerido por JPA
    }

    private PeriodoDisponibilidad(Publicacion publicacion, Reserva reserva, LocalDate fechaInicio,
                                  LocalDate fechaFin, TipoPeriodo tipo) {
        if (!fechaFin.isAfter(fechaInicio)) {
            throw new IllegalArgumentException("La fecha fin debe ser posterior a la fecha inicio");
        }
        this.publicacion = publicacion;
        this.reserva = reserva;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.tipo = tipo;
    }

    public static PeriodoDisponibilidad disponible(Publicacion publicacion, LocalDate fechaInicio,
                                                   LocalDate fechaFin) {
        return new PeriodoDisponibilidad(publicacion, null, fechaInicio, fechaFin, TipoPeriodo.DISPONIBLE);
    }

    public static PeriodoDisponibilidad reservado(Publicacion publicacion, Reserva reserva, LocalDate fechaInicio,
                                                  LocalDate fechaFin) {
        return new PeriodoDisponibilidad(publicacion, reserva, fechaInicio, fechaFin, TipoPeriodo.RESERVADO);
    }

    /**
     * Cierra un periodo disponible sin eliminarlo del calendario.
     */
    public void cerrarComoNoOfrecido() {
        if (tipo != TipoPeriodo.DISPONIBLE || reserva != null) {
            throw new IllegalStateException("Solo se puede cerrar un periodo disponible sin reserva");
        }
        this.tipo = TipoPeriodo.NO_OFRECIDO;
    }

    /**
     * Noches de este tramo que caen dentro de [llegada, salida). Cero si no se tocan.
     */
    public long nochesEnComun(LocalDate llegada, LocalDate salida) {
        LocalDate desde = fechaInicio.isAfter(llegada) ? fechaInicio : llegada;
        LocalDate hasta = fechaFin.isBefore(salida) ? fechaFin : salida;
        return Math.max(0, ChronoUnit.DAYS.between(desde, hasta));
    }

    /**
     * Si los tramos cubren todas las noches de [llegada, salida), sin huecos. Los tramos de una publicación no se
     * superponen entre sí, así que basta con que sus noches en común sumen las noches pedidas. Una estadía sin
     * noches no se cubre.
     */
    public static boolean cubren(List<PeriodoDisponibilidad> tramos, LocalDate llegada, LocalDate salida) {
        long noches = ChronoUnit.DAYS.between(llegada, salida);
        long cubiertas = tramos.stream().mapToLong(tramo -> tramo.nochesEnComun(llegada, salida)).sum();
        return noches > 0 && cubiertas == noches;
    }

    /**
     * La reserva dejó de ocupar el tramo: vuelve a estar DISPONIBLE y sin reserva asociada.
     */
    public void liberar() {
        if (tipo != TipoPeriodo.RESERVADO) {
            throw new IllegalStateException("El periodo no está reservado: " + tipo);
        }
        this.tipo = TipoPeriodo.DISPONIBLE;
        this.reserva = null;
    }

    public Long getIdPeriodo() {
        return idPeriodo;
    }

    public Publicacion getPublicacion() {
        return publicacion;
    }

    public Reserva getReserva() {
        return reserva;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public LocalDate getFechaFin() {
        return fechaFin;
    }

    public TipoPeriodo getTipo() {
        return tipo;
    }
}
