package com.inversioneslr.backend.validation;

import com.inversioneslr.backend.dto.request.RegistroUsuarioRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Objects;

public class ContrasenasCoincidenValidator
        implements ConstraintValidator<ContrasenasCoinciden, RegistroUsuarioRequest> {

    static final String CAMPO_CONFIRMACION = "confirmarContrasena";

    @Override
    public boolean isValid(RegistroUsuarioRequest request, ConstraintValidatorContext context) {
        if (request == null || request.confirmarContrasena() == null) {
            return true; // La obligatoriedad de la confirmación la valida @NotBlank
        }
        if (Objects.equals(request.contrasena(), request.confirmarContrasena())) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode(CAMPO_CONFIRMACION)
                .addConstraintViolation();
        return false;
    }
}
