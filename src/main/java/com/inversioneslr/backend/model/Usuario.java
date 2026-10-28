package com.inversioneslr.backend.model;

import com.inversioneslr.backend.model.enums.Rol;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Supertipo de los roles HUESPED, ANFITRION y ADMINISTRADOR (tabla usuario, MER v0.3).
 */
@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Long idUsuario;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "correo", nullable = false, length = 254)
    private String correo;

    @Column(name = "contrasena_hash", nullable = false, length = 255)
    private String contrasenaHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, length = 15)
    private Rol rol;

    @Column(name = "cuenta_activa", nullable = false)
    private boolean cuentaActiva = true;

    @Column(name = "restringido", nullable = false)
    private boolean restringido = false;

    protected Usuario() {
        // Requerido por JPA
    }

    public Usuario(String nombre, String correo, String contrasenaHash, Rol rol) {
        this.nombre = nombre;
        this.correo = correo;
        this.contrasenaHash = contrasenaHash;
        this.rol = rol;
    }

    public Long getIdUsuario() {
        return idUsuario;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCorreo() {
        return correo;
    }

    public String getContrasenaHash() {
        return contrasenaHash;
    }

    public Rol getRol() {
        return rol;
    }

    public boolean isCuentaActiva() {
        return cuentaActiva;
    }

    public boolean isRestringido() {
        return restringido;
    }
}
