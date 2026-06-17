package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.domain.ClienteClaimToken;
import com.jetski.locacoes.domain.ClienteIdentityProvider;
import com.jetski.locacoes.event.ClaimEnviadoEvent;
import com.jetski.locacoes.event.ContaAtivadaEvent;
import com.jetski.locacoes.internal.repository.ClienteClaimTokenRepository;
import com.jetski.locacoes.internal.repository.ClienteIdentityProviderRepository;
import com.jetski.locacoes.internal.repository.ClienteRepository;
import com.jetski.shared.email.EmailService;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.security.TenantContext;
import com.jetski.shared.security.UserProvisioningService;
import jakarta.persistence.EntityManager;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * F2.7 — Claim-token de ativação da conta do cliente (balcão).
 *
 * <p><b>gerar/reenviar</b> (staff): emite token + senha temporária, desativa
 * tokens anteriores, marca o cliente como CONVIDADA e envia o link por e-mail.
 * <p><b>validar</b> (público): confere token+senha, provisiona o usuário no
 * Keycloak (role CLIENTE, <strong>sem Membro</strong>), vincula via
 * {@code cliente_identity_provider} e marca o cliente como ATIVA.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ClaimService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String ALPHANUM =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int TOKEN_LEN = 40;
    private static final int TTL_DIAS = 7;
    private static final String PROVIDER = "keycloak";
    private static final String ROLE_CLIENTE = "CLIENTE";

    private final ClienteRepository clienteRepository;
    private final ClienteClaimTokenRepository tokenRepository;
    private final ClienteIdentityProviderRepository identityRepository;
    private final UserProvisioningService userProvisioningService;
    private final EmailService emailService;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    @org.springframework.beans.factory.annotation.Value("${jetski.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Value
    @Builder
    public static class ClaimResult {
        UUID clienteId;
        String token;
        String link;
        Instant expiraEm;
        String canais;
        boolean enviado;
    }

    @Value
    @Builder
    public static class AtivacaoResult {
        UUID clienteId;
        String providerUserId;
    }

    /**
     * Gera (ou reenvia) o claim-token do cliente e dispara o e-mail de ativação.
     * Reenvio: desativa tokens ativos anteriores e emite um novo.
     */
    @Transactional
    public ClaimResult gerar(UUID clienteId, String canais) {
        Cliente cliente = clienteRepository.findById(clienteId)
            .orElseThrow(() -> new NotFoundException("Cliente não encontrado: " + clienteId));

        if (cliente.getStatusConta() == Cliente.StatusConta.ATIVA
                || identityRepository.existsByClienteId(clienteId)) {
            throw new BusinessException("Conta do cliente já está ativa");
        }
        if (cliente.getEmail() == null || cliente.getEmail().isBlank()) {
            throw new BusinessException("Cliente sem e-mail para envio do link de ativação");
        }

        // Reenvio: invalida tokens ativos anteriores (append-only).
        List<ClienteClaimToken> ativos = tokenRepository.findByClienteIdAndAtivoTrue(clienteId);
        for (ClienteClaimToken antigo : ativos) {
            antigo.setAtivo(false);
        }
        if (!ativos.isEmpty()) {
            tokenRepository.saveAll(ativos);
        }

        String token = randomToken();
        String senhaTemporaria = senhaTemporaria();
        String canaisResolvidos = (canais == null || canais.isBlank()) ? "email" : canais;
        Instant expiraEm = Instant.now().plus(TTL_DIAS, ChronoUnit.DAYS);

        ClienteClaimToken claim = ClienteClaimToken.builder()
            .tenantId(cliente.getTenantId())
            .clienteId(clienteId)
            .token(token)
            .canais(canaisResolvidos)
            .expiraEm(expiraEm)
            .ativo(true)
            .criadoPor(TenantContext.getUsuarioId())
            .build();
        claim.setTemporaryPassword(senhaTemporaria);
        tokenRepository.save(claim);

        cliente.setStatusConta(Cliente.StatusConta.CONVIDADA);
        clienteRepository.save(cliente);

        String link = String.format("%s/cliente/ativar?token=%s", frontendUrl, token);
        emailService.sendInvitationEmail(cliente.getEmail(), cliente.getNome(), link, senhaTemporaria);

        eventPublisher.publishEvent(ClaimEnviadoEvent.of(
            cliente.getTenantId(), clienteId, canaisResolvidos, TenantContext.getUsuarioId()));

        log.info("Claim-token gerado: clienteId={}, canais={}, expiraEm={}", clienteId, canaisResolvidos, expiraEm);
        return ClaimResult.builder()
            .clienteId(clienteId).token(token).link(link)
            .expiraEm(expiraEm).canais(canaisResolvidos).enviado(true).build();
    }

    /**
     * Valida o claim-token (endpoint público) e ativa a conta do cliente:
     * provisiona no Keycloak (role CLIENTE, sem Membro) e vincula a identidade.
     */
    @Transactional
    public AtivacaoResult validar(String token, String senhaTemporaria) {
        ClienteClaimToken claim = tokenRepository.findByToken(token)
            .orElseThrow(() -> new BusinessException("Token inválido"));

        if (Boolean.FALSE.equals(claim.getAtivo()) || claim.isUsado()) {
            throw new BusinessException("Token já utilizado ou cancelado");
        }
        if (claim.isExpired()) {
            throw new BusinessException("Token expirado");
        }
        if (!claim.validateTemporaryPassword(senhaTemporaria)) {
            log.warn("Senha temporária inválida no claim do cliente {}", claim.getClienteId());
            throw new BusinessException("Senha temporária inválida");
        }

        // O endpoint é público (sem tenant no contexto): o token foi lido via o
        // carve-out de RLS (tenant nulo ⇒ permitido). A partir daqui fixamos o
        // tenant DO TOKEN nesta conexão para que as escritas em cliente/identidade
        // satisfaçam a RLS estrita (V009).
        fixarTenant(claim.getTenantId());

        Cliente cliente = clienteRepository.findById(claim.getClienteId())
            .orElseThrow(() -> new NotFoundException("Cliente não encontrado: " + claim.getClienteId()));

        if (identityRepository.existsByClienteId(cliente.getId())) {
            throw new BusinessException("Conta do cliente já está ativa");
        }

        // Provisiona no Keycloak: role CLIENTE, sem Usuario/Membro.
        // O id do cliente é guardado como atributo (postgresql_user_id) no IdP.
        String providerUserId = userProvisioningService.provisionUserWithPassword(
            cliente.getId(),
            cliente.getEmail(),
            cliente.getNome(),
            cliente.getTenantId(),
            List.of(ROLE_CLIENTE),
            senhaTemporaria);
        if (providerUserId == null) {
            throw new BusinessException("Falha ao provisionar usuário no provedor de identidade");
        }

        identityRepository.save(ClienteIdentityProvider.builder()
            .tenantId(cliente.getTenantId())
            .clienteId(cliente.getId())
            .provider(PROVIDER)
            .providerUserId(providerUserId)
            .build());

        cliente.setStatusConta(Cliente.StatusConta.ATIVA);
        clienteRepository.save(cliente);

        claim.setUsadoEm(Instant.now());
        claim.setAtivo(false);
        tokenRepository.save(claim);

        eventPublisher.publishEvent(ContaAtivadaEvent.of(
            cliente.getTenantId(), cliente.getId(), providerUserId));

        log.info("Conta do cliente ativada: clienteId={}, providerUserId={}", cliente.getId(), providerUserId);
        return AtivacaoResult.builder().clienteId(cliente.getId()).providerUserId(providerUserId).build();
    }

    /** Fixa app.tenant_id (transação-local) na conexão atual para satisfazer a RLS estrita. */
    private void fixarTenant(UUID tenantId) {
        entityManager.createNativeQuery("SELECT set_config('app.tenant_id', :tid, true)")
            .setParameter("tid", tenantId.toString())
            .getSingleResult();
        TenantContext.setTenantId(tenantId);
    }

    private static String randomToken() {
        StringBuilder sb = new StringBuilder(TOKEN_LEN);
        for (int i = 0; i < TOKEN_LEN; i++) {
            sb.append(ALPHANUM.charAt(SECURE_RANDOM.nextInt(ALPHANUM.length())));
        }
        return sb.toString();
    }

    private static String senhaTemporaria() {
        // 12 chars alfanuméricos — Keycloak força a troca no 1º login (UPDATE_PASSWORD).
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(ALPHANUM.charAt(SECURE_RANDOM.nextInt(ALPHANUM.length())));
        }
        return sb.toString();
    }
}
