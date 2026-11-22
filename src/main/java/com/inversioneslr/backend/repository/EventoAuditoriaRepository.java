package com.inversioneslr.backend.repository;

import com.inversioneslr.backend.model.EventoAuditoria;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoAuditoriaRepository extends JpaRepository<EventoAuditoria, Long> {
}
