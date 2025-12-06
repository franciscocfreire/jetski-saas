package com.jetski.usuarios.internal;

import com.jetski.shared.exception.BusinessException;
import com.jetski.usuarios.api.dto.ConviteSummaryDTO;
import com.jetski.usuarios.domain.Convite;
import com.jetski.usuarios.internal.repository.ConviteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service: Invitation Management
 *
 * Handles pending invitation management:
 * - List pending/expired invitations
 * - Resend invitation emails
 * - Cancel invitations
 *
 * @author Jetski Team
 * @since 0.4.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ConviteManagementService {

    private final ConviteRepository conviteRepository;

    // Token generation constants
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int TOKEN_LENGTH = 40;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * List all pending and expired invitations for a tenant.
     *
     * @param tenantId Tenant UUID
     * @return List of invitation summaries
     */
    @Transactional(readOnly = true)
    public List<ConviteSummaryDTO> listInvitations(UUID tenantId) {
        log.info("Listing invitations for tenant: {}", tenantId);

        // Get pending invitations
        List<Convite> pendingInvitations = conviteRepository.findByTenantIdAndStatus(
            tenantId, Convite.ConviteStatus.PENDING
        );

        return pendingInvitations.stream()
            .map(this::toSummaryDTO)
            .collect(Collectors.toList());
    }

    /**
     * Resend an invitation email.
     *
     * Generates a new token and extends expiration to 48 hours from now.
     *
     * @param tenantId Tenant UUID
     * @param conviteId Invitation UUID
     * @return Updated invitation summary
     */
    @Transactional
    public ConviteSummaryDTO resendInvitation(UUID tenantId, UUID conviteId) {
        log.info("Resending invitation: convite={}, tenant={}", conviteId, tenantId);

        Convite convite = conviteRepository.findById(conviteId)
            .orElseThrow(() -> new BusinessException("Convite não encontrado"));

        // Validate tenant ownership
        if (!convite.getTenantId().equals(tenantId)) {
            throw new BusinessException("Convite não pertence a este tenant");
        }

        // Only pending or expired invitations can be resent
        if (convite.getStatus() != Convite.ConviteStatus.PENDING) {
            throw new BusinessException(
                "Apenas convites pendentes podem ser reenviados. Status atual: " + convite.getStatus()
            );
        }

        // Generate new token and extend expiration
        String newToken = generateToken();
        convite.setToken(newToken);
        convite.setExpiresAt(Instant.now().plus(48, ChronoUnit.HOURS));

        // Generate new temporary password
        String temporaryPassword = generateTemporaryPassword();
        convite.setTemporaryPassword(temporaryPassword);

        // Record email resend
        convite.recordEmailSent(null); // Will be updated with actual link

        conviteRepository.save(convite);

        log.info("Invitation resent successfully: convite={}, newExpiration={}", conviteId, convite.getExpiresAt());

        // TODO: Send actual email with new link
        // emailService.sendInvitationEmail(convite, temporaryPassword);

        return toSummaryDTO(convite);
    }

    /**
     * Cancel a pending invitation.
     *
     * @param tenantId Tenant UUID
     * @param conviteId Invitation UUID
     */
    @Transactional
    public void cancelInvitation(UUID tenantId, UUID conviteId) {
        log.info("Cancelling invitation: convite={}, tenant={}", conviteId, tenantId);

        Convite convite = conviteRepository.findById(conviteId)
            .orElseThrow(() -> new BusinessException("Convite não encontrado"));

        // Validate tenant ownership
        if (!convite.getTenantId().equals(tenantId)) {
            throw new BusinessException("Convite não pertence a este tenant");
        }

        // Only pending invitations can be cancelled
        if (convite.getStatus() != Convite.ConviteStatus.PENDING) {
            throw new BusinessException(
                "Apenas convites pendentes podem ser cancelados. Status atual: " + convite.getStatus()
            );
        }

        convite.cancel();
        conviteRepository.save(convite);

        log.info("Invitation cancelled successfully: convite={}", conviteId);
    }

    /**
     * Convert Convite entity to summary DTO.
     */
    private ConviteSummaryDTO toSummaryDTO(Convite convite) {
        // Determine effective status (check if expired)
        String status = convite.isExpired() ? "EXPIRED" : convite.getStatus().name();

        return ConviteSummaryDTO.builder()
            .id(convite.getId())
            .email(convite.getEmail())
            .nome(convite.getNome())
            .papeis(Arrays.asList(convite.getPapeis()))
            .createdAt(convite.getCreatedAt())
            .expiresAt(convite.getExpiresAt())
            .status(status)
            .emailSentCount(convite.getEmailSentCount())
            .lastEmailSentAt(convite.getEmailSentAt())
            .build();
    }

    /**
     * Generate secure random token for invitation.
     */
    private String generateToken() {
        StringBuilder token = new StringBuilder(TOKEN_LENGTH);
        for (int i = 0; i < TOKEN_LENGTH; i++) {
            token.append(ALPHANUMERIC.charAt(SECURE_RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return token.toString();
    }

    /**
     * Generate temporary password (12 characters).
     */
    private String generateTemporaryPassword() {
        StringBuilder password = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            password.append(ALPHANUMERIC.charAt(SECURE_RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return password.toString();
    }
}
