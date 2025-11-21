/**
 * Módulo de Locações e Operações (Rentals & Operations Module)
 *
 * <p>Este módulo gerencia todo o ciclo de vida das operações de aluguel de jetskis,
 * incluindo cadastros básicos, reservas, check-in/check-out, e gestão da frota.
 * Responsabilidades principais:
 *
 * <ul>
 *   <li>Gestão de modelos de jetski e precificação (Modelo)</li>
 *   <li>Gestão da frota de jetskis individuais (Jetski)</li>
 *   <li>Gestão de vendedores/parceiros e comissões (Vendedor)</li>
 *   <li>Gestão de clientes (Cliente)</li>
 *   <li>Gestão de reservas e agendamento (Reserva)</li>
 *   <li>Gestão de locações ativas e histórico (Locacao)</li>
 *   <li>Check-in e check-out com fotos obrigatórias</li>
 *   <li>Cálculo automático de tempo, valor e comissões</li>
 * </ul>
 *
 * <p><strong>API Pública:</strong>
 * <ul>
 *   <li>{@code api} - Controllers REST e DTOs para operações de aluguel</li>
 *   <li>{@code domain} - Entidades de domínio (Modelo, Jetski, Vendedor, Cliente, Reserva, Locacao)</li>
 * </ul>
 *
 * <p><strong>Implementação Interna:</strong>
 * <ul>
 *   <li>{@code internal} - Serviços, repositórios, validações e lógica de negócio (não deve ser acessado diretamente)</li>
 * </ul>
 *
 * <p><strong>Dependências:</strong>
 * <ul>
 *   <li>{@code shared::security} - Contexto de tenant e autenticação</li>
 *   <li>{@code shared::exception} - Exceções de negócio</li>
 *   <li>{@code shared::storage} - Serviço de armazenamento de arquivos (S3/MinIO/Local)</li>
 * </ul>
 *
 * <p><strong>Regras de Negócio Principais:</strong>
 * <ul>
 *   <li>RN01: Tolerância e arredondamento de tempo (base 15 minutos)</li>
 *   <li>RN02: Fotos obrigatórias no check-in e check-out</li>
 *   <li>RN04: Cálculo hierárquico de comissões</li>
 *   <li>RN06: Controle de disponibilidade por status do jetski</li>
 * </ul>
 *
 * @since 0.2.0
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Rentals and Operations",
    allowedDependencies = {"shared::security", "shared::exception", "shared::storage", "combustivel::internal"}
)
package com.jetski.locacoes;
