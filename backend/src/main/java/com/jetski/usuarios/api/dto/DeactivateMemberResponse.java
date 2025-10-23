package com.jetski.usuarios.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO: Deactivate Member Response
 *
 * Response after deactivating a tenant member.
 *
 * @author Jetski Team
 * @since 0.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeactivateMemberResponse {
    private boolean success;
    private String message;
    private UUID usuarioId;
    private String email;
    private UUID tenantId;

    public static DeactivateMemberResponse success(UUID usuarioId, String email, UUID tenantId) {
        return DeactivateMemberResponse.builder()
            .success(true)
            .message("Membro desativado com sucesso")
            .usuarioId(usuarioId)
            .email(email)
            .tenantId(tenantId)
            .build();
    }
}
