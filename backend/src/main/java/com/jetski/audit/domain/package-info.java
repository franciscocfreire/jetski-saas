/**
 * Audit domain model for cross-cutting audit logging.
 *
 * <p>This package contains:
 * <ul>
 *   <li>{@link com.jetski.shared.audit.domain.Auditoria} - The audit log entity</li>
 *   <li>{@link com.jetski.shared.audit.domain.AuditoriaRepository} - JPA repository for audit entries</li>
 * </ul>
 *
 * <p><strong>Design Decisions:</strong>
 * <ul>
 *   <li>JSONB columns for flexible before/after snapshots</li>
 *   <li>RLS-enabled for automatic multi-tenant isolation</li>
 *   <li>Created by event listeners for loose coupling</li>
 * </ul>
 *
 * @author Jetski Team
 * @since 0.10.0
 */
package com.jetski.shared.audit.domain;
