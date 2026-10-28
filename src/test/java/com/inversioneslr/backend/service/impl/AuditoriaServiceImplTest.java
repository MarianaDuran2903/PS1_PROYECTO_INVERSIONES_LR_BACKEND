package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.model.EventoAuditoria;
import com.inversioneslr.backend.model.NombreCompleto;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.AccionAuditoria;
import com.inversioneslr.backend.model.enums.ResultadoAuditoria;
import com.inversioneslr.backend.model.enums.Rol;
import com.inversioneslr.backend.repository.EventoAuditoriaRepository;
import com.inversioneslr.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceImplTest {

    @Mock
    private EventoAuditoriaRepository eventoAuditoriaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private AuditoriaServiceImpl auditoriaService;

    @Test
    void registrarExito_guardaEventoConResultadoExitoso() {
        Usuario usuario = new Usuario(new NombreCompleto("Ana", null, "Pérez", null),
                "ana@correo.com", "hash", Rol.HUESPED);

        auditoriaService.registrarExito(usuario, AccionAuditoria.REGISTRO_USUARIO, "Usuario", "7");

        ArgumentCaptor<EventoAuditoria> captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(eventoAuditoriaRepository).save(captor.capture());
        EventoAuditoria evento = captor.getValue();
        assertThat(evento.getUsuario()).isSameAs(usuario);
        assertThat(evento.getAccion()).isEqualTo(AccionAuditoria.REGISTRO_USUARIO);
        assertThat(evento.getTipoEntidad()).isEqualTo("Usuario");
        assertThat(evento.getIdEntidad()).isEqualTo("7");
        assertThat(evento.getResultado()).isEqualTo(ResultadoAuditoria.EXITOSO);
    }

    @Test
    void registrarFallo_noPersisteEvento() {
        auditoriaService.registrarFallo("ana@correo.com", AccionAuditoria.REGISTRO_USUARIO, "Usuario",
                "CORREO_DUPLICADO");

        verify(eventoAuditoriaRepository, never()).save(any());
    }

    @Test
    void registrarIntentoFallido_conUsuario_guardaEventoFallidoConReferencia() {
        Usuario referencia = new Usuario(new NombreCompleto("Ana", null, "Pérez", null),
                "ana@correo.com", "hash", Rol.HUESPED);
        when(usuarioRepository.getReferenceById(7L)).thenReturn(referencia);

        auditoriaService.registrarIntentoFallido(7L, "ana@correo.com", AccionAuditoria.ACCESO_DENEGADO,
                "Ruta", "GET /api/v1/admin", "ROL_NO_AUTORIZADO");

        ArgumentCaptor<EventoAuditoria> captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(eventoAuditoriaRepository).save(captor.capture());
        EventoAuditoria evento = captor.getValue();
        assertThat(evento.getUsuario()).isSameAs(referencia);
        assertThat(evento.getAccion()).isEqualTo(AccionAuditoria.ACCESO_DENEGADO);
        assertThat(evento.getIdEntidad()).isEqualTo("GET /api/v1/admin");
        assertThat(evento.getResultado()).isEqualTo(ResultadoAuditoria.FALLIDO);
    }

    @Test
    void registrarIntentoFallido_sinUsuario_guardaEventoSinUsuario() {
        auditoriaService.registrarIntentoFallido(null, "nadie@correo.com", AccionAuditoria.INICIO_SESION,
                "Usuario", null, "CREDENCIALES_INVALIDAS");

        ArgumentCaptor<EventoAuditoria> captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(eventoAuditoriaRepository).save(captor.capture());
        assertThat(captor.getValue().getUsuario()).isNull();
        assertThat(captor.getValue().getResultado()).isEqualTo(ResultadoAuditoria.FALLIDO);
        verify(usuarioRepository, never()).getReferenceById(any());
    }
}
