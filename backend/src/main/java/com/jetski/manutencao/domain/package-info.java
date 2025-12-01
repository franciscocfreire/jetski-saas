/**
 * Domain entities for Manutencao module.
 *
 * <p>This package is part of the public API and can be accessed by other modules.
 * Contains core domain entities and enums for maintenance operations.
 *
 * <h2>Exposed Types</h2>
 * <ul>
 *   <li>{@link com.jetski.manutencao.domain.OSManutencao} - Maintenance order entity</li>
 *   <li>{@link com.jetski.manutencao.domain.OSManutencaoStatus} - OS status enum</li>
 *   <li>{@link com.jetski.manutencao.domain.OSManutencaoTipo} - OS type enum (preventiva/corretiva/revisao)</li>
 *   <li>{@link com.jetski.manutencao.domain.OSManutencaoPrioridade} - Priority enum (baixa/media/alta/urgente)</li>
 * </ul>
 *
 * <h2>Usage Guidelines</h2>
 * <ul>
 *   <li>Other modules MAY read these entities (e.g., for queries, references)</li>
 *   <li>Other modules SHOULD NOT modify these entities directly</li>
 *   <li>To create/update maintenance orders, use {@link com.jetski.manutencao.api.ManutencaoPublicService}</li>
 * </ul>
 *
 * @since 0.9.0
 * @author Jetski Team
 */
@org.springframework.modulith.NamedInterface("domain")
package com.jetski.manutencao.domain;
