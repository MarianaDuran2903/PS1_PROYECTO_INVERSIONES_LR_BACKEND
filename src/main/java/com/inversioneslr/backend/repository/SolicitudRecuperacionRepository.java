package com.inversioneslr.backend.repository;

import com.inversioneslr.backend.model.SolicitudRecuperacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface SolicitudRecuperacionRepository extends JpaRepository<SolicitudRecuperacion, Long> {

    Optional<SolicitudRecuperacion> findByTokenHash(String tokenHash);

    /**
     * Cierra las solicitudes vigentes del usuario, para que solo funcione el último enlace entregado.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SolicitudRecuperacion s SET s.fechaUso = :ahora "
            + "WHERE s.usuario.idUsuario = :idUsuario AND s.fechaUso IS NULL")
    int cerrarVigentes(@Param("idUsuario") Long idUsuario, @Param("ahora") OffsetDateTime ahora);
}
