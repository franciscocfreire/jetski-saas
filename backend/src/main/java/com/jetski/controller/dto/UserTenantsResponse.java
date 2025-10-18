package com.jetski.controller.dto;

import com.jetski.domain.entity.Membro;
import lombok.*;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * DTO: UserTenantsResponse
 *
 * Response for GET /api/v1/user/tenants endpoint.
 * Lists all tenants the user has access to.
 *
 * Two types of responses:
 * 1. LIMITED: User has specific tenant memberships (returns list)
 * 2. UNRESTRICTED: User is platform admin (returns indicator only)
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserTenantsResponse {

    /**
     * Type of access: "LIMITED" or "UNRESTRICTED"
     */
    private String accessType;

    /**
     * Total number of tenants user can access
     * -1 means unrestricted (infinite)
     */
    private Long totalTenants;

    /**
     * Message for unrestricted users
     */
    private String message;

    /**
     * List of tenant summaries (empty for unrestricted)
     */
    private List<TenantSummary> tenants;

    /**
     * Factory method: Unrestricted access
     */
    public static UserTenantsResponse unrestricted() {
        return UserTenantsResponse.builder()
            .accessType("UNRESTRICTED")
            .totalTenants(-1L)
            .message("Full platform access - use tenant search")
            .tenants(List.of())
            .build();
    }

    /**
     * Factory method: Limited access
     */
    public static UserTenantsResponse limited(List<Membro> membros, long total) {
        return UserTenantsResponse.builder()
            .accessType("LIMITED")
            .totalTenants(total)
            .message(null)
            .tenants(membros.stream()
                .map(m -> TenantSummary.builder()
                    .tenantId(m.getTenantId())
                    .roles(Arrays.asList(m.getPapeis()))
                    .build())
                .collect(Collectors.toList()))
            .build();
    }
}

/**
 * DTO: TenantSummary
 *
 * Represents a single tenant in the user's access list.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
class TenantSummary {
    private UUID tenantId;
    private List<String> roles;
}
