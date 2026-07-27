/**
 * Módulo da Plataforma (console do operador).
 *
 * <p>Aqui mora o que é da <strong>plataforma em si</strong> e não tem dono em nenhum
 * módulo de negócio: a visão consolidada de todas as empresas, a trilha global, a saúde
 * do sistema e a sessão de suporte que substituiu o god mode.
 *
 * <p><strong>Onde está a fronteira.</strong> Nem todo endpoint {@code /v1/platform/**}
 * pertence a este módulo. Créditos, metering, faturas, capitanias e operadores são a
 * <em>visão de plataforma</em> de dados que pertencem a outro módulo — mover essas classes
 * para cá exigiria expor repositórios internos de {@code creditos}, {@code metering},
 * {@code tenant} e {@code usuarios}, ou seja, trocar um acoplamento organizado por um
 * vazamento de encapsulamento. Elas ficam onde o dado mora. A regra que separa as duas
 * famílias está no nome: {@code Plataforma*} vive aqui; {@code Platform*} é a fachada de
 * plataforma de um módulo de negócio.
 *
 * <p><strong>Dependências:</strong> só {@code shared}. As leituras consolidadas usam SQL
 * direto sobre o read model ({@code plataforma_metrica_diaria}) e sobre a trilha global,
 * sem tocar em entidade de outro módulo.
 *
 * <p><strong>API Pública:</strong>
 * <ul>
 *   <li>{@code api} - controllers REST de {@code /v1/platform/**} e {@code /v1/suporte/**}</li>
 *   <li>{@code event} - eventos consumidos pela auditoria</li>
 * </ul>
 *
 * @since 0.9.0
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Plataforma (Console)"
)
package com.jetski.plataforma;
