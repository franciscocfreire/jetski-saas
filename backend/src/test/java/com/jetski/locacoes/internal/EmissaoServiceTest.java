package com.jetski.locacoes.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.domain.DocumentoEmitido;
import com.jetski.locacoes.domain.Reserva;
import com.jetski.locacoes.domain.ReservaAceite;
import com.jetski.locacoes.domain.ReservaHabilitacao;
import com.jetski.locacoes.internal.repository.ClienteRepository;
import com.jetski.locacoes.internal.repository.DocumentoEmitidoRepository;
import com.jetski.locacoes.internal.repository.ReservaAceiteRepository;
import com.jetski.locacoes.internal.repository.ReservaHabilitacaoRepository;
import com.jetski.locacoes.internal.repository.ReservaRepository;
import com.jetski.reservas.domain.event.DocumentosEmitidosEvent;
import com.jetski.shared.email.EmailService;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.storage.PresignedUrl;
import com.jetski.shared.storage.StorageService;
import com.jetski.tenant.TenantQueryService;
import com.jetski.tenant.domain.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2.6 — orquestração da emissão (gera → arquiva → registra → envia → evento).
 */
@DisplayName("EmissaoService (F2.6)")
class EmissaoServiceTest {

    private final ReservaRepository reservaRepo = mock(ReservaRepository.class);
    private final ClienteRepository clienteRepo = mock(ClienteRepository.class);
    private final com.jetski.locacoes.internal.repository.InstrutorRepository instrutorRepo =
        mock(com.jetski.locacoes.internal.repository.InstrutorRepository.class);
    private final ReservaHabilitacaoRepository habRepo = mock(ReservaHabilitacaoRepository.class);
    private final ReservaAceiteRepository aceiteRepo = mock(ReservaAceiteRepository.class);
    private final DocumentoEmitidoRepository docRepo = mock(DocumentoEmitidoRepository.class);
    private final StorageService storage = mock(StorageService.class);
    private final EmailService email = mock(EmailService.class);
    private final TenantQueryService tenantQuery = mock(TenantQueryService.class);
    private final DocumentoPdfService pdfService = mock(DocumentoPdfService.class);
    private final ClienteAnexoService anexoService = mock(ClienteAnexoService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final com.jetski.shared.assinatura.CarimboTempoService carimboService =
        mock(com.jetski.shared.assinatura.CarimboTempoService.class);
    private final PadesSignatureService padesService = mock(PadesSignatureService.class);

    private final EmissaoService service = new EmissaoService(
        reservaRepo, clienteRepo, instrutorRepo, habRepo, aceiteRepo, docRepo, storage, email,
        tenantQuery, pdfService, anexoService, events, new ObjectMapper(), carimboService, padesService);

    private final UUID tenant = UUID.randomUUID();
    private final UUID reservaId = UUID.randomUUID();
    private final UUID clienteId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(reservaRepo.findById(reservaId))
            .thenReturn(Optional.of(Reserva.builder().id(reservaId).tenantId(tenant).clienteId(clienteId).build()));
        when(habRepo.findByReservaId(reservaId)).thenReturn(Optional.of(ReservaHabilitacao.builder()
            .via(ReservaHabilitacao.Via.EMA).resolvida(true)
            .anexoSaude(true).anexoRegras(true).anexoResidencia(true).instrutorId(UUID.randomUUID())
            .gruNumero("GRU-1").gruValor(new BigDecimal("23.13")).build()));
        when(aceiteRepo.findFirstByReservaIdOrderByAceitoEmDesc(reservaId))
            .thenReturn(Optional.of(ReservaAceite.builder().assinaturaS3Key("t/r/assinatura.png").build()));
        when(clienteRepo.findById(clienteId)).thenReturn(Optional.of(Cliente.builder()
            .id(clienteId)
            .nome("Roberto Lima").documento("987.654.321-00").email("roberto@email.com")
            .rg("12.345.678-9").orgaoEmissor("SSP/SP").nacionalidade("Brasileira").naturalidade("São Paulo/SP")
            .enderecoJson("{\"logradouro\":\"Av. Paulista\",\"numero\":\"1500\",\"cidade\":\"São Paulo\",\"uf\":\"SP\",\"cep\":\"01310-100\"}")
            .build()));
        when(tenantQuery.findById(tenant)).thenReturn(Tenant.builder()
            .razaoSocial("Jet Save Turismo Náutico LTDA").cnpj("65.455.888/0001-00")
            .marinhaEmail("capitania@example.com").cidade("Angra dos Reis").build());
        when(storage.getObject(anyString())).thenReturn("png".getBytes());
        when(storage.generatePresignedDownloadUrl(anyString(), anyInt()))
            .thenReturn(PresignedUrl.builder().url("http://download/doc.pdf").build());
        when(pdfService.gerarDocumentoConsolidado(any(), any(), any(), any(), nullable(String.class)))
            .thenReturn(new DocumentoPdfService.DocumentoPdf("%PDF-fake".getBytes(), "abc123hash"));
        when(docRepo.save(any(DocumentoEmitido.class))).thenAnswer(i -> i.getArgument(0));
        // Anexos obrigatórios à Marinha presentes (identidade + selfie, por padrão).
        when(anexoService.buscar(eq(clienteId), any(com.jetski.locacoes.domain.ClienteAnexo.Tipo.class)))
            .thenReturn(Optional.of(mock(com.jetski.locacoes.domain.ClienteAnexo.class)));
    }

    @Test
    @DisplayName("Emissão completa: gera, arquiva, registra, envia (marinha+cliente) e publica evento")
    void emissaoCompleta() {
        EmissaoService.ResultadoEmissao r = service.emitir(reservaId);

        assertThat(r.getHashSha256()).isEqualTo("abc123hash");
        assertThat(r.getDownloadUrl()).isEqualTo("http://download/doc.pdf");
        assertThat(r.isEnviadoMarinha()).isTrue();
        assertThat(r.isEnviadoCliente()).isTrue();
        assertThat(r.getGruNumero()).isEqualTo("GRU-1");

        // Dois PDFs arquivados: visão do cliente (canônico) + recorte da Marinha.
        verify(storage, times(2)).putObject(anyString(), any(), eq("application/pdf"));
        verify(docRepo).save(any(DocumentoEmitido.class));
        verify(reservaRepo).save(any(Reserva.class)); // documento_emitido_em
        verify(email, times(2)).sendEmailComAnexo(anyString(), anyString(), anyString(), anyString(), any(), anyString());
        verify(events).publishEvent(any(DocumentosEmitidosEvent.class));
    }

    @Test
    @DisplayName("Bloqueia emissão se habilitação não resolvida")
    void bloqueiaSemHabilitacaoResolvida() {
        when(habRepo.findByReservaId(reservaId)).thenReturn(Optional.of(
            ReservaHabilitacao.builder().via(ReservaHabilitacao.Via.EMA).resolvida(false).build()));

        assertThatThrownBy(() -> service.emitir(reservaId)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Bloqueia emissão se termos não assinados (aceite ausente)")
    void bloqueiaSemAceite() {
        when(aceiteRepo.findFirstByReservaIdOrderByAceitoEmDesc(reservaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.emitir(reservaId)).isInstanceOf(BusinessException.class);
    }
}
