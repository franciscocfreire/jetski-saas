package com.jetski.shared.security;

import java.util.UUID;

/**
 * Sessão de suporte ATIVA no request corrente (F3).
 *
 * <p>Substitui o god mode implícito: até a F2, um operador de plataforma trocava de empresa
 * no switcher do backoffice e operava como se fosse membro dela — sem motivo declarado, sem
 * prazo e sem trilha. Agora entrar numa empresa é ato explícito, e este record é o que o
 * resto do sistema enxerga dele.
 *
 * <p>Vive no {@link TenantContext} durante o request e é propagado ao OPA, que usa
 * {@link #somenteLeitura()} para negar escrita.
 *
 * @param id             id da sessão (carimbado na auditoria de tudo que for feito)
 * @param operadorId     operador de plataforma por trás da sessão
 * @param tenantId       empresa alvo
 * @param somenteLeitura true = qualquer método diferente de GET é negado
 */
public record SessaoSuporte(UUID id, UUID operadorId, UUID tenantId, boolean somenteLeitura) {
}
