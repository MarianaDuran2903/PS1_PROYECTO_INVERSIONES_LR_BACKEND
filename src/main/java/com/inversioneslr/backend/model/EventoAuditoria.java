package com.inversioneslr.backend.model;

import com.inversioneslr.backend.model.enums.AccionAuditoria;
import com.inversioneslr.backend.model.enums.ResultadoAuditoria;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Trazabilidad de acciones relevantes (tabla evento_auditoria, RNF-AUD-001).
 */
@Entity
@Table(name = "evento_auditoria")
public class EventoAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evento")
    private Long idEvento;

    // Nulo cuando el evento no tiene usuario (por ejemplo, un inicio de sesión fallido).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "accion", nullable = false, length = 60)
    private AccionAuditoria accion;

    @Column(name = "tipo_entidad", length = 60)
    private String tipoEntidad;

    @Column(name = "id_entidad", length = 100)
    private String idEntidad;

    @Column(name = "fecha_hora", nullable = false)
    private OffsetDateTime fechaHora;

    @Enumerated(EnumType.STRING)
    @Column(name = "resultado", nullable = false, length = 30)
    private ResultadoAuditoria resultado;

    protected EventoAuditoria() {
        // Requerido por JPA
    }

    public EventoAuditoria(Usuario usuario, AccionAuditoria accion, String tipoEntidad,
                           String idEntidad, ResultadoAuditoria resultado) {
        this.usuario = usuario;
        this.accion = accion;
        this.tipoEntidad = tipoEntidad;
        this.idEntidad = idEntidad;
        this.resultado = resultado;
    }

    @PrePersist
    void asignarFechaHora() {
        if (fechaHora == null) {
            fechaHora = OffsetDateTime.now();
        }
    }

    public Long getIdEvento() {
        return idEvento;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public AccionAuditoria getAccion() {
        return accion;
    }

    public String getTipoEntidad() {
        return tipoEntidad;
    }

    public String getIdEntidad() {
        return idEntidad;
    }

    public OffsetDateTime getFechaHora() {
        return fechaHora;
    }

    public ResultadoAuditoria getResultado() {
        return resultado;
    }
}
