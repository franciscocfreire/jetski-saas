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
    /** Módulos do plano (V046); null = todos — usado no gating do menu. */
    private List<String> modulos;
    /**
     * Videoaula da Marinha exigida no balcão (via EMA) — regra efetiva (V063):
     * sem o módulo VIDEO_ORIENTACAO é sempre true; com ele, vale o toggle da empresa.
     * Vem aqui porque o OPERADOR (quem atende o balcão) não lê /config/documento.
     */
    private Boolean videoaulaObrigatoria;
    /**
     * Papel na emissão (EMISSAO_DELEGADA_SPEC §8.M): EMISSORA, DELEGADA ou NENHUM.
     * O switcher agrupa as delegadas sob a EAMA emissora delas.
     */
    private String papelEmissao;
    /** Na delegada: a EAMA emissora da parceria em vigor (pode não estar na lista do usuário). */
    private UUID emissoraTenantId;
    private String emissoraNome;
}
