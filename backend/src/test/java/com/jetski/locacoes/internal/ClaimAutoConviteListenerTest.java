package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.domain.ClienteClaimToken;
import com.jetski.locacoes.event.PreContaCriadaEvent;
import com.jetski.locacoes.internal.repository.ClienteClaimTokenRepository;
import com.jetski.locacoes.internal.repository.ClienteIdentityProviderRepository;
import com.jetski.locacoes.internal.repository.ClienteRepository;
import com.jetski.shared.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Auto-convite na criação/reuso da pré-conta de balcão: dispara o claim
 * quando há e-mail e nenhum convite vigente; best-effort (nunca propaga).
 */
@DisplayName("ClaimAutoConviteListener")
class ClaimAutoConviteListenerTest {

    private final ClienteRepository clienteRepo = mock(ClienteRepository.class);
    private final ClienteClaimTokenRepository tokenRepo = mock(ClienteClaimTokenRepository.class);
    private final ClienteIdentityProviderRepository identityRepo = mock(ClienteIdentityProviderRepository.class);
    private final ClaimService claimService = mock(ClaimService.class);

    private final ClaimAutoConviteListener listener = new ClaimAutoConviteListener(
        clienteRepo, tokenRepo, identityRepo, claimService);

    private final UUID tenant = UUID.randomUUID();
    private final UUID clienteId = UUID.randomUUID();
    private final UUID staff = UUID.randomUUID();

    private PreContaCriadaEvent evento() {
        return PreContaCriadaEvent.of(tenant, clienteId, "BALCAO", staff);
    }

    private Cliente preConta(String email) {
        return Cliente.builder().id(clienteId).tenantId(tenant)
            .nome("Maria Souza").email(email)
            .statusConta(Cliente.StatusConta.PRE_CONTA).build();
    }

    @Test
    @DisplayName("com e-mail e sem convite vigente → gera o claim")
    void enviaComEmail() {
        when(clienteRepo.findById(clienteId)).thenReturn(Optional.of(preConta("maria@email.com")));
        when(identityRepo.existsByClienteId(clienteId)).thenReturn(false);
        when(tokenRepo.findByClienteIdAndAtivoTrue(clienteId)).thenReturn(List.of());

        listener.onPreContaCriada(evento());

        verify(claimService).gerar(clienteId, "email");
    }

    @Test
    @DisplayName("sem e-mail → não envia (convite fica manual)")
    void naoEnviaSemEmail() {
        when(clienteRepo.findById(clienteId)).thenReturn(Optional.of(preConta(null)));

        listener.onPreContaCriada(evento());

        verify(claimService, never()).gerar(any(), anyString());
    }

    @Test
    @DisplayName("convite vigente (token ativo não-expirado) → não reenvia (não invalida o link enviado)")
    void naoReenviaConviteVigente() {
        Cliente convidada = preConta("maria@email.com");
        convidada.setStatusConta(Cliente.StatusConta.CONVIDADA);
        when(clienteRepo.findById(clienteId)).thenReturn(Optional.of(convidada));
        when(identityRepo.existsByClienteId(clienteId)).thenReturn(false);
        when(tokenRepo.findByClienteIdAndAtivoTrue(clienteId)).thenReturn(List.of(
            ClienteClaimToken.builder().tenantId(tenant).clienteId(clienteId).token("tok")
                .ativo(true).expiraEm(Instant.now().plus(3, ChronoUnit.DAYS)).build()));

        listener.onPreContaCriada(evento());

        verify(claimService, never()).gerar(any(), anyString());
    }

    @Test
    @DisplayName("convite anterior EXPIRADO → reenvia automaticamente")
    void reenviaConviteExpirado() {
        Cliente convidada = preConta("maria@email.com");
        convidada.setStatusConta(Cliente.StatusConta.CONVIDADA);
        when(clienteRepo.findById(clienteId)).thenReturn(Optional.of(convidada));
        when(identityRepo.existsByClienteId(clienteId)).thenReturn(false);
        when(tokenRepo.findByClienteIdAndAtivoTrue(clienteId)).thenReturn(List.of(
            ClienteClaimToken.builder().tenantId(tenant).clienteId(clienteId).token("tok")
                .ativo(true).expiraEm(Instant.now().minus(1, ChronoUnit.DAYS)).build()));

        listener.onPreContaCriada(evento());

        verify(claimService).gerar(clienteId, "email");
    }

    @Test
    @DisplayName("conta já ATIVA ou identidade vinculada → não envia")
    void naoEnviaContaAtiva() {
        Cliente ativa = preConta("maria@email.com");
        ativa.setStatusConta(Cliente.StatusConta.ATIVA);
        when(clienteRepo.findById(clienteId)).thenReturn(Optional.of(ativa));

        listener.onPreContaCriada(evento());

        verify(claimService, never()).gerar(any(), anyString());
    }

    @Test
    @DisplayName("gerar lança exceção → listener não propaga (best-effort; pré-conta já commitada)")
    void naoPropagaFalha() {
        when(clienteRepo.findById(clienteId)).thenReturn(Optional.of(preConta("maria@email.com")));
        when(identityRepo.existsByClienteId(clienteId)).thenReturn(false);
        when(tokenRepo.findByClienteIdAndAtivoTrue(clienteId)).thenReturn(List.of());
        when(claimService.gerar(clienteId, "email")).thenThrow(new BusinessException("smtp fora"));

        assertThatCode(() -> listener.onPreContaCriada(evento())).doesNotThrowAnyException();
    }
}
