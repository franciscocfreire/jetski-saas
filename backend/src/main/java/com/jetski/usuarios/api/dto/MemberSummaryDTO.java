package com.jetski.usuarios.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO: Member Summary
 *
 * Summary information about a tenant member.
 *
 * @author Jetski Team
 * @since 0.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberSummaryDTO {
    private UUID usuarioId;
    private String email;
    private String nome;
    private String[] papeis;
    private boolean ativo;
    private Instant joinedAt;  // created_at from membro
    private Instant lastUpdated;
}
