package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.internal.AssinaturaCertificado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssinaturaCertificadoRepository extends JpaRepository<AssinaturaCertificado, UUID> {

    /** Certificado ativo mais recente da plataforma (linha única na prática). */
    Optional<AssinaturaCertificado> findFirstByOrderByCreatedAtDesc();
}
