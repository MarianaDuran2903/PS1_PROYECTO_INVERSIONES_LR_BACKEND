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
        actualizarDatos(datos, ciudad, tipo);
    }

    public final void actualizarDatos(DatosPublicacion datos, Ciudad ciudad, TipoAlojamiento tipo) {
        this.titulo = datos.titulo();
        this.descripcion = datos.descripcion();
        this.direccionTextual = datos.direccionTextual();
        this.barrioZona = datos.barrioZona();
        this.capacidad = datos.capacidad();
        this.precioNoche = datos.precioNoche();
        this.ciudad = ciudad;
        this.tipo = tipo;
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
