package com.jetski.usuarios.api.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

/**
 * DTO: TenantSummary
 *
 * Represents a single tenant in the user's access list.
 * Contains full tenant info (id, slug, razaoSocial, status) and user's roles.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TenantSummary {
    private UUID id;
    private String slug;
    private String razaoSocial;
    private String status;
    private List<String> roles;
}
