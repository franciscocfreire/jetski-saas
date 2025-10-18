package com.jetski.shared.authorization.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Input enviado para o OPA para decisões de autorização.
 *
 * Estrutura compatível com políticas Rego em policies/authz/
 *
 * @author Jetski Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OPAInput {

    /**
     * Ação sendo executada (ex: "modelo:list", "desconto:aplicar")
     */
    private String action;

    /**
     * Contexto do usuário que está fazendo a requisição
     */
    private UserContext user;

    /**
     * Contexto do recurso sendo acessado
     */
    private ResourceContext resource;

    /**
     * Contexto da operação específica (alçadas, valores, etc)
     */
    private OperationContext operation;

    /**
     * Contexto do usuário
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserContext {
        private String id;
        private String tenant_id;
        private String role;
        private List<String> roles;
        private String email;
    }

    /**
     * Contexto do recurso
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResourceContext {
        private String id;
        private String tenant_id;
        private String type;
        private String status;
        private Map<String, Object> attributes;
    }

    /**
     * Contexto da operação (para alçadas e regras complexas)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OperationContext {
        // Para descontos
        private BigDecimal percentual_desconto;

        // Para aprovação de OS
        private BigDecimal valor_os;

        // Para fechamentos
        private BigDecimal valor_total;

        // Para cancelamentos com contexto temporal
        private Long locacao_inicio_timestamp;

        // Justificativa (para operações que requerem)
        private String justificativa;

        // Atributos extras
        private Map<String, Object> extra;
    }

    /**
     * Factory method: criar input para RBAC simples
     */
    public static OPAInput forRBAC(String action, UUID userId, UUID tenantId, String role, UUID resourceId) {
        return OPAInput.builder()
            .action(action)
            .user(UserContext.builder()
                .id(userId.toString())
                .tenant_id(tenantId.toString())
                .role(role)
                .build())
            .resource(ResourceContext.builder()
                .id(resourceId != null ? resourceId.toString() : null)
                .tenant_id(tenantId.toString())
                .build())
            .build();
    }

    /**
     * Factory method: criar input para alçada de desconto
     */
    public static OPAInput forDesconto(UUID userId, UUID tenantId, String role,
                                       UUID locacaoId, BigDecimal percentual) {
        return OPAInput.builder()
            .action("desconto:aplicar")
            .user(UserContext.builder()
                .id(userId.toString())
                .tenant_id(tenantId.toString())
                .role(role)
                .build())
            .resource(ResourceContext.builder()
                .id(locacaoId.toString())
                .tenant_id(tenantId.toString())
                .type("locacao")
                .build())
            .operation(OperationContext.builder()
                .percentual_desconto(percentual)
                .build())
            .build();
    }
}
