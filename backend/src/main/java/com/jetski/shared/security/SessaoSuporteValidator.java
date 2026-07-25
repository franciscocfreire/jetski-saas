package com.jetski.shared.security;

/**
 * Validação de sessão de suporte para o {@code TenantFilter}.
 *
 * <p>Mesma inversão de dependência do {@link TenantAccessValidator}: o módulo {@code shared}
 * define o contrato e o módulo dono da tabela implementa, sem ciclo.
 *
 * @since 0.9.0
 */
public interface SessaoSuporteValidator {

    /**
     * Resolve o cookie de suporte numa sessão ativa.
     *
     * <p>Não lança: cookie inválido, expirado, encerrado ou desconhecido devolve
     * {@code null} e o request segue como um request normal (sem acesso à empresa) —
     * quem nega é a autorização, não o filtro.
     *
     * @param token valor cru do cookie (o banco guarda apenas o SHA-256)
     * @return sessão ativa ou {@code null}
     */
    SessaoSuporte validar(String token);
}
