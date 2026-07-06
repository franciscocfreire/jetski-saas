package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.event.PreContaCriadaEvent;
import com.jetski.locacoes.internal.repository.ClienteRepository;
import com.jetski.shared.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pré-conta de balcão: dedupe por CPF e publicação do PreContaCriadaEvent
 * (que alimenta a auditoria E o auto-convite do cliente).
 */
@DisplayName("ClienteService — pré-conta de balcão")
class ClienteServiceTest {

    private final ClienteRepository clienteRepo = mock(ClienteRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    private final ClienteService service = new ClienteService(clienteRepo, events);

    private final UUID tenant = UUID.randomUUID();

    private Cliente dados() {
        return Cliente.builder().tenantId(tenant)
            .nome("Regis Porta").email("regis@email.com").documento("469.441.130-66")
            .build();
    }

    @Test
    @DisplayName("criação nova → salva PRE_CONTA/BALCAO e publica evento origem=BALCAO")
    void criacaoNovaPublicaEvento() {
        when(clienteRepo.findByDocumento("469.441.130-66")).thenReturn(Optional.empty());
        when(clienteRepo.save(any(Cliente.class))).thenAnswer(i -> {
            Cliente c = i.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        Cliente r = service.criarPreConta(dados());

        assertThat(r.getStatusConta()).isEqualTo(Cliente.StatusConta.PRE_CONTA);
        assertThat(r.getOrigem()).isEqualTo(Cliente.Origem.BALCAO);

        ArgumentCaptor<Object> ev = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(ev.capture());
        PreContaCriadaEvent evento = (PreContaCriadaEvent) ev.getValue();
        assertThat(evento.origem()).isEqualTo("BALCAO");
        assertThat(evento.clienteId()).isEqualTo(r.getId());
    }

    @Test
    @DisplayName("reaproveitamento (CPF existente não-ativo) → retorna existente e publica origem=BALCAO_REUSO")
    void reusoPublicaEvento() {
        Cliente existente = dados();
        existente.setId(UUID.randomUUID());
        existente.setStatusConta(Cliente.StatusConta.PRE_CONTA);
        when(clienteRepo.findByDocumento("469.441.130-66")).thenReturn(Optional.of(existente));

        Cliente r = service.criarPreConta(dados());

        assertThat(r.getId()).isEqualTo(existente.getId());
        verify(clienteRepo, never()).save(any());

        ArgumentCaptor<Object> ev = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(ev.capture());
        assertThat(((PreContaCriadaEvent) ev.getValue()).origem()).isEqualTo("BALCAO_REUSO");
    }

    @Test
    @DisplayName("CPF com conta ATIVA → bloqueia (exige OTP) e não publica evento")
    void contaAtivaBloqueia() {
        Cliente ativa = dados();
        ativa.setId(UUID.randomUUID());
        ativa.setStatusConta(Cliente.StatusConta.ATIVA);
        when(clienteRepo.findByDocumento("469.441.130-66")).thenReturn(Optional.of(ativa));

        assertThatThrownBy(() -> service.criarPreConta(dados()))
            .isInstanceOf(BusinessException.class);
        verify(events, never()).publishEvent(any());
    }
}
