/**
 * Módulo: Manutenção (Maintenance Orders)
 *
 * <p>Gerenciamento de Ordens de Serviço (OS) de manutenção preventiva e corretiva
 * para a frota de jetskis.
 *
 * <h2>Funcionalidades</h2>
 * <ul>
 *   <li><b>RF09</b>: CRUD de ordens de serviço (preventiva, corretiva, revisão)</li>
 *   <li><b>RN06</b>: Bloqueio automático de jetski durante manutenção (status=indisponível)</li>
 *   <li><b>RN06.1</b>: Jetski em manutenção não pode ser reservado</li>
 *   <li>Rastreamento de peças, custos, e mão de obra</li>
 *   <li>Controle de horimetro (abertura/conclusão)</li>
 *   <li>Workflow: aberta → em_andamento → aguardando_peças → concluída/cancelada</li>
 * </ul>
 *
 * <h2>Arquitetura</h2>
 * <ul>
 *   <li><b>api</b>: Controllers e DTOs REST</li>
 *   <li><b>domain</b>: Entidades JPA e enums</li>
 *   <li><b>internal</b>: Services e Repositories (package-private)</li>
 * </ul>
 *
 * <h2>Entidades</h2>
 * <ul>
 *   <li>{@link com.jetski.manutencao.domain.OSManutencao}: Ordem de serviço</li>
 *   <li>{@link com.jetski.manutencao.domain.OSManutencaoStatus}: Status da OS</li>
 *   <li>{@link com.jetski.manutencao.domain.OSManutencaoTipo}: Tipo (preventiva/corretiva/revisão)</li>
 *   <li>{@link com.jetski.manutencao.domain.OSManutencaoPrioridade}: Prioridade (baixa/média/alta/urgente)</li>
 * </ul>
 *
 * <h2>Multi-tenancy</h2>
 * Todas as operações respeitam isolamento por {@code tenant_id} via RLS (Row Level Security).
 *
 * @author Jetski Team
 * @version 1.0.0
 * @since Sprint 4
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Manutenção (Maintenance Orders)",
    allowedDependencies = {
        "shared::security",
        "shared::exception",
        "usuarios",
        "locacoes::api",
        "locacoes::domain",
        "locacoes::internal"
    }
)
package com.jetski.manutencao;
