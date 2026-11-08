package com.inversioneslr.backend.validation;

/**
 * Formulario que pide una contraseña y su confirmación (registro, restablecimiento). Permite validar ambos
 * con {@link ContrasenasCoinciden}.
 */
public interface ConfirmacionDeContrasena {

    String contrasena();

    String confirmarContrasena();
}
