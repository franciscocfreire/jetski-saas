package com.jetski.signup.internal;

import com.jetski.shared.email.EmailService;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.ConflictException;
import com.jetski.shared.exception.ForbiddenException;
import com.jetski.shared.exception.GoneException;
import com.jetski.shared.exception.InternalServerException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.security.MagicLinkTokenService;
import com.jetski.shared.security.UserProvisioningService;
import com.jetski.signup.api.dto.CreateTenantRequest;
import com.jetski.signup.api.dto.TenantSignupRequest;
import com.jetski.signup.api.dto.TenantSignupResponse;
import com.jetski.signup.domain.SignupStatus;
import com.jetski.signup.domain.TenantSignup;
import com.jetski.signup.internal.repository.TenantSignupRepository;
import com.jetski.tenant.domain.Tenant;
import com.jetski.tenant.domain.TenantStatus;
import com.jetski.tenant.internal.repository.TenantRepository;
import com.jetski.usuarios.internal.IdentityProviderMappingService;
import com.jetski.usuarios.internal.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.UUID;

/**
 * Service: Tenant Signup
 *
 * Handles tenant self-service signup flow:
 * 1. New user creates tenant and admin account
 * 2. Existing user creates additional tenant
 *
 * @author Jetski Team
 * @since 0.5.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TenantSignupService {

    private final TenantRepository tenantRepository;
    private final TenantSignupRepository signupRepository;
    private final UsuarioRepository usuarioRepository;
    private final UserProvisioningService userProvisioningService;
    private final IdentityProviderMappingService identityMappingService;
    private final EmailService emailService;
    private final MagicLinkTokenService magicLinkTokenService;

    @PersistenceContext
    private final EntityManager entityManager;

    @Value("${jetski.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    private static final int TOKEN_VALIDITY_HOURS = 48;
    private static final int TRIAL_DAYS = 14;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String TOKEN_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int TEMP_PASSWORD_LENGTH = 12;
    private static final String TEMP_PASSWORD_CHARS =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%&*";

    /**
     * Signup a new tenant with new admin user.
     * Used for public signup (user doesn't have an account yet).
     *
     * Flow:
     * 1. Validate slug and email are unique
     * 2. Create Tenant
     * 3. Create Assinatura (Trial plan)
     * 4. Create TenantSignup record
     * 5. Send activation email
     * 6. User activates via link (creates Usuario, Membro, Keycloak)
     */
    @Transactional
    public TenantSignupResponse signupNewTenant(TenantSignupRequest request) {
        log.info("Processing tenant signup for: {} ({})", request.razaoSocial(), request.adminEmail());

        // 1. Validate slug is unique
        if (tenantRepository.existsBySlug(request.slug())) {
            throw new ConflictException("Este slug já está em uso. Escolha outro identificador.");
        }

        // 2. Validate email doesn't exist
        if (usuarioRepository.existsByEmail(request.adminEmail())) {
            throw new ConflictException(
                "Este email já possui uma conta. Use o login para acessar ou crie a empresa pelo dashboard."
            );
        }

        // 3. Check for pending signup with same email (rate limiting)
        if (signupRepository.existsByEmailAndStatus(request.adminEmail(), SignupStatus.PENDING)) {
            throw new ConflictException(
                "Já existe um cadastro pendente para este email. Verifique seu email ou aguarde a expiração."
            );
        }

        // 4. Create Tenant
        Tenant tenant = Tenant.builder()
            .slug(request.slug())
            .razaoSocial(request.razaoSocial())
            .cnpj(request.cnpj())
            .status(TenantStatus.ATIVO)
            .timezone("America/Sao_Paulo")
            .moeda("BRL")
            .build();
        tenantRepository.save(tenant);
        log.info("Tenant created: {} ({})", tenant.getId(), tenant.getSlug());

        // 5. Create Assinatura with Trial plan
        Instant trialExpiresAt = Instant.now().plus(TRIAL_DAYS, ChronoUnit.DAYS);
        LocalDate trialEndDate = LocalDate.now().plusDays(TRIAL_DAYS);

        entityManager.createNativeQuery(
            """
            INSERT INTO assinatura (tenant_id, plano_id, ciclo, dt_inicio, dt_fim, status)
            SELECT ?1, p.id, 'mensal', CURRENT_DATE, ?2, 'ativa'
            FROM plano p WHERE p.nome = 'Trial'
            """
        )
        .setParameter(1, tenant.getId())
        .setParameter(2, trialEndDate)
        .executeUpdate();
        log.info("Trial subscription created for tenant: {}", tenant.getId());

        // 5.1. Create default fuel policy (INCLUSO - combustível incluído no preço)
        createDefaultFuelPolicy(tenant.getId());
        log.info("Default fuel policy created for tenant: {}", tenant.getId());

        // 6. Generate activation token and temporary password
        String token = generateSecureToken(40);
        String temporaryPassword = generateTemporaryPassword();
        Instant expiresAt = Instant.now().plus(TOKEN_VALIDITY_HOURS, ChronoUnit.HOURS);

        // 7. Create TenantSignup record
        TenantSignup signup = TenantSignup.builder()
            .tenantId(tenant.getId())
            .email(request.adminEmail())
            .nome(request.adminNome())
            .token(token)
            .expiresAt(expiresAt)
            .status(SignupStatus.PENDING)
            .build();
        signup.setTemporaryPassword(temporaryPassword);
        signupRepository.save(signup);
        log.info("TenantSignup created: {} for tenant {}", signup.getId(), tenant.getId());

        // 8. Generate magic link and send activation email
        String magicToken = magicLinkTokenService.generateMagicToken(token, temporaryPassword);
        String magicLink = String.format("%s/magic-activate?token=%s", frontendUrl, magicToken);

        emailService.sendInvitationEmail(
            request.adminEmail(),
            request.adminNome(),
            magicLink,
            temporaryPassword
        );
        log.info("Activation email sent to: {}", request.adminEmail());

        return TenantSignupResponse.forNewUser(
            tenant.getId(),
            tenant.getSlug(),
            tenant.getRazaoSocial(),
            request.adminEmail(),
            trialExpiresAt
        );
    }

    /**
     * Create a new tenant for an existing authenticated user.
     * User already has an account and wants to create another company.
     *
     * Flow:
     * 1. Validate slug is unique
     * 2. Create Tenant
     * 3. Create Assinatura (Trial plan)
     * 4. Create Membro (ADMIN_TENANT)
     * 5. Create TenantAccess
     * 6. Return immediately (no activation needed)
     */
    @Transactional
    public TenantSignupResponse createTenantForExistingUser(CreateTenantRequest request, UUID usuarioId) {
        log.info("Creating tenant for existing user: {} ({})", request.razaoSocial(), usuarioId);

        // 1. Validate slug is unique
        if (tenantRepository.existsBySlug(request.slug())) {
            throw new ConflictException("Este slug já está em uso. Escolha outro identificador.");
        }

        // 2. Verify user exists
        if (!usuarioRepository.existsById(usuarioId)) {
            throw new NotFoundException("Usuário não encontrado");
        }

        // 3. Create Tenant
        Tenant tenant = Tenant.builder()
            .slug(request.slug())
            .razaoSocial(request.razaoSocial())
            .cnpj(request.cnpj())
            .status(TenantStatus.ATIVO)
            .timezone("America/Sao_Paulo")
            .moeda("BRL")
            .build();
        tenantRepository.save(tenant);
        log.info("Tenant created for existing user: {} ({})", tenant.getId(), tenant.getSlug());

        // 4. Create Assinatura with Trial plan
        Instant trialExpiresAt = Instant.now().plus(TRIAL_DAYS, ChronoUnit.DAYS);
        LocalDate trialEndDate = LocalDate.now().plusDays(TRIAL_DAYS);

        entityManager.createNativeQuery(
            """
            INSERT INTO assinatura (tenant_id, plano_id, ciclo, dt_inicio, dt_fim, status)
            SELECT ?1, p.id, 'mensal', CURRENT_DATE, ?2, 'ativa'
            FROM plano p WHERE p.nome = 'Trial'
            """
        )
        .setParameter(1, tenant.getId())
        .setParameter(2, trialEndDate)
        .executeUpdate();
        log.info("Trial subscription created for tenant: {}", tenant.getId());

        // 4.1. Create default fuel policy (INCLUSO - combustível incluído no preço)
        createDefaultFuelPolicy(tenant.getId());
        log.info("Default fuel policy created for tenant: {}", tenant.getId());

        // 5. Create Membro with ADMIN_TENANT role
        entityManager.createNativeQuery(
            """
            INSERT INTO membro (tenant_id, usuario_id, papeis, ativo, created_at, updated_at)
            VALUES (?1, ?2, ?3, true, NOW() AT TIME ZONE 'UTC', NOW() AT TIME ZONE 'UTC')
            """
        )
        .setParameter(1, tenant.getId())
        .setParameter(2, usuarioId)
        .setParameter(3, new String[]{"ADMIN_TENANT"})
        .executeUpdate();
        log.info("Membro created for user {} in tenant {}", usuarioId, tenant.getId());

        // 6. Create TenantAccess
        entityManager.createNativeQuery(
            """
            INSERT INTO tenant_access (usuario_id, tenant_id, roles, is_default, created_at, updated_at)
            VALUES (?1, ?2, ?3, false, NOW() AT TIME ZONE 'UTC', NOW() AT TIME ZONE 'UTC')
            """
        )
        .setParameter(1, usuarioId)
        .setParameter(2, tenant.getId())
        .setParameter(3, new String[]{"ADMIN_TENANT"})
        .executeUpdate();
        log.info("TenantAccess created for user {} in tenant {}", usuarioId, tenant.getId());

        return TenantSignupResponse.forExistingUser(
            tenant.getId(),
            tenant.getSlug(),
            tenant.getRazaoSocial(),
            trialExpiresAt
        );
    }

    /**
     * Complete the signup activation (creates Usuario, Membro, Keycloak user).
     * Called when user clicks the activation link and provides temporary password.
     */
    @Transactional
    public void completeSignupActivation(String token, String temporaryPassword) {
        log.info("Completing signup activation");

        // 1. Find signup by token
        TenantSignup signup = signupRepository.findByToken(token)
            .orElseThrow(() -> new NotFoundException("Token não encontrado"));

        // 2. Validate status
        if (signup.getStatus() == SignupStatus.ACTIVATED) {
            throw new ConflictException("Esta conta já foi ativada");
        }

        if (signup.getStatus() == SignupStatus.EXPIRED) {
            throw new GoneException("Token expirado");
        }

        // 3. Check expiration
        if (signup.isExpired()) {
            signup.expire();
            signupRepository.save(signup);
            throw new GoneException("Token expirado");
        }

        // 4. Validate temporary password
        if (!signup.validateTemporaryPassword(temporaryPassword)) {
            log.warn("Invalid temporary password for signup: {}", signup.getId());
            throw new ForbiddenException("Senha temporária inválida");
        }

        // 5. Create Usuario
        UUID usuarioId = UUID.randomUUID();
        entityManager.createNativeQuery(
            """
            INSERT INTO usuario (id, email, nome, ativo, email_verified, created_at, updated_at)
            VALUES (?1, ?2, ?3, true, true, NOW() AT TIME ZONE 'UTC', NOW() AT TIME ZONE 'UTC')
            """
        )
        .setParameter(1, usuarioId)
        .setParameter(2, signup.getEmail())
        .setParameter(3, signup.getNome())
        .executeUpdate();
        log.info("Usuario created: {}", usuarioId);

        // 6. Create Membro with ADMIN_TENANT role
        entityManager.createNativeQuery(
            """
            INSERT INTO membro (tenant_id, usuario_id, papeis, ativo, created_at, updated_at)
            VALUES (?1, ?2, ?3, true, NOW() AT TIME ZONE 'UTC', NOW() AT TIME ZONE 'UTC')
            """
        )
        .setParameter(1, signup.getTenantId())
        .setParameter(2, usuarioId)
        .setParameter(3, new String[]{"ADMIN_TENANT"})
        .executeUpdate();
        log.info("Membro created for user {} in tenant {}", usuarioId, signup.getTenantId());

        // 7. Create TenantAccess
        entityManager.createNativeQuery(
            """
            INSERT INTO tenant_access (usuario_id, tenant_id, roles, is_default, created_at, updated_at)
            VALUES (?1, ?2, ?3, true, NOW() AT TIME ZONE 'UTC', NOW() AT TIME ZONE 'UTC')
            """
        )
        .setParameter(1, usuarioId)
        .setParameter(2, signup.getTenantId())
        .setParameter(3, new String[]{"ADMIN_TENANT"})
        .executeUpdate();
        log.info("TenantAccess created for user {} in tenant {}", usuarioId, signup.getTenantId());

        // 8. Provision user in Keycloak
        String providerUserId = userProvisioningService.provisionUserWithPassword(
            usuarioId,
            signup.getEmail(),
            signup.getNome(),
            signup.getTenantId(),
            Arrays.asList("ADMIN_TENANT"),
            temporaryPassword
        );

        if (providerUserId == null) {
            log.error("Failed to provision user in Keycloak: {}", usuarioId);
            throw new InternalServerException("Falha ao provisionar usuário no Keycloak");
        }
        log.info("User provisioned in Keycloak: {}", providerUserId);

        // 9. Create identity provider mapping
        try {
            identityMappingService.linkProvider(usuarioId, "keycloak", providerUserId);
            log.info("Identity mapping created: {} -> {}", usuarioId, providerUserId);
        } catch (Exception e) {
            log.error("Failed to create identity mapping", e);
            throw new InternalServerException("Falha ao criar mapeamento de identidade");
        }

        // 10. Mark signup as activated
        signup.activate();
        signupRepository.save(signup);
        log.info("Signup activation completed: {}", signup.getId());
    }

    /**
     * Check if a slug is available.
     */
    public boolean isSlugAvailable(String slug) {
        return !tenantRepository.existsBySlug(slug);
    }

    /**
     * Generate a secure random token.
     */
    private String generateSecureToken(int length) {
        StringBuilder token = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            token.append(TOKEN_CHARS.charAt(SECURE_RANDOM.nextInt(TOKEN_CHARS.length())));
        }
        return token.toString();
    }

    /**
     * Generate a secure temporary password.
     */
    private String generateTemporaryPassword() {
        StringBuilder password = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            password.append(TEMP_PASSWORD_CHARS.charAt(SECURE_RANDOM.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return password.toString();
    }

    /**
     * Create default fuel policy for a new tenant.
     * Default is INCLUSO (fuel included in rental price) - most common for jetski rentals.
     *
     * @param tenantId the tenant ID
     */
    private void createDefaultFuelPolicy(UUID tenantId) {
        entityManager.createNativeQuery(
            """
            INSERT INTO fuel_policy (
                tenant_id, nome, tipo, aplicavel_a, referencia_id,
                valor_taxa_por_hora, comissionavel, ativo, prioridade, descricao,
                created_at, updated_at
            ) VALUES (
                ?1, 'Combustível Incluído', 'INCLUSO', 'GLOBAL', NULL,
                NULL, false, true, 0, 'Política padrão - combustível incluído no valor da locação',
                NOW() AT TIME ZONE 'UTC', NOW() AT TIME ZONE 'UTC'
            )
            """
        )
        .setParameter(1, tenantId)
        .executeUpdate();
    }
}
