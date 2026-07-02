/**
 * Assinatura — Named Interface do módulo shared.
 *
 * <p>API pública para reforço jurídico da assinatura eletrônica:
 * <ul>
 *   <li>{@link com.jetski.shared.assinatura.CarimboTempoService} — carimbo de tempo
 *       (RFC 3161 / âncora interna) sobre o hash de um documento.</li>
 * </ul>
 *
 * <p>Exposto como named interface para que outros módulos (ex.: {@code locacoes})
 * possam carimbar documentos sem quebrar as fronteiras de módulos.
 *
 * @since 0.8.0
 */
@org.springframework.modulith.NamedInterface("assinatura")
package com.jetski.shared.assinatura;
