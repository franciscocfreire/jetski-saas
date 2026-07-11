package com.jetski.tenant;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Catálogo de módulos gateáveis por plano (controle de oferta, V046).
 *
 * <p>O super admin define em {@code plano.modulos} (jsonb array de chaves
 * deste enum; NULL = todos) quais módulos cada plano inclui. O gating atua em
 * duas camadas: o MENU do backoffice (itens somem) e a API (interceptor
 * {@code ModuloPlanoInterceptor} casa o sub-path após {@code /v1/tenants/{id}/}
 * com {@link #patterns()} e nega com mensagem de upgrade).
 *
 * <p>O core operacional (agenda, balcão, frota, clientes, locações, reservas,
 * auditoria, configurações) NÃO é gateável — sem ele a loja não opera.
 */
public enum ModuloPlano {

    EMISSAO_MARINHA(
        "Emissão à Marinha",
        "GRU automática, documentação NORMAM-212 (EMA/CHA) e instrutores",
        List.of("^(documentos|grus|instrutores)(/|$)",
                "^reservas/[^/]+/(habilitacao/gru|emitir-documentos)")),

    COMISSOES(
        "Comissões e vendedores",
        "Cadastro de vendedores, políticas de comissão, presença e pagamentos",
        List.of("^(comissoes|politicas-comissao|vendedores|pagamentos|presencas)(/|$)")),

    MANUTENCAO(
        "Manutenção",
        "Ordens de serviço e bloqueio de agenda por manutenção",
        List.of("^manutencoes(/|$)")),

    FECHAMENTOS(
        "Fechamentos",
        "Fechamento diário por forma de pagamento e mensal com comissões",
        List.of("^fechamentos(/|$)")),

    RELATORIOS(
        "Relatórios e dashboard financeiro",
        "Relatórios operacionais e visão financeira consolidada",
        List.of("^dashboard/financeiro(/|$)")),

    DESPESAS(
        "Despesas operacionais",
        "Lançamento e acompanhamento de despesas da operação",
        List.of("^despesas-operacionais(/|$)"));

    private final String rotulo;
    private final String descricao;
    private final List<Pattern> patterns;

    ModuloPlano(String rotulo, String descricao, List<String> regexes) {
        this.rotulo = rotulo;
        this.descricao = descricao;
        this.patterns = regexes.stream().map(Pattern::compile).toList();
    }

    public String rotulo() {
        return rotulo;
    }

    public String descricao() {
        return descricao;
    }

    /** Padrões casados contra o sub-path após {@code /v1/tenants/{id}/}. */
    public List<Pattern> patterns() {
        return patterns;
    }

    /** True se o sub-path pertence a este módulo. */
    public boolean cobre(String subPath) {
        return patterns.stream().anyMatch(p -> p.matcher(subPath).find());
    }
}
