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

    // Split V047 (emissão delegada): a antiga EMISSAO_MARINHA virou EMISSAO_PROPRIA.
    // Um mesmo path pode ser coberto pelos dois módulos de emissão (documentos/grus);
    // o interceptor libera se QUALQUER módulo cobridor estiver no plano. O cadastro
    // de instrutores é exclusivo da emissão própria (operadora usa os da EAMA parceira).
    EMISSAO_PROPRIA(
        "Emissão à Marinha — própria",
        "GRU automática, documentação NORMAM-212 (EMA/CHA) e instrutores, emitindo em nome da própria empresa (EAMA licenciada)",
        List.of("^(documentos|grus|instrutores)(/|$)",
                "^reservas/[^/]+/(habilitacao/gru|emitir-documentos)")),

    // "instrutores" também é coberto pela delegada: a operadora cadastra instrutores
    // próprios, que só assinam emissão delegada depois de aprovados pela EAMA da
    // parceria (V070); os da EAMA vêm pela designação (resolverParaEmissao).
    EMISSAO_DELEGADA(
        "Emissão à Marinha — delegada",
        "GRU automática e documentação NORMAM-212 emitida em nome de uma EAMA parceira (instrutores da emissora)",
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
        List.of("^despesas-operacionais(/|$)")),

    // Os três módulos abaixo não têm endpoint tenant-scoped gateável (patterns
    // vazios): o enforcement é nas consultas PÚBLICAS (MarketplaceService,
    // CustomerReservaService, disponibilidade) via moduloHabilitado.
    MARKETPLACE(
        "Marketplace",
        "Vitrine dos seus jetskis no marketplace público do Meu Jet",
        List.of()),

    LOJA_ONLINE(
        "Loja online",
        "Vitrine própria da loja, com os modelos e os dados de contato",
        List.of()),

    // Separado da Loja online (V072): sem ele o cliente vê o modelo mas só fala pelo WhatsApp.
    RESERVA_ONLINE(
        "Reserva online (Reservar Agora)",
        "Botão Reservar Agora no marketplace e na vitrine, com reserva e sinal PIX pelo portal do cliente (sem o módulo, só o contato por WhatsApp)",
        List.of()),

    // Módulo "de permissão": o passo Orientações do balcão SEMPRE exibe a videoaula
    // da Marinha (via EMA); por padrão é obrigatório assistir até o fim para
    // continuar. Ter este módulo no plano libera o toggle em Configurações ›
    // Documentos para a empresa desligar essa obrigação. Enforcement programático
    // (TenantConfigService, EmissaoService, UserTenantsController).
    VIDEO_ORIENTACAO(
        "Videoaula no balcão — configurável",
        "Permite à empresa desligar a obrigação de assistir a videoaula da Marinha até o fim no balcão (sem o módulo, o atendimento EMA só continua ao término do vídeo)",
        List.of());

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
