package com.jetski.locacoes.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.domain.Reserva;
import com.jetski.locacoes.domain.ReservaHabilitacao;
import com.jetski.locacoes.internal.gru.GruClient;
import com.jetski.locacoes.internal.gru.GruContribuinte;
import com.jetski.locacoes.internal.gru.GruException;
import com.jetski.locacoes.internal.gru.GruResultado;
import com.jetski.locacoes.internal.repository.ClienteRepository;
import com.jetski.locacoes.internal.repository.ReservaHabilitacaoRepository;
import com.jetski.locacoes.internal.repository.ReservaRepository;
import com.jetski.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GruServiceTest {

    @Mock ReservaHabilitacaoRepository habilitacaoRepository;
    @Mock ReservaRepository reservaRepository;
    @Mock ClienteRepository clienteRepository;
    @Mock GruClient gruClient;
    @Mock com.jetski.shared.storage.StorageService storageService;

    GruService service;

    final UUID reservaId = UUID.randomUUID();
    final UUID clienteId = UUID.randomUUID();
    final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new GruService(habilitacaoRepository, reservaRepository,
            clienteRepository, gruClient, new ObjectMapper(), storageService);
    }

    private void stubReservaECliente() {
        Reserva reserva = new Reserva();
        reserva.setClienteId(clienteId);
        reserva.setTenantId(tenantId);
        when(reservaRepository.findById(reservaId)).thenReturn(Optional.of(reserva));

        Cliente cliente = new Cliente();
        cliente.setNome("Fulano");
        cliente.setDocumento("382.489.718-05");
        cliente.setGenero("MASCULINO");
        cliente.setEnderecoJson("{\"cep\":\"11095460\",\"logradouro\":\"Rua X\",\"numero\":\"10\",\"cidade\":\"Santos\",\"uf\":\"SP\"}");
        when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(cliente));
    }

    @Test
    void geraEPersisteGru() {
        stubReservaECliente();
        when(habilitacaoRepository.findByReservaId(reservaId)).thenReturn(Optional.empty());
        when(gruClient.gerar(any(GruContribuinte.class))).thenReturn(new GruResultado(
            "60893100225672026", new BigDecimal("60.32"), "CHA",
            "PIXEMV", "QR", Instant.now().plus(1, ChronoUnit.HOURS), "7976123"));
        when(habilitacaoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        GruService.GruGeracao g = service.gerarGru(reservaId);

        assertThat(g.sucesso()).isTrue();
        assertThat(g.reaproveitada()).isFalse();
        assertThat(g.qrPngBase64()).isEqualTo("QR");
        ArgumentCaptor<ReservaHabilitacao> cap = ArgumentCaptor.forClass(ReservaHabilitacao.class);
        verify(habilitacaoRepository).save(cap.capture());
        ReservaHabilitacao salvo = cap.getValue();
        assertThat(salvo.getGruNumero()).isEqualTo("60893100225672026");
        assertThat(salvo.getGruPixCopiaECola()).isEqualTo("PIXEMV");
        assertThat(salvo.getGruIdMarinha()).isEqualTo("7976123");
        assertThat(salvo.getGruGeradaEm()).isNotNull();
    }

    @Test
    void mandaCpfSoComDigitos() {
        stubReservaECliente();
        when(habilitacaoRepository.findByReservaId(reservaId)).thenReturn(Optional.empty());
        when(gruClient.gerar(any())).thenReturn(new GruResultado(
            "1", BigDecimal.ONE, "CHA", "PIX", "QR", null, "9"));
        when(habilitacaoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.gerarGru(reservaId);

        ArgumentCaptor<GruContribuinte> cap = ArgumentCaptor.forClass(GruContribuinte.class);
        verify(gruClient).gerar(cap.capture());
        assertThat(cap.getValue().cpf()).isEqualTo("38248971805");
        assertThat(cap.getValue().sexo()).isEqualTo("M");
        assertThat(cap.getValue().cep()).isEqualTo("11095460");
    }

    @Test
    void reaproveitaGruValidaNaoVencida() {
        stubReservaECliente();
        ReservaHabilitacao existente = ReservaHabilitacao.builder()
            .reservaId(reservaId).tenantId(tenantId).via(ReservaHabilitacao.Via.EMA)
            .gruNumero("123").gruPixCopiaECola("PIX")
            .gruPixExpiracao(Instant.now().plus(2, ChronoUnit.HOURS))
            .build();
        when(habilitacaoRepository.findByReservaId(reservaId)).thenReturn(Optional.of(existente));

        GruService.GruGeracao g = service.gerarGru(reservaId);

        assertThat(g.sucesso()).isTrue();
        assertThat(g.reaproveitada()).isTrue();
        verify(gruClient, never()).gerar(any());
        verify(habilitacaoRepository, never()).save(any());
    }

    @Test
    void regeneraQuandoPixVencido() {
        stubReservaECliente();
        ReservaHabilitacao vencida = ReservaHabilitacao.builder()
            .reservaId(reservaId).tenantId(tenantId).via(ReservaHabilitacao.Via.EMA)
            .gruNumero("123").gruPixCopiaECola("PIX")
            .gruPixExpiracao(Instant.now().minus(1, ChronoUnit.HOURS))
            .build();
        when(habilitacaoRepository.findByReservaId(reservaId)).thenReturn(Optional.of(vencida));
        when(gruClient.gerar(any())).thenReturn(new GruResultado(
            "999", BigDecimal.TEN, "CHA", "NOVO", "QR",
            Instant.now().plus(1, ChronoUnit.HOURS), "7"));
        when(habilitacaoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        GruService.GruGeracao g = service.gerarGru(reservaId);

        assertThat(g.reaproveitada()).isFalse();
        assertThat(g.habilitacao().getGruNumero()).isEqualTo("999");
    }

    @Test
    void falhaDoClienteCaiNoFallback() {
        stubReservaECliente();
        when(habilitacaoRepository.findByReservaId(reservaId)).thenReturn(Optional.empty());
        when(gruClient.gerar(any())).thenThrow(
            new GruException(GruException.Codigo.MARINHA_INDISPONIVEL, "fora do ar"));

        GruService.GruGeracao g = service.gerarGru(reservaId);

        assertThat(g.sucesso()).isFalse();
        assertThat(g.erroCodigo()).isEqualTo("MARINHA_INDISPONIVEL");
        verify(habilitacaoRepository, never()).save(any());
    }

    @Test
    void reservaInexistenteLancaNotFound() {
        when(reservaRepository.findById(reservaId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.gerarGru(reservaId))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void geraBoletoArmazenaPdfEDevolveUrl() {
        stubReservaECliente();
        when(habilitacaoRepository.findByReservaId(reservaId)).thenReturn(Optional.empty());
        when(gruClient.gerarBoleto(any())).thenReturn(
            new com.jetski.locacoes.internal.gru.GruBoletoResultado("7977050", new byte[]{1, 2, 3}));
        when(habilitacaoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(storageService.generatePresignedDownloadUrl(any(), org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(com.jetski.shared.storage.PresignedUrl.builder().url("https://dl/boleto.pdf").build());

        GruService.BoletoGeracao g = service.gerarBoleto(reservaId);

        assertThat(g.sucesso()).isTrue();
        assertThat(g.reaproveitada()).isFalse();
        assertThat(g.downloadUrl()).isEqualTo("https://dl/boleto.pdf");
        ArgumentCaptor<ReservaHabilitacao> cap = ArgumentCaptor.forClass(ReservaHabilitacao.class);
        verify(habilitacaoRepository).save(cap.capture());
        assertThat(cap.getValue().getGruPdfS3Key())
            .isEqualTo(tenantId + "/reserva/" + reservaId + "/gru-boleto.pdf");
        assertThat(cap.getValue().getGruIdMarinha()).isEqualTo("7977050");
        verify(storageService).putObject(any(), any(), org.mockito.ArgumentMatchers.eq("application/pdf"));
    }

    @Test
    void reaproveitaBoletoExistenteNaoPago() {
        stubReservaECliente();
        ReservaHabilitacao existente = ReservaHabilitacao.builder()
            .reservaId(reservaId).tenantId(tenantId).via(ReservaHabilitacao.Via.EMA)
            .gruPdfS3Key(tenantId + "/reserva/" + reservaId + "/gru-boleto.pdf")
            .gruPago(false)
            .build();
        when(habilitacaoRepository.findByReservaId(reservaId)).thenReturn(Optional.of(existente));
        when(storageService.generatePresignedDownloadUrl(any(), org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(com.jetski.shared.storage.PresignedUrl.builder().url("https://dl/again.pdf").build());

        GruService.BoletoGeracao g = service.gerarBoleto(reservaId);

        assertThat(g.reaproveitada()).isTrue();
        assertThat(g.downloadUrl()).isEqualTo("https://dl/again.pdf");
        verify(gruClient, never()).gerarBoleto(any());
        verify(habilitacaoRepository, never()).save(any());
    }
}
