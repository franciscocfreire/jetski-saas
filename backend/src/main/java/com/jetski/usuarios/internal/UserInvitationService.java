package com.jetski.usuarios.internal;

import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.ConflictException;
import com.jetski.shared.exception.GoneException;
import com.jetski.shared.exception.ForbiddenException;
import com.jetski.shared.exception.InternalServerException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.email.EmailService;
import com.jetski.shared.security.MagicLinkTokenService;
import com.jetski.shared.security.UserProvisioningService;
import com.jetski.usuarios.api.dto.*;
import com.jetski.usuarios.domain.Convite;
import com.jetski.usuarios.domain.Membro;
import com.jetski.usuarios.domain.Usuario;
import com.jetski.usuarios.internal.repository.ConviteRepository;
import com.jetski.usuarios.internal.repository.MembroRepository;
import org.springframework.context.ApplicationEventPublisher;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Service: User Invitation
 *
 * Handles user invitation flow:
 * 1. Validate plan limits
 * 2. Create invitation with token
 * 3. Send invitation email
 * 4. Activate account (PostgreSQL + Keycloak)
 *
 * @author Jetski Team
 * @since 0.3.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserInvitationService {

    private final ConviteRepository conviteRepository;
    private final MembroRepository membroRepository;
    private final UserProvisioningService userProvisioningService;
    private final IdentityProviderMappingService identityMappingService;
    private final ApplicationEventPublisher eventPublisher;
    private final EmailService emailService;
    private final MagicLinkTokenService magicLinkTokenService;

    @PersistenceContext
    private final EntityManager entityManager;

    @Value("${jetski.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    private static final int TOKEN_VALIDITY_HOURS = 48;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String TOKEN_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    // Temporary password generation (Option 2: temp password + Keycloak UPDATE_PASSWORD)
    private static final int TEMP_PASSWORD_LENGTH = 12;
    private static final String TEMP_PASSWORD_CHARS =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%&*";

    /**
     * Invite new user to tenant.
     *
     * Validates:
     * - Plan user limit
     * - Email not already invited
     * - Email not already member
     */
    @Transactional
    public InviteUserResponse inviteUser(UUID tenantId, InviteUserRequest request, UUID invitedBy) {
        log.info("Inviting user {} to tenant {}", request.getEmail(), tenantId);

        // 1. Validate plan limits
        validatePlanLimits(tenantId);

        // 2. Validate email not already invited (PENDING)
        if (conviteRepository.existsByTenantIdAndEmailAndStatus(
            tenantId, request.getEmail(), Convite.ConviteStatus.PENDING)) {
            throw new ConflictException(
                "Este email já possui um convite pendente para este tenant"
            );
        }

        // 3. Validate email not already member
        if (membroRepository.existsByTenantIdAndEmail(tenantId, request.getEmail())) {
            throw new ConflictException(
                "Este email já é membro deste tenant"
            );
        }

        // 4. Generate activation token (40 chars alphanumeric)
        String token = generateSecureToken(40);
        Instant expiresAt = Instant.now().plus(TOKEN_VALIDITY_HOURS, ChronoUnit.HOURS);

        // 5. Generate temporary password (Option 2 flow)
        String temporaryPassword = generateTemporaryPassword();

        // 6. Create invitation
        Convite convite = Convite.builder()
            .tenantId(tenantId)
            .email(request.getEmail())
            .nome(request.getNome())
            .papeis(request.getPapeis())
            .token(token)
            .expiresAt(expiresAt)
            .createdBy(invitedBy)
            .status(Convite.ConviteStatus.PENDING)
            .build();

        // 7. Set temporary password (will be hashed with BCrypt inside Convite)
        convite.setTemporaryPassword(temporaryPassword);

        conviteRepository.save(convite);

        log.info("Invitation created: {} for tenant {} expires at {} (with temporary password)",
            convite.getId(), tenantId, expiresAt);

        // 8. Generate Magic Link JWT (contains token + temp password encrypted)
        String magicToken = magicLinkTokenService.generateMagicToken(token, temporaryPassword);
        String magicLink = String.format("%s/magic-activate?token=%s", frontendUrl, magicToken);

        log.info("Magic link generated for invitation: {}", convite.getId());

        // 9. Send invitation email with magic link (backward compatible: also sends temp password)
        String activationLink = String.format("%s/activate?token=%s", frontendUrl, token);
        emailService.sendInvitationEmail(
            request.getEmail(),
            request.getNome(),
            magicLink,  // Magic link is primary activation method (UX improvement)
            temporaryPassword  // Plain password sent in email (backward compatibility + manual activation fallback)
        );

        // 10. Record email sent and save (updates email_sent_count, email_sent_at)
        convite.recordEmailSent(magicLink);
        conviteRepository.save(convite);

        log.info("Invitation email sent to {} with magic link and temporary password", request.getEmail());

        return InviteUserResponse.success(
            convite.getId(),
            convite.getEmail(),
            convite.getNome(),
            convite.getPapeis(),
            convite.getExpiresAt()
        );
    }

    /**
     * Complete account activation with temporary password (Option 2 flow).
     *
     * Steps:
     * 1. Validate token
     * 2. Validate temporary password matches stored hash
     * 3. Create Usuario in PostgreSQL
     * 4. Create Membro (tenant membership)
     * 5. Provision user in Keycloak with temporary password + UPDATE_PASSWORD required action
     * 6. Create identity provider mapping
     * 7. Mark invitation as ACTIVATED
     * 8. User must change password on first login (Keycloak enforces)
     */
    @Transactional
    public CompleteActivationResponse completeActivation(CompleteActivationRequest request) {
        log.info("Completing account activation with temporary password (Option 2)");

        // 1. Find invitation by token
        Convite convite = conviteRepository.findByToken(request.getToken())
            .orElseThrow(() -> new NotFoundException("Token não encontrado"));

        // 2. Validate invitation status
        if (convite.getStatus() == Convite.ConviteStatus.ACTIVATED) {
            throw new ConflictException("Convite já foi ativado anteriormente");
        }

        if (convite.getStatus() == Convite.ConviteStatus.CANCELLED) {
            throw new GoneException("Convite foi cancelado");
        }

        // 3. Check if expired
        if (convite.isExpired() || convite.getStatus() == Convite.ConviteStatus.EXPIRED) {
            if (convite.getStatus() != Convite.ConviteStatus.EXPIRED) {
                convite.expire();
                conviteRepository.save(convite);
            }
            throw new GoneException("Convite expirado");
        }

        // 4. VALIDATE TEMPORARY PASSWORD (Option 2 security check)
        if (!convite.validateTemporaryPassword(request.getTemporaryPassword())) {
            log.warn("Invalid temporary password attempt for email: {}", convite.getEmail());
            throw new ForbiddenException("Senha temporária inválida");
        }

        log.info("Temporary password validated successfully for: {}", convite.getEmail());

        // 5. Check if usuario already exists
        Usuario usuario = entityManager.createQuery(
            "SELECT u FROM Usuario u WHERE u.email = :email", Usuario.class)
            .setParameter("email", convite.getEmail())
            .getResultStream()
            .findFirst()
            .orElse(null);

        if (usuario != null) {
            throw new ConflictException("Usuário com este email já existe");
        }

        // 6. Create new usuario (email_verified=true since invitation was validated)
        UUID usuarioId = UUID.randomUUID();
        entityManager.createNativeQuery(
            "INSERT INTO usuario (id, email, nome, ativo, email_verified, created_at, updated_at) " +
            "VALUES (?1, ?2, ?3, true, true, NOW() AT TIME ZONE 'UTC', NOW() AT TIME ZONE 'UTC')"
        )
        .setParameter(1, usuarioId)
        .setParameter(2, convite.getEmail())
        .setParameter(3, convite.getNome())
        .executeUpdate();

        log.info("Created new usuario: {}", usuarioId);

        // 7. Create membro
        entityManager.createNativeQuery(
            "INSERT INTO membro (tenant_id, usuario_id, papeis, ativo, created_at, updated_at) " +
            "VALUES (?1, ?2, ?3, true, NOW() AT TIME ZONE 'UTC', NOW() AT TIME ZONE 'UTC')"
        )
        .setParameter(1, convite.getTenantId())
        .setParameter(2, usuarioId)
        .setParameter(3, convite.getPapeis())
        .executeUpdate();

        log.info("Created membro for usuario {} in tenant {}", usuarioId, convite.getTenantId());

        // 7.1 Create tenant_access (required for TenantFilter validation)
        entityManager.createNativeQuery(
            "INSERT INTO tenant_access (usuario_id, tenant_id, roles, is_default, created_at, updated_at) " +
            "VALUES (?1, ?2, ?3, true, NOW() AT TIME ZONE 'UTC', NOW() AT TIME ZONE 'UTC')"
        )
        .setParameter(1, usuarioId)
        .setParameter(2, convite.getTenantId())
        .setParameter(3, convite.getPapeis())
        .executeUpdate();

        log.info("Created tenant_access for usuario {} in tenant {}", usuarioId, convite.getTenantId());

        // 8. Provision user in Keycloak WITH TEMPORARY PASSWORD + UPDATE_PASSWORD required action
        // Keycloak will force password change on first login
        String providerUserId = userProvisioningService.provisionUserWithPassword(
            usuarioId,
            convite.getEmail(),
            convite.getNome(),
            convite.getTenantId(),
            java.util.Arrays.asList(convite.getPapeis()),
            request.getTemporaryPassword()  // Use temporary password from request (already validated)
        );

        if (providerUserId == null) {
            log.error("Failed to provision user in Keycloak: {}", usuarioId);
            throw new InternalServerException("Falha ao provisionar usuário no Keycloak");
        }

        log.info("User provisioned with password in Keycloak: providerUserId={}, postgresId={}",
            providerUserId, usuarioId);

        // 8. Create identity provider mapping
        try {
            identityMappingService.linkProvider(usuarioId, "keycloak", providerUserId);
            log.info("Identity provider mapping created: postgresId={}, provider=keycloak, providerUserId={}",
                usuarioId, providerUserId);
        } catch (Exception e) {
            log.error("Failed to create identity provider mapping: postgresId={}, providerUserId={}, error={}",
                usuarioId, providerUserId, e.getMessage(), e);
            throw new InternalServerException("Falha ao criar mapeamento de identidade");
        }

        // 9. Mark invitation as activated (NO email event published)
        convite.activate(usuarioId);
        conviteRepository.save(convite);

        log.info("Account activation completed successfully for {}", convite.getEmail());

        return CompleteActivationResponse.success(
            usuarioId,
            convite.getEmail(),
            convite.getNome(),
            convite.getTenantId(),
            convite.getPapeis()
        );
    }

    /**
     * Validate tenant plan limits.
     *
     * Checks if tenant has reached maximum users allowed by plan.
     */
    private void validatePlanLimits(UUID tenantId) {
        // Query to get plan limit
        Integer maxUsuarios = (Integer) entityManager.createNativeQuery(
            "SELECT (p.limites->>'usuarios_max')::int " +
            "FROM assinatura a " +
            "JOIN plano p ON a.plano_id = p.id " +
            "WHERE a.tenant_id = ?1 AND a.status = 'ativa'"
        )
        .setParameter(1, tenantId)
        .getSingleResult();

        if (maxUsuarios == null) {
            throw new BusinessException("Tenant não possui assinatura ativa");
        }

        // Count current active members
        Long currentUsers = membroRepository.countByTenantIdAndAtivo(tenantId, true);

        if (currentUsers >= maxUsuarios) {
            throw new ForbiddenException(
                String.format(
                    "Limite de usuários atingido. Plano permite %d usuários, você já tem %d. Faça upgrade do seu plano para convidar mais usuários.",
                    maxUsuarios, currentUsers
                )
            );
        }

        log.debug("Plan validation passed: {}/{} users", currentUsers, maxUsuarios);
    }

    /**
     * Generate secure random token.
     *
     * @param length Token length in characters
     * @return Secure random alphanumeric token
     */
    private String generateSecureToken(int length) {
        StringBuilder token = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            token.append(TOKEN_CHARS.charAt(SECURE_RANDOM.nextInt(TOKEN_CHARS.length())));
        }
        return token.toString();
    }

    /**
     * Generate secure temporary password for Option 2 flow.
     *
     * Generates a cryptographically secure random password with:
     * - Mix of uppercase, lowercase, digits, and special chars
     * - Length: 12 characters (configurable via TEMP_PASSWORD_LENGTH)
     * - Special chars: !@#$%&*
     *
     * Used in Option 2: Backend generates temp password, hashes it with BCrypt,
     * sends plain password in email. User activates account with token + temp password,
     * then Keycloak forces password change on first login via UPDATE_PASSWORD.
     *
     * @return Secure random temporary password
     */
    private String generateTemporaryPassword() {
        StringBuilder password = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            password.append(TEMP_PASSWORD_CHARS.charAt(SECURE_RANDOM.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return password.toString();
    }
}
