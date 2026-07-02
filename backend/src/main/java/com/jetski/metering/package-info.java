/**
 * Módulo de Metering (uso por tenant)
 *
 * <p>Contabiliza fatos de uso cobráveis (ou monitoráveis) por tenant, consumindo
 * eventos de domínio — hoje, emissões de documentos (DOCUMENTO), gerações de GRU
 * na Marinha (GRU, custo real) e prévias de documento (PREVIA, sinal antifraude).
 * Base para cobrança por plano no futuro ({@code plano.limites.emissoes_mes}).
 *
 * <p><strong>Posicionamento arquitetural:</strong> módulo <em>observador</em>, como
 * {@code audit} — depende dos eventos dos módulos de negócio, mas nenhum módulo
 * depende dele. A contagem é assíncrona e best-effort: nunca quebra o fluxo de
 * negócio; a fonte durável {@code documento_emitido} permite reconciliação.
 *
 * <p><strong>API Pública:</strong>
 * <ul>
 *   <li>{@code api} - Consulta de uso do tenant e agregado da plataforma</li>
 *   <li>{@code domain} - Entidade {@code EmissaoUso} e repositório</li>
 * </ul>
 *
 * @since 0.20.0
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Metering"
    // Sem allowedDependencies: módulo observador (mesma abordagem do audit).
)
package com.jetski.metering;
