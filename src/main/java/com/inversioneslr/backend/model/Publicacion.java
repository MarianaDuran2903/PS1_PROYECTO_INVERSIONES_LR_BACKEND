package com.inversioneslr.backend.model;

import com.inversioneslr.backend.model.enums.EstadoPublicacion;
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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Alojamiento publicado por un Anfitrión (tabla publicacion). Nace en BORRADOR y queda asociado para siempre
 * al Anfitrión que lo creó.
 */
@Entity
@Table(name = "publicacion")
public class Publicacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_publicacion")
    private Long idPublicacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_anfitrion", nullable = false, updatable = false)
    private Usuario anfitrion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_ciudad", nullable = false)
    private Ciudad ciudad;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tipo", nullable = false)
    private TipoAlojamiento tipo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_politica", nullable = false)
    private PoliticaCancelacion politica;

    @Column(name = "titulo", nullable = false, length = 150)
    private String titulo;

    @Column(name = "descripcion", columnDefinition = "text")
    private String descripcion;

    @Column(name = "direccion_textual", length = 250)
    private String direccionTextual;

    @Column(name = "barrio_zona", length = 120)
    private String barrioZona;

    @Column(name = "capacidad", nullable = false)
    private Integer capacidad;

    @Column(name = "precio_noche", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioNoche;

    @Column(name = "tarifa_limpieza", nullable = false, precision = 12, scale = 2)
    private BigDecimal tarifaLimpieza = BigDecimal.ZERO;

    @Column(name = "reglas", columnDefinition = "text")
    private String reglas;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoPublicacion estado = EstadoPublicacion.BORRADOR;

    protected Publicacion() {
        // Requerido por JPA
    }

    /**
     * Crea una publicación en BORRADOR para el Anfitrión indicado.
     */
    public Publicacion(Usuario anfitrion, PoliticaCancelacion politica, DatosPublicacion datos,
                       Ciudad ciudad, TipoAlojamiento tipo) {
        this.anfitrion = anfitrion;
        this.politica = politica;
        asignarDatos(datos, ciudad, tipo);
    }

    /**
     * Privado para que el constructor no llame a un método que Hibernate pueda sobrescribir en sus proxies.
     */
    private void asignarDatos(DatosPublicacion datos, Ciudad ciudad, TipoAlojamiento tipo) {
        this.titulo = datos.titulo();
        this.descripcion = datos.descripcion();
        this.direccionTextual = datos.direccionTextual();
        this.barrioZona = datos.barrioZona();
        this.capacidad = datos.capacidad();
        this.precioNoche = datos.precioNoche();
        this.ciudad = ciudad;
        this.tipo = tipo;
    }

    /**
     * Reemplaza la información editable. Las reglas en blanco quedan nulas.
     */
    public void editar(DatosPublicacion datos, Ciudad ciudad, TipoAlojamiento tipo, BigDecimal tarifaLimpieza,
                       String reglas) {
        asignarDatos(datos, ciudad, tipo);
        this.tarifaLimpieza = tarifaLimpieza;
        this.reglas = reglas == null || reglas.isBlank() ? null : reglas.strip();
    }

    /**
     * Campos que faltan para pedir la revisión, con el mensaje de cada uno. Vacío si la información mínima
     * está completa.
     */
    public Map<String, String> informacionFaltante() {
        Map<String, String> faltantes = new LinkedHashMap<>();
        if (estaEnBlanco(descripcion)) {
            faltantes.put("descripcion", "La descripción es obligatoria para solicitar la activación");
        }
        if (estaEnBlanco(direccionTextual)) {
            faltantes.put("direccionTextual", "La dirección es obligatoria para solicitar la activación");
        }
        if (estaEnBlanco(barrioZona)) {
            faltantes.put("barrioZona", "El barrio o zona es obligatorio para solicitar la activación");
        }
        if (estaEnBlanco(reglas)) {
            faltantes.put("reglas", "Las reglas son obligatorias para solicitar la activación");
        }
        return faltantes;
    }

    /**
     * Pasa a PENDIENTE_REVISION. El servicio verifica antes la transición y la información mínima.
     */
    public void solicitarRevision() {
        pasarA(EstadoPublicacion.PENDIENTE_REVISION);
    }

    public boolean estaPendienteDeRevision() {
        return estado == EstadoPublicacion.PENDIENTE_REVISION;
    }

    /**
     * Decisión del Administrador sobre una publicación pendiente: queda ACTIVA. El servicio verifica antes la
     * información mínima.
     */
    public void aprobar() {
        exigirPendienteDeRevision();
        pasarA(EstadoPublicacion.ACTIVA);
    }

    /**
     * Decisión del Administrador sobre una publicación pendiente: vuelve a BORRADOR para que el Anfitrión la
     * corrija y la reenvíe.
     */
    public void rechazar() {
        exigirPendienteDeRevision();
        pasarA(EstadoPublicacion.BORRADOR);
    }

    private void exigirPendienteDeRevision() {
        if (!estaPendienteDeRevision()) {
            throw new IllegalStateException("La publicación no está pendiente de revisión: " + estado);
        }
    }

    private void pasarA(EstadoPublicacion destino) {
        if (!estado.puedePasarA(destino)) {
            throw new IllegalStateException("No se puede pasar de " + estado + " a " + destino);
        }
        this.estado = destino;
    }

    private static boolean estaEnBlanco(String valor) {
        return valor == null || valor.isBlank();
    }

    public boolean perteneceA(Long idUsuario) {
        return anfitrion.getIdUsuario().equals(idUsuario);
    }

    public Long getIdPublicacion() {
        return idPublicacion;
    }

    public Usuario getAnfitrion() {
        return anfitrion;
    }

    public Ciudad getCiudad() {
        return ciudad;
    }

    public TipoAlojamiento getTipo() {
        return tipo;
    }

    public PoliticaCancelacion getPolitica() {
        return politica;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getDireccionTextual() {
        return direccionTextual;
    }

    public String getBarrioZona() {
        return barrioZona;
    }

    public Integer getCapacidad() {
        return capacidad;
    }

    public BigDecimal getPrecioNoche() {
        return precioNoche;
    }

    public BigDecimal getTarifaLimpieza() {
        return tarifaLimpieza;
    }

    public String getReglas() {
        return reglas;
    }

    public EstadoPublicacion getEstado() {
        return estado;
    }
}
