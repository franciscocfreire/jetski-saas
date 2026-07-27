package com.jetski.shared.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Papéis do operador da PLATAFORMA (F2) — o "poder", separado do "alcance".
 *
 * <p>Até a F1, {@code usuario_global_roles.unrestricted_access} significava as duas coisas
 * ao mesmo tempo: acessar qualquer empresa E poder tudo. Com mais de um tipo de operador
 * isso não se sustenta:
 *
 * <ul>
 *   <li><strong>Alcance</strong> continua em {@code unrestricted_access} — é o que faz o
 *       {@code TenantFilter} e a RLS deixarem o operador enxergar empresas sem ser membro.
 *       Vale para QUALQUER papel desta lista.</li>
 *   <li><strong>Poder</strong> passa a ser o papel aqui, decidido em
 *       {@code policies/authz/platform.rego}.</li>
 * </ul>
 *
 * <p>Esta enum é a fonte única dos nomes: a API de operadores valida contra ela, e a
 * migration V054 deliberadamente NÃO tem CHECK de nomes no banco (CHECK do PostgreSQL não
 * aceita subquery, e uma função IMMUTABLE viraria DDL obrigatório a cada papel novo).
 *
 * <p>A matriz de permissões vive no {@code platform.rego} — mudou aqui, mude lá, e vice-versa.
 *
 * @since 0.9.0
 */
public enum PapelPlataforma {

    /** Tudo, inclusive destrutivo, segredos e gestão de operadores. */
    PLATFORM_ADMIN("Administrador", "Acesso total à plataforma, incluindo exclusão de "
        + "empresas, rotação de chave e gestão de operadores."),

    /** Ciclo de vida da empresa e catálogo EAMA. Sem destrutivo, sem financeiro. */
    PLATFORM_SUPORTE("Suporte", "Aprova, suspende e reativa empresas; habilita EAMA e "
        + "mantém o catálogo de capitanias. Não apaga dados nem mexe em dinheiro."),

    /** Créditos, faturas, plano e oferta de módulos. Sem destrutivo, sem operadores. */
    PLATFORM_FINANCEIRO("Financeiro", "Créditos, faturas, troca de plano e oferta de "
        + "módulos. Não apaga dados nem gerencia operadores."),

    /** Somente leitura. Sem export completo e sem comprovante financeiro. */
    PLATFORM_LEITURA("Leitura", "Consulta os painéis da plataforma. Não executa nenhuma "
        + "ação de escrita.");

    /** Prefixo que distingue papel de plataforma de papel de empresa em {@code roles[]}. */
    public static final String PREFIXO = "PLATFORM_";

    private final String rotulo;
    private final String descricao;

    PapelPlataforma(String rotulo, String descricao) {
        this.rotulo = rotulo;
        this.descricao = descricao;
    }

    public String getRotulo() {
        return rotulo;
    }

    public String getDescricao() {
        return descricao;
    }

    /** {@code true} se o nome é de um papel de plataforma (mesmo desconhecido). */
    public static boolean ehPapelDePlataforma(String papel) {
        return papel != null && papel.startsWith(PREFIXO);
    }

    public static Optional<PapelPlataforma> de(String papel) {
        return Arrays.stream(values()).filter(p -> p.name().equals(papel)).findFirst();
    }

    /** Só os papéis de plataforma RECONHECIDOS presentes na lista. */
    public static List<PapelPlataforma> filtrar(Collection<String> papeis) {
        if (papeis == null) {
            return List.of();
        }
        return papeis.stream().map(PapelPlataforma::de).flatMap(Optional::stream).toList();
    }

    /**
     * Se a lista concede acesso à plataforma. Usado pelo
     * {@code PlatformScopeInterceptor} como barreira em profundidade — o OPA decide
     * a ação; aqui só decide se o operador tem qualquer entrada.
     */
    public static boolean temAcessoDePlataforma(Collection<String> papeis) {
        return !filtrar(papeis).isEmpty();
    }
}
