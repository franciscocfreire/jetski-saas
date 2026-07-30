package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.domain.ClienteClaimToken;
import com.jetski.locacoes.event.ClaimEnviadoEvent;
import com.jetski.locacoes.event.ContaAtivadaEvent;
import com.jetski.locacoes.internal.repository.ClienteClaimTokenRepository;
import com.jetski.locacoes.internal.repository.ClienteRepository;
import com.jetski.shared.email.EmailService;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.security.UserProvisioningService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2.7 — claim-token do cliente: geração/envio e validação/ativação.
 * Invariante chave (identidade única, F4): o vínculo canônico é
 * cliente.usuario_id (pessoa provisionada) e o cliente NUNCA recebe Membro
 * (o serviço sequer depende de MembroRepository).
 */
@DisplayName("ClaimService (F2.7)")
class ClaimServiceTest {

    private final ClienteRepository clienteRepo = mock(ClienteRepository.class);
    private final ClienteClaimTokenRepository tokenRepo = mock(ClienteClaimTokenRepository.class);
    private final UserProvisioningService provisioning = mock(UserProvisioningService.class);
    private final EmailService email = mock(EmailService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final EntityManager em = mock(EntityManager.class);

    private final com.jetski.usuarios.api.PessoaProvisioningService pessoa =
        mock(com.jetski.usuarios.api.PessoaProvisioningService.class);

    private final ClaimService service = new ClaimService(
        clienteRepo, tokenRepo, provisioning, pessoa, email, events, em);

    private final UUID tenant = UUID.randomUUID();
    private final UUID clienteId = UUID.randomUUID();
    private final UUID pessoaId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "frontendUrl", "http://portal.test");
        when(tokenRepo.findByClienteIdAndAtivoTrue(clienteId)).thenReturn(List.of());
        when(tokenRepo.save(any(ClienteClaimToken.class))).thenAnswer(i -> i.getArgument(0));
        when(provisioning.provisionOrReuseCliente(any(), anyString(), anyString(), any(), anyString()))
            .thenReturn(new UserProvisioningService.ClienteProvisionResult("kc-sub-123", false));
        when(pessoa.provisionarPessoa(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(pessoaId);
        when(clienteRepo.findByTenantIdAndUsuarioId(any(), any())).thenReturn(Optional.empty());

        // fixarTenant(): set_config('app.tenant_id', ...) na conexão
        Query q = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(q);
        when(q.setParameter(anyString(), any())).thenReturn(q);
        when(q.getSingleResult()).thenReturn(1);
    }

    private Cliente preConta() {
        return Cliente.builder().id(clienteId).tenantId(tenant)
            .nome("Maria Souza").email("maria@email.com")
            .statusConta(Cliente.StatusConta.PRE_CONTA).build();
    }

    private ClienteClaimToken claimValido() {
        ClienteClaimToken claim = ClienteClaimToken.builder()
            .tenantId(tenant).clienteId(clienteId).token("tok").ativo(true)
            .expiraEm(Instant.now().plus(7, ChronoUnit.DAYS)).build();
        claim.setTemporaryPassword("Senha#123");
        return claim;
    }

    @Test
    @DisplayName("gerar: emite token, marca CONVIDADA, envia e-mail (link do portal) e publica ClaimEnviadoEvent")
    void gerarEnviaEmail() {
        when(clienteRepo.findById(clienteId)).thenReturn(Optional.of(preConta()));

        ClaimService.ClaimResult r = service.gerar(clienteId, "email,whatsapp");

        assertThat(r.getToken()).hasSize(40);
        // sem jetski.portal.url configurada: fallback legado {frontend}/portal
        assertThat(r.getLink()).startsWith("http://portal.test/portal/ativar?token=");
        assertThat(r.isEnviado()).isTrue();

        ArgumentCaptor<Cliente> cli = ArgumentCaptor.forClass(Cliente.class);
        verify(clienteRepo).save(cli.capture());
        assertThat(cli.getValue().getStatusConta()).isEqualTo(Cliente.StatusConta.CONVIDADA);

        verify(tokenRepo).save(any(ClienteClaimToken.class));
        verify(email).sendClienteInvitationEmail(eq("maria@email.com"), eq("Maria Souza"), anyString(), anyString());
        verify(events).publishEvent(any(ClaimEnviadoEvent.class));
    }

    @Test
    @DisplayName("gerar: com jetski.portal.url o link aponta ao SUBDOMÍNIO do portal (raiz, sem /portal)")
    void gerarLinkSubdominio() {
        ReflectionTestUtils.setField(service, "portalUrl", "https://cliente.pegaojet.com.br");
        when(clienteRepo.findById(clienteId)).thenReturn(Optional.of(preConta()));

        ClaimService.ClaimResult r = service.gerar(clienteId, "email");

        assertThat(r.getLink()).startsWith("https://cliente.pegaojet.com.br/ativar?token=");
    }

    @Test
    @DisplayName("gerar: rejeita se a conta já está ativa")
    void gerarRejeitaContaAtiva() {
        Cliente ativa = preConta();
        ativa.setStatusConta(Cliente.StatusConta.ATIVA);
        when(clienteRepo.findById(clienteId)).thenReturn(Optional.of(ativa));

        assertThatThrownBy(() -> service.gerar(clienteId, null)).isInstanceOf(BusinessException.class);
        verify(email, never()).sendClienteInvitationEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("validar: provisiona Keycloak (CLIENTE), vincula a pessoa (usuario_id), ativa conta — sem Membro")
    void validarAtivaConta() {
        when(tokenRepo.findByToken("tok")).thenReturn(Optional.of(claimValido()));
        when(clienteRepo.findById(clienteId)).thenReturn(Optional.of(preConta()));

        ClaimService.AtivacaoResult r = service.validar("tok", "Senha#123");

        assertThat(r.getProviderUserId()).isEqualTo("kc-sub-123");
        assertThat(r.isContaExistente()).isFalse();

        verify(provisioning).provisionOrReuseCliente(
            eq(clienteId), eq("maria@email.com"), eq("Maria Souza"), eq(tenant), eq("Senha#123"));
        verify(pessoa).provisionarPessoa(
            eq("kc-sub-123"), eq("maria@email.com"), eq("Maria Souza"), eq("CLAIM"));

        ArgumentCaptor<Cliente> cli = ArgumentCaptor.forClass(Cliente.class);
        verify(clienteRepo).save(cli.capture());
        assertThat(cli.getValue().getUsuarioId()).isEqualTo(pessoaId);
        assertThat(cli.getValue().getStatusConta()).isEqualTo(Cliente.StatusConta.ATIVA);

        verify(events).publishEvent(any(ContaAtivadaEvent.class));
    }

    @Test
    @DisplayName("validar: reutiliza usuário Keycloak CLIENTE existente (auto-cadastro no portal) — senha intacta")
    void validarReutilizaUsuarioClienteExistente() {
        ClienteClaimToken claim = claimValido();
        when(tokenRepo.findByToken("tok")).thenReturn(Optional.of(claim));
        when(clienteRepo.findById(clienteId)).thenReturn(Optional.of(preConta()));
        when(provisioning.provisionOrReuseCliente(any(), anyString(), anyString(), any(), anyString()))
            .thenReturn(new UserProvisioningService.ClienteProvisionResult("kc-existente", true));
        when(clienteRepo.findByTenantIdAndUsuarioId(tenant, pessoaId)).thenReturn(Optional.empty());

        ClaimService.AtivacaoResult r = service.validar("tok", "Senha#123");

        assertThat(r.getProviderUserId()).isEqualTo("kc-existente");
        assertThat(r.isContaExistente()).isTrue();

        ArgumentCaptor<Cliente> cli = ArgumentCaptor.forClass(Cliente.class);
        verify(clienteRepo).save(cli.capture());
        assertThat(cli.getValue().getUsuarioId()).isEqualTo(pessoaId);
        assertThat(cli.getValue().getStatusConta()).isEqualTo(Cliente.StatusConta.ATIVA);

        assertThat(claim.isUsado()).isTrue();
    }

    @Test
    @DisplayName("validar: conta de STAFF acumula o papel de cliente (identidade única, F2)")
    void validarStaffAcumulaPapelDeCliente() {
        // A antiga IdentityConflictException aplicava a regra revogada de
        // "populações que nunca se cruzam" — sob identidade única, a conta
        // existente do e-mail (staff incluído) é reutilizada e o claim conclui.
        ClienteClaimToken claim = claimValido();
        when(tokenRepo.findByToken("tok")).thenReturn(Optional.of(claim));
        when(clienteRepo.findById(clienteId)).thenReturn(Optional.of(preConta()));
        when(provisioning.provisionOrReuseCliente(any(), anyString(), anyString(), any(), anyString()))
            .thenReturn(new UserProvisioningService.ClienteProvisionResult("kc-sub-staff", true));

        var r = service.validar("tok", "Senha#123");

        assertThat(r.getProviderUserId()).isEqualTo("kc-sub-staff");
        assertThat(r.isContaExistente()).isTrue();

        ArgumentCaptor<Cliente> cli = ArgumentCaptor.forClass(Cliente.class);
        verify(clienteRepo).save(cli.capture());
        assertThat(cli.getValue().getUsuarioId()).isEqualTo(pessoaId);
        assertThat(claim.isUsado()).isTrue();
    }

    @Test
    @DisplayName("validar: pessoa já vinculada a OUTRO cadastro da loja é recusada (unique tenant+usuario)")
    void validarRecusaSubJaVinculadoNaLoja() {
        when(tokenRepo.findByToken("tok")).thenReturn(Optional.of(claimValido()));
        when(clienteRepo.findById(clienteId)).thenReturn(Optional.of(preConta()));
        when(provisioning.provisionOrReuseCliente(any(), anyString(), anyString(), any(), anyString()))
            .thenReturn(new UserProvisioningService.ClienteProvisionResult("kc-existente", true));
        when(clienteRepo.findByTenantIdAndUsuarioId(tenant, pessoaId))
            .thenReturn(Optional.of(Cliente.builder()
                .id(UUID.randomUUID()).tenantId(tenant)
                .nome("Outra Ficha").usuarioId(pessoaId)
                .statusConta(Cliente.StatusConta.ATIVA).build()));

        assertThatThrownBy(() -> service.validar("tok", "Senha#123")).isInstanceOf(BusinessException.class);
        verify(clienteRepo, never()).save(any());
    }

    @Test
    @DisplayName("validar: senha temporária incorreta é rejeitada (sem provisionar)")
    void validarSenhaErrada() {
        ClienteClaimToken claim = claimValido();
        claim.setTemporaryPassword("correta");
        when(tokenRepo.findByToken("tok")).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> service.validar("tok", "errada")).isInstanceOf(BusinessException.class);
        verify(provisioning, never()).provisionOrReuseCliente(any(), anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("validar: token expirado é rejeitado")
    void validarExpirado() {
        ClienteClaimToken claim = claimValido();
        claim.setExpiraEm(Instant.now().minus(1, ChronoUnit.DAYS));
        when(tokenRepo.findByToken("tok")).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> service.validar("tok", "Senha#123")).isInstanceOf(BusinessException.class);
        verify(clienteRepo, never()).save(any());
    }
}
