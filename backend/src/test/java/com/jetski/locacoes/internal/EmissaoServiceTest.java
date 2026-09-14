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
    private final com.jetski.creditos.CreditoService creditoService =
        mock(com.jetski.creditos.CreditoService.class);
    private final com.jetski.tenant.PlanoLimiteService planoLimiteService =
        mock(com.jetski.tenant.PlanoLimiteService.class);
    private final VinculoEmissaoService vinculoEmissaoService =
        mock(VinculoEmissaoService.class);

    private final ClienteNotificacaoService notificacaoService =
        mock(ClienteNotificacaoService.class);
    // Serviço de envio REAL, ligado aos mesmos mocks: é ele que monta o ofício
    // NORMAM-212 e chama o EmailService, então os asserts do ofício continuam
    // valendo aqui. Mockar este colaborador faria os verify(email, ...) pararem de
    // disparar e a cobertura sumiria sem nenhum teste ficar vermelho.
    private final DocumentoEnvioService envioService = new DocumentoEnvioService(
        reservaRepo, clienteRepo, habRepo, docRepo, storage, email, tenantQuery,
        anexoService, new ObjectMapper());
    private final EmissaoEnvioConfigService envioConfig = mock(EmissaoEnvioConfigService.class);
    private final EmissaoLockService lockService = mock(EmissaoLockService.class);

    private final EmissaoService service = new EmissaoService(
        reservaRepo, mock(CustomerHabilitacaoSyncService.class), notificacaoService, clienteRepo, instrutorRepo, habRepo, aceiteRepo, docRepo, storage,
        tenantQuery, pdfService, anexoService, creditoService, planoLimiteService,
        vinculoEmissaoService, events, new ObjectMapper(),
        carimboService, padesService, envioService, envioConfig, lockService);

    private final UUID tenant = UUID.randomUUID();
    private final UUID reservaId = UUID.randomUUID();
    private final UUID clienteId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Modo SÍNCRONO nos testes: o envio acontece dentro do emitir() e os asserts de
        // enviadoMarinha/enviadoCliente valem sem esperar por thread. O caminho
        // assíncrono é coberto pelos testes próprios abaixo.
        when(envioConfig.assincrono()).thenReturn(false);
        // Reserva sem documento anterior: a idempotência não intercepta.
        when(docRepo.findByTenantIdAndReservaIdOrderByEmitidoEmDesc(any(UUID.class), any(UUID.class)))
            .thenReturn(java.util.List.of());
        // Plano com emissão própria → comportamento clássico (delegação só quando ausente)
        when(planoLimiteService.moduloHabilitado(any(UUID.class),
            eq(com.jetski.tenant.ModuloPlano.EMISSAO_PROPRIA))).thenReturn(true);
        when(reservaRepo.findById(reservaId))
            .thenReturn(Optional.of(Reserva.builder().id(reservaId).tenantId(tenant).clienteId(clienteId).build()));
        when(habRepo.findByReservaId(reservaId)).thenReturn(Optional.of(ReservaHabilitacao.builder()
            .via(ReservaHabilitacao.Via.EMA).resolvida(true)
            .anexoSaude(true).anexoRegras(true).anexoResidencia(true).instrutorId(UUID.randomUUID())
            // Videoaula (V063): obrigatória por padrão — sem ela a Marinha não recebe.
            .videoaulaEm(java.time.Instant.now()).videoaulaModo(ReservaHabilitacao.VideoaulaModo.PLAYER)
            .videoaulaIdioma("pt")
            .gruNumero("GRU-1").gruValor(new BigDecimal("23.13")).build()));
        when(aceiteRepo.findFirstByReservaIdOrderByAceitoEmDesc(reservaId))
            .thenReturn(Optional.of(ReservaAceite.builder().assinaturaS3Key("t/r/assinatura.png").build()));
        when(clienteRepo.findById(clienteId)).thenReturn(Optional.of(Cliente.builder()
            .id(clienteId).tenantId(tenant)
            .nome("Roberto Lima").documento("987.654.321-00").email("roberto@email.com")
            .rg("12.345.678-9").orgaoEmissor("SSP/SP").nacionalidade("Brasileira").naturalidade("São Paulo/SP")
            .enderecoJson("{\"logradouro\":\"Av. Paulista\",\"numero\":\"1500\",\"cidade\":\"São Paulo\",\"uf\":\"SP\",\"cep\":\"01310-100\"}")
            .build()));
        when(tenantQuery.findById(tenant)).thenReturn(Tenant.builder()
            .razaoSocial("Jet Save Turismo Náutico LTDA").cnpj("65.455.888/0001-00")
            .marinhaEmail("capitania@example.com").cidade("Angra dos Reis")
            .responsavelNome("Maria da Silva").emailOficial("eama@jetsave.com.br")
            .emissoraHabilitada(true).build());
        when(storage.getObject(anyString())).thenReturn("png".getBytes());
        when(storage.generatePresignedDownloadUrl(anyString(), anyInt()))
            .thenReturn(PresignedUrl.builder().url("http://download/doc.pdf").build());
        when(pdfService.gerarDocumentoConsolidado(any(), any(), any(), any(), nullable(String.class)))
            .thenReturn(new DocumentoPdfService.DocumentoPdf("%PDF-fake".getBytes(), "abc123hash"));
        when(docRepo.save(any(DocumentoEmitido.class))).thenAnswer(i -> i.getArgument(0));
        // Anexos obrigatórios à Marinha presentes (identidade + selfie, por padrão).
        when(anexoService.buscar(eq(tenant), eq(clienteId), any(com.jetski.locacoes.domain.ClienteAnexo.Tipo.class)))
            .thenReturn(Optional.of(mock(com.jetski.locacoes.domain.ClienteAnexo.class)));
    }

    @Test
    @DisplayName("Modo assíncrono: nada de SMTP dentro do emitir — status PENDENTE e evento publicado")
    void modoAssincronoNaoEnviaNoRequest() {
        when(envioConfig.assincrono()).thenReturn(true);

        EmissaoService.ResultadoEmissao r = service.emitir(reservaId);

        // O que motivou a mudança: os ~24 s de SMTP não podem acontecer no request.
        verify(email, org.mockito.Mockito.never())
            .sendEmailComAnexo(anyString(), anyString(), anyString(), anyString(), any(), anyString(),
                nullable(String.class), any());
        assertThat(r.getMarinhaEnvioStatus()).isEqualTo(com.jetski.locacoes.domain.EnvioStatus.PENDENTE);
        assertThat(r.getClienteEnvioStatus()).isEqualTo(com.jetski.locacoes.domain.EnvioStatus.PENDENTE);
        assertThat(r.isEnviadoMarinha()).isFalse();
        assertThat(r.isEnviadoCliente()).isFalse();
        // O documento em si saiu: PDF arquivado e crédito debitado como sempre.
        verify(storage, times(2)).putObject(anyString(), any(), eq("application/pdf"));
        verify(creditoService).debitarEmissaoDocumento(eq(tenant), any(), eq(reservaId));
        verify(events).publishEvent(any(com.jetski.locacoes.event.EnvioDocumentosSolicitadoEvent.class));
    }

    @Test
    @DisplayName("Idempotência: segunda emissão devolve o documento existente, sem PDF, crédito ou e-mail")
    void segundaEmissaoReaproveitaDocumento() {
        UUID docExistente = UUID.randomUUID();
        when(docRepo.findByTenantIdAndReservaIdOrderByEmitidoEmDesc(tenant, reservaId))
            .thenReturn(java.util.List.of(DocumentoEmitido.builder()
                .id(docExistente).tenantId(tenant).reservaId(reservaId)
                .s3Key("t/reserva/r/documento.pdf").hashSha256("abc123hash")
                .marinhaEnvioStatus(com.jetski.locacoes.domain.EnvioStatus.ENVIADO)
                .clienteEnvioStatus(com.jetski.locacoes.domain.EnvioStatus.ENVIADO)
                .build()));

        EmissaoService.ResultadoEmissao r = service.emitir(reservaId);

        // Foi exatamente isto que debitou 2 créditos em produção: F5 + segundo clique.
        assertThat(r.isReaproveitado()).isTrue();
        assertThat(r.getDocumentoId()).isEqualTo(docExistente);
        verify(creditoService, org.mockito.Mockito.never()).debitarEmissaoDocumento(any(), any(), any());
        verify(storage, org.mockito.Mockito.never()).putObject(anyString(), any(), anyString());
        verify(docRepo, org.mockito.Mockito.never()).save(any(DocumentoEmitido.class));
        verify(email, org.mockito.Mockito.never())
            .sendEmailComAnexo(anyString(), anyString(), anyString(), anyString(), any(), anyString(),
                nullable(String.class), any());
    }

    @Test
    @DisplayName("Idempotência: reemitir=true força nova emissão, com novo crédito e key versionada")
    void reemitirForcaNovaEmissao() {
        when(docRepo.findByTenantIdAndReservaIdOrderByEmitidoEmDesc(tenant, reservaId))
            .thenReturn(java.util.List.of(DocumentoEmitido.builder()
                .id(UUID.randomUUID()).tenantId(tenant).reservaId(reservaId)
                .s3Key("t/reserva/r/documento.pdf").hashSha256("abc123hash").build()));

        EmissaoService.ResultadoEmissao r = service.emitir(reservaId, true);

        assertThat(r.isReaproveitado()).isFalse();
        verify(creditoService).debitarEmissaoDocumento(eq(tenant), any(), eq(reservaId));
        // Key versionada: a fixa sobrescreveria o PDF da emissão anterior, deixando a
        // linha antiga com um hash_sha256 que não confere mais.
        org.mockito.ArgumentCaptor<String> keys = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(storage, times(2)).putObject(keys.capture(), any(), eq("application/pdf"));
        assertThat(keys.getAllValues().get(0)).doesNotEndWith("/documento.pdf");
        assertThat(keys.getAllValues().get(1)).endsWith("-marinha.pdf");
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
        verify(events).publishEvent(any(DocumentosEmitidosEvent.class));

        // Marinha: ofício NORMAM-212 5.4.2 — assunto com a reserva no final, anexo "Nome CPF.pdf",
        // GRU no corpo, Reply-To = e-mail oficial do EAMA. Cliente: e-mail próprio (6 args).
        org.mockito.ArgumentCaptor<String> subject = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<String> body = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<String> anexo = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(email).sendEmailComAnexo(eq("capitania@example.com"), subject.capture(), body.capture(),
            anexo.capture(), any(), eq("application/pdf"), eq("eama@jetsave.com.br"),
            // remetente explícito = quem emite (própria: o tenant), nunca o "Meu Jet" global
            eq(new EmailService.Remetente(tenant, "Jet Save Turismo Náutico LTDA")));
        assertThat(subject.getValue())
            .startsWith("Solicitação de Emissão de CHA-MTA-E – Roberto Lima – CPF 987.654.321-00 – reserva #")
            .endsWith("reserva #" + reservaId.toString().substring(0, 8));
        assertThat(anexo.getValue()).isEqualTo("Roberto Lima 987.654.321-00.pdf");
        assertThat(body.getValue())
            .contains("item 5.4.2").contains("GRU paga: <b>GRU-1</b>")
            .contains("Anexo 5-C").contains("Anexo 5-B").contains("Anexo 1-C")
            .contains("Documento oficial de identificação")
            .contains("<b>Maria da Silva</b><br>EAMA Jet Save Turismo Náutico LTDA<br>CNPJ: 65.455.888/0001-00")
            .doesNotContain("operado por"); // própria: sem operadora na assinatura

        // Cliente: em nome da loja, com a reserva no assunto e o resumo no corpo.
        org.mockito.ArgumentCaptor<String> assuntoCliente = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<String> corpoCliente = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(email).sendEmailComAnexo(eq("roberto@email.com"), assuntoCliente.capture(), corpoCliente.capture(),
            anyString(), any(), anyString(), nullable(String.class),
            eq(new EmailService.Remetente(tenant, "Jet Save Turismo Náutico LTDA")));
        String codigo = "#" + reservaId.toString().substring(0, 8);
        assertThat(assuntoCliente.getValue())
            .isEqualTo("Seus documentos — Jet Save Turismo Náutico LTDA — reserva " + codigo);
        assertThat(corpoCliente.getValue())
            .contains("Olá, <b>Roberto Lima</b>").contains("<b>" + codigo + "</b>")
            .contains("987.654.321-00").contains("<b>GRU-1</b> — R$ 23,13")
            .contains("habilitação temporária (CHA-MTA-E)").doesNotContain(" pela EAMA ")
            .contains("E-mail: eama@jetsave.com.br").contains("Equipe Jet Save Turismo Náutico LTDA");
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

    @Test
    @DisplayName("Sem créditos: bloqueia ANTES do trabalho pesado — nada é gerado nem salvo")
    void bloqueiaSemCreditos() {
        org.mockito.Mockito.doThrow(new BusinessException("Créditos de emissão esgotados."))
            .when(creditoService).verificarSaldoDisponivel(tenant);

        assertThatThrownBy(() -> service.emitir(reservaId))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Créditos");

        verify(pdfService, org.mockito.Mockito.never())
            .gerarDocumentoConsolidado(any(), any(), any(), any(), nullable(String.class));
        verify(docRepo, org.mockito.Mockito.never()).save(any(DocumentoEmitido.class));
    }

    @Test
    @DisplayName("Videoaula (V063): sem o módulo VIDEO_ORIENTACAO ela é sempre exigida — falta vira pendência e trava a Marinha")
    void videoaulaObrigatoriaSemModulo() {
        when(habRepo.findByReservaId(reservaId)).thenReturn(Optional.of(ReservaHabilitacao.builder()
            .via(ReservaHabilitacao.Via.EMA).resolvida(true)
            .anexoSaude(true).anexoRegras(true).anexoResidencia(true).instrutorId(UUID.randomUUID())
            .gruNumero("GRU-1").build()));
        // módulo ausente (default do mock = false) e tenant sem config → exigida

        EmissaoService.ResultadoEmissao r = service.emitir(reservaId);

        assertThat(r.isDocCompleta()).isFalse();
        assertThat(r.getPendencias()).contains("Videoaula de orientação não assistida");
        assertThat(r.isEnviadoMarinha()).isFalse();
    }

    @Test
    @DisplayName("Videoaula (V063): com o módulo e o toggle desligado, a falta NÃO é pendência")
    void videoaulaDesligadaComModulo() {
        when(planoLimiteService.moduloHabilitado(any(UUID.class),
            eq(com.jetski.tenant.ModuloPlano.VIDEO_ORIENTACAO))).thenReturn(true);
        when(habRepo.findByReservaId(reservaId)).thenReturn(Optional.of(ReservaHabilitacao.builder()
            .via(ReservaHabilitacao.Via.EMA).resolvida(true)
            .anexoSaude(true).anexoRegras(true).anexoResidencia(true).instrutorId(UUID.randomUUID())
            .gruNumero("GRU-1").build()));
        var padrao = com.jetski.tenant.domain.DocumentoConfig.padrao();
        var obr = padrao.obrigatoriosMarinha();
        var cfg = new com.jetski.tenant.domain.DocumentoConfig(padrao.marinha(), padrao.cliente(),
            new com.jetski.tenant.domain.DocumentoConfig.ObrigatoriosMarinha(
                obr.identidade(), obr.selfie(), obr.saude(), obr.regras(), obr.residencia(),
                obr.instrutor(), obr.nacionalidade(), obr.naturalidade(), false));
        when(tenantQuery.findById(tenant)).thenReturn(Tenant.builder()
            .razaoSocial("Jet Save Turismo Náutico LTDA").cnpj("65.455.888/0001-00")
            .marinhaEmail("capitania@example.com").cidade("Angra dos Reis")
            .emissoraHabilitada(true).documentoConfig(cfg).build());

        EmissaoService.ResultadoEmissao r = service.emitir(reservaId);

        assertThat(r.getPendencias()).doesNotContain("Videoaula de orientação não assistida");
        assertThat(r.isEnviadoMarinha()).isTrue();

        // Toggle desligado SEM o módulo continua exigindo (o módulo é a permissão de desligar).
        assertThat(cfg.videoaulaExigida(false)).isTrue();
        assertThat(cfg.videoaulaExigida(true)).isFalse();
        assertThat(padrao.videoaulaExigida(true)).isTrue();
    }

    @Test
    @DisplayName("Página de auditoria descreve COMO a videoaula foi cumprida (player+idioma+quando / declarada / —)")
    void descricaoVideoaulaAuditoria() {
        java.time.Instant em = java.time.Instant.parse("2026-09-06T17:32:10Z"); // 14:32:10 em São Paulo
        assertThat(EmissaoService.descricaoVideoaula(ReservaHabilitacao.builder()
            .videoaulaEm(em).videoaulaModo(ReservaHabilitacao.VideoaulaModo.PLAYER).videoaulaIdioma("pt").build()))
            .isEqualTo("Sim — assistida no balcão (player integrado, PT) em 06/09/2026 14:32:10");
        assertThat(EmissaoService.descricaoVideoaula(ReservaHabilitacao.builder()
            .videoaulaEm(em).videoaulaModo(ReservaHabilitacao.VideoaulaModo.DECLARACAO).build()))
            .isEqualTo("Sim — declarada em 06/09/2026 14:32:10");
        assertThat(EmissaoService.descricaoVideoaula(ReservaHabilitacao.builder().build())).isEqualTo("—");
    }

    @Test
    @DisplayName("Emissão via EMA debita 1 crédito com o id do documento")
    void debitaCreditoNaEmissao() {
        service.emitir(reservaId);

        verify(creditoService).verificarSaldoDisponivel(tenant);
        verify(creditoService).debitarEmissaoDocumento(eq(tenant), any(), eq(reservaId));
    }

    // ------------------------------------------------------------------
    // Ofício à Capitania na emissão delegada: TUDO da EAMA, nada da operadora
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Delegada: o snapshot manda; o que falta cai na EAMA ATUAL, nunca na operadora (§8.J)")
    void emissorDelegadoNuncaCaiNaOperadora() {
        UUID emissora = UUID.randomUUID();
        Tenant operadora = Tenant.builder().razaoSocial("Operadora Praia LTDA")
            .marinhaEmail("capitania-da-operadora@example.com").emailOficial("oficial@operadora.com").build();
        Tenant eama = Tenant.builder().razaoSocial("EAMA Atual LTDA").cnpj("22.222.222/0001-22")
            .eamaRegistro("EAMA-SP-999").responsavelNome("Ana Souza").telefone("(13) 99999-0000")
            .emailOficial("oficial@eama.com").marinhaEmail("capitania-sp@example.com")
            .emailRemetente("contato@eama.com").build();
        ObjectMapper om = new ObjectMapper();

        // snapshot anterior à V064: só razão social/CNPJ
        var e = DocumentoEnvioService.Emissor.fromSnapshot(
            "{\"razaoSocial\":\"EAMA Como Era LTDA\",\"cnpj\":\"22.222.222/0001-22\"}",
            emissora, eama, tenant, operadora, om);
        assertThat(e.remetenteTenantId()).isEqualTo(emissora);
        assertThat(e.nome()).isEqualTo("EAMA Como Era LTDA");               // snapshot manda
        assertThat(e.marinhaEmail()).isEqualTo("capitania-sp@example.com"); // EAMA atual, não a operadora
        assertThat(e.registro()).isEqualTo("EAMA-SP-999");
        assertThat(e.responsavel()).isEqualTo("Ana Souza");
        assertThat(e.emailOficial()).isEqualTo("oficial@eama.com");
        assertThat(e.contatoEmail()).isEqualTo("contato@eama.com");

        // snapshot ilegível e EAMA que já não existe: nada da operadora — destino vazio
        var vazio = DocumentoEnvioService.Emissor.fromSnapshot("{ilegível", emissora, null, tenant, operadora, om);
        assertThat(vazio.remetenteTenantId()).isEqualTo(emissora);
        assertThat(vazio.marinhaEmail()).isNull();
        assertThat(vazio.nome()).isNull();
        assertThat(vazio.emailOficial()).isNull();

        // emissão própria: o tenant assina e remete
        var proprio = DocumentoEnvioService.Emissor.fromSnapshot(null, null, null, tenant, operadora, om);
        assertThat(proprio.remetenteTenantId()).isEqualTo(tenant);
        assertThat(proprio.marinhaEmail()).isEqualTo("capitania-da-operadora@example.com");
        assertThat(proprio.contatoEmail()).isNull();
    }

    @Test
    @DisplayName("Reenvio pela operadora de documento delegado: ofício vai para a Capitania da EAMA e sai em nome dela")
    void reenvioDelegadoRemetePelaEama() {
        UUID emissora = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        when(docRepo.findById(docId)).thenReturn(Optional.of(DocumentoEmitido.builder()
            .id(docId).tenantId(tenant).reservaId(reservaId)
            .s3Key("t/reserva/r/documento.pdf").hashSha256("abc123hash")
            .emissorTenantId(emissora)
            // snapshot anterior à V064: sem marinhaEmail/responsável/e-mail oficial
            .emissorSnapshot("{\"razaoSocial\":\"EAMA Santos LTDA\",\"cnpj\":\"22.222.222/0001-22\"}")
            .build()));
        when(tenantQuery.findOutroTenantById(emissora)).thenReturn(Tenant.builder()
            .razaoSocial("EAMA Santos LTDA").eamaRegistro("EAMA-SP-999")
            .marinhaEmail("capitania-sp@example.com").responsavelNome("Ana Souza")
            .emailOficial("oficial@eamasantos.com.br").build());

        EmissaoService.ResultadoReenvio r = service.reenviarEmail(docId);

        assertThat(r.isEnviadoMarinha()).isTrue();
        org.mockito.ArgumentCaptor<String> body = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(email).sendEmailComAnexo(eq("capitania-sp@example.com"), anyString(), body.capture(),
            anyString(), any(), eq("application/pdf"), eq("oficial@eamasantos.com.br"),
            // cópia para a operadora (tenant do documento), pelo e-mail oficial dela
            eq(new EmailService.Remetente(emissora, "EAMA Santos LTDA", "eama@jetsave.com.br")));
        assertThat(body.getValue()).contains("Ana Souza").contains("EAMA-SP-999")
            // a operadora (tenant da sessão) aparece só na assinatura, como quem opera pela EAMA
            .contains("CNPJ: 22.222.222/0001-22<br>operado por <b>Jet Save Turismo Náutico LTDA</b>")
            .doesNotContain("O EAMA <b>Jet Save");
        // nunca a Capitania configurada pela operadora
        verify(email, org.mockito.Mockito.never()).sendEmailComAnexo(eq("capitania@example.com"),
            anyString(), anyString(), anyString(), any(), anyString(), nullable(String.class), any());
    }

    @Test
    @DisplayName("Reenvio delegado sem snapshot legível e sem EAMA: SEM_DESTINATARIO — nada sai pela operadora")
    void reenvioDelegadoSemEamaNaoCaiNaOperadora() {
        UUID docId = UUID.randomUUID();
        DocumentoEmitido doc = DocumentoEmitido.builder()
            .id(docId).tenantId(tenant).reservaId(reservaId)
            .s3Key("t/reserva/r/documento.pdf").hashSha256("abc123hash")
            .emissorTenantId(UUID.randomUUID()).emissorSnapshot("{ilegível")
            .build();
        when(docRepo.findById(docId)).thenReturn(Optional.of(doc));
        // tenantQuery.findOutroTenantById → null (mock): a EAMA já não existe

        EmissaoService.ResultadoReenvio r = service.reenviarEmail(docId);

        assertThat(r.isEnviadoMarinha()).isFalse();
        assertThat(doc.getMarinhaEnvioStatus()).isEqualTo(com.jetski.locacoes.domain.EnvioStatus.SEM_DESTINATARIO);
        verify(email, org.mockito.Mockito.never()).sendEmailComAnexo(eq("capitania@example.com"),
            anyString(), anyString(), anyString(), any(), anyString(), nullable(String.class), any());
        // a via do cliente continua saindo normalmente (em nome da operadora)
        verify(email).sendEmailComAnexo(eq("roberto@email.com"), anyString(), anyString(), anyString(),
            any(), anyString(), nullable(String.class), eq(new EmailService.Remetente(tenant, "Jet Save Turismo Náutico LTDA")));
    }
}
