package com.inversioneslr.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Solicitud de recuperación de contraseña (tabla solicitud_recuperacion). Guarda solo el hash del token y es
 * de un solo uso.
 */
@Entity
@Table(name = "solicitud_recuperacion")
public class SolicitudRecuperacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_solicitud")
    private Long idSolicitud;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false, updatable = false)
    private Usuario usuario;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "fecha_solicitud", nullable = false, updatable = false)
    private OffsetDateTime fechaSolicitud;

    @Column(name = "fecha_expiracion", nullable = false, updatable = false)
    private OffsetDateTime fechaExpiracion;

    @Column(name = "fecha_uso")
    private OffsetDateTime fechaUso;

    protected SolicitudRecuperacion() {
        // Requerido por JPA
    }

    public SolicitudRecuperacion(Usuario usuario, String tokenHash, OffsetDateTime fechaSolicitud,
                                 OffsetDateTime fechaExpiracion) {
        this.usuario = usuario;
        this.tokenHash = tokenHash;
        this.fechaSolicitud = fechaSolicitud;
        this.fechaExpiracion = fechaExpiracion;
    }

    public boolean estaUsada() {
        return fechaUso != null;
    }

    public boolean estaVencida(OffsetDateTime ahora) {
        return !ahora.isBefore(fechaExpiracion);
    }

    public void marcarUsada(OffsetDateTime ahora) {
        this.fechaUso = ahora;
    }

    public Long getIdSolicitud() {
        return idSolicitud;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public OffsetDateTime getFechaSolicitud() {
        return fechaSolicitud;
    }

    public OffsetDateTime getFechaExpiracion() {
        return fechaExpiracion;
    }

    public OffsetDateTime getFechaUso() {
        return fechaUso;
    }
}
