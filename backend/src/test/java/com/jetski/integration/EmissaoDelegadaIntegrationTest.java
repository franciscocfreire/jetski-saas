package com.jetski.integration;

import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.domain.ReservaAceite;
import com.jetski.locacoes.domain.ReservaHabilitacao;
import com.jetski.locacoes.domain.Instrutor;
import com.jetski.locacoes.domain.VinculoEmissao;
import com.jetski.locacoes.domain.VinculoInstrutorOperadora;
import com.jetski.locacoes.internal.AceiteService;
import com.jetski.locacoes.internal.ClienteService;
import com.jetski.locacoes.internal.EmissaoDelegadaService;
import com.jetski.locacoes.internal.EmissaoService;
import com.jetski.locacoes.internal.HabilitacaoService;
import com.jetski.locacoes.internal.VinculoEmissaoService;
import com.jetski.shared.email.EmailService;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.ConflictException;
import com.jetski.shared.security.TenantContext;
import com.jetski.shared.security.UserProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Emissão delegada fim-a-fim (EMISSAO_DELEGADA_SPEC, V048): vínculo
 * operadora×EAMA (convite/aceite/termo/mesma capitania), estorno anti-fraude
 * do bônus, emissão com identidade/instrutor do emissor + snapshot + espelho,
 * kill switch e painel do emissor (contagens + reenvio).
 *
 * <p>Tenants ÚNICOS por execução: o ledger de créditos é append-only (trigger
 * proíbe DELETE), então cada teste nasce com empresas frescas em vez de limpar.
 */
@DisplayName("Emissão delegada (V048) — vínculo, estorno, emissão e painel")
class EmissaoDelegadaIntegrationTest extends AbstractIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("de1e0000-0000-0000-0000-0000000000ee");

    @Autowired private VinculoEmissaoService vinculoService;
    @Autowired private EmissaoDelegadaService delegadaService;
    @Autowired private EmissaoService emissaoService;
    @Autowired private ClienteService clienteService;
    @Autowired private HabilitacaoService habilitacaoService;
    @Autowired private AceiteService aceiteService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private org.springframework.cache.CacheManager cacheManager;
    @Autowired private com.jetski.shared.email.TenantSmtpResolver smtpResolver;
    @Autowired private com.jetski.locacoes.internal.InstrutorService instrutorService;
    @Autowired private com.jetski.tenant.internal.PlatformTenantService platformTenantService;
    @Autowired private com.jetski.tenant.internal.TenantConfigService tenantConfigService;
    @Autowired private com.jetski.tenant.PapelEmissaoService papelEmissaoService;

    @MockBean private UserProvisioningService userProvisioningService;
    @MockBean private EmailService emailService;

    private UUID operadora;
    private UUID emissora;
    private UUID modeloId;
    private UUID instrutorId;
    private String emissoraSlug;

    @BeforeEach
    void setUp() {
        operadora = UUID.randomUUID();
        emissora = UUID.randomUUID();
        modeloId = UUID.randomUUID();
        instrutorId = UUID.randomUUID();
        String sufixo = operadora.toString().substring(0, 8);
        emissoraSlug = "delegada-em-" + sufixo;

        TenantContext.setTenantId(operadora);
        TenantContext.setUsuarioId(USER_ID);

        UUID cpsp = jdbc.queryForObject("SELECT id FROM capitania WHERE codigo = 'CPSP'", UUID.class);

        jdbc.update("""
            INSERT INTO tenant (id, slug, razao_social, cnpj, cidade, uf, capitania_id)
            VALUES (?, ?, 'Operadora Praia LTDA', '11.111.111/0001-11', 'Santos', 'SP', ?)
            """, operadora, "delegada-op-" + sufixo, cpsp);
        jdbc.update("""
            INSERT INTO tenant (id, slug, razao_social, cnpj, cidade, uf, capitania_id,
                                emissora_habilitada, eama_registro, marinha_email, email_remetente)
            VALUES (?, ?, 'EAMA Santos LTDA', '22.222.222/0001-22', 'Santos', 'SP', ?,
                    true, 'EAMA-SP-999', 'capitania-sp@example.com', 'contato@eamasantos.com.br')
            """, emissora, emissoraSlug, cpsp);
        jdbc.update("UPDATE tenant SET assinatura_config = ?::jsonb WHERE id = ?",
            "{\"carimboTempo\":{\"ativo\":false}}", operadora);

        // plano da OPERADORA: só EMISSAO_DELEGADA (sem emissão própria)
        jdbc.update("INSERT INTO plano (nome, preco_mensal, modulos) "
            + "VALUES ('Delegada Teste', 99, '[\"EMISSAO_DELEGADA\"]'::jsonb) "
            + "ON CONFLICT (nome) DO UPDATE SET modulos = EXCLUDED.modulos");
        jdbc.update("INSERT INTO assinatura (tenant_id, plano_id, ciclo, dt_inicio, status) "
            + "SELECT ?, id, 'mensal', CURRENT_DATE, 'ativa' FROM plano WHERE nome = 'Delegada Teste'",
            operadora);
        var cache = cacheManager.getCache("plano-modulos");
        if (cache != null) {
            cache.clear();
        }

        // instrutor da EMISSORA (o único que pode assinar na delegada)
        jdbc.update("""
            INSERT INTO instrutor (id, tenant_id, nome, rg, orgao_emissor, cpf, cha, ativo)
            VALUES (?, ?, 'Instrutor da EAMA', '11.222.333-4', 'SSP/SP', '111.222.333-44', 'CHA-777', true)
            """, instrutorId, emissora);

        jdbc.update("""
            INSERT INTO usuario (id, email, nome, ativo)
            VALUES (?, 'operador.delegada@example.com', 'Operador Delegada', TRUE)
            ON CONFLICT (id) DO NOTHING
            """, USER_ID);
        jdbc.update("""
            INSERT INTO modelo (id, tenant_id, nome, fabricante, potencia_hp, capacidade_pessoas,
                                preco_base_hora, tolerancia_min, taxa_hora_extra, caucao,
                                inclui_combustivel, ativo)
            VALUES (?, ?, 'SeaDoo Spark', 'Sea-Doo', 90, 2, 120.00, 5, 40.00, 300.00, FALSE, TRUE)
            """, modeloId, operadora);

        when(userProvisioningService.provisionOrReuseCliente(
                any(), anyString(), anyString(), any(), anyString()))
            .thenReturn(new UserProvisioningService.ClienteProvisionResult("kc-sub-delegada", false));
    }

    private void seedCreditos(int bonus, int comprados) {
        int saldo = 0;
        if (bonus > 0) {
            saldo += bonus;
            jdbc.update("INSERT INTO credito_lancamento (tenant_id, tipo, quantidade, saldo_apos, motivo) "
                + "VALUES (?, 'ADESAO', ?, ?, 'seed delegada bonus')", operadora, bonus, saldo);
        }
        if (comprados > 0) {
            saldo += comprados;
            jdbc.update("INSERT INTO credito_lancamento (tenant_id, tipo, quantidade, saldo_apos, motivo) "
                + "VALUES (?, 'AJUSTE', ?, ?, 'seed delegada compra')", operadora, comprados, saldo);
        }
    }

    private int saldoOperadora() {
        Integer s = jdbc.queryForObject(
            "SELECT COALESCE(SUM(quantidade), 0) FROM credito_lancamento WHERE tenant_id = ?",
            Integer.class, operadora);
        return s != null ? s : 0;
    }

    /** Parceria ativa com o instrutor da EAMA designado (designação é obrigatória, §8.L). */
    private VinculoEmissao vinculoAtivo() {
        VinculoEmissao ativo = vinculoAtivoSemDesignacao();
        TenantContext.setTenantId(emissora);
        vinculoService.designarInstrutores(emissora, ativo.getId(), java.util.List.of(instrutorId));
        TenantContext.setTenantId(operadora);
        return ativo;
    }

    private VinculoEmissao vinculoAtivoSemDesignacao() {
        VinculoEmissao convite = vinculoService.convidar(
            operadora, emissoraSlug, VinculoEmissaoService.PapelConvite.OPERADORA);
        TenantContext.setTenantId(emissora);
        VinculoEmissao ativo = vinculoService.aceitar(emissora, convite.getId(), true);
        TenantContext.setTenantId(operadora);
        return ativo;
    }

    @Test
    @DisplayName("convite/aceite: bilateral, termo obrigatório; estorna SÓ o bônus da operadora (idempotente)")
    void vinculoEEstorno() {
        seedCreditos(5, 10); // 5 bônus + 10 comprados = saldo 15

        VinculoEmissao convite = vinculoService.convidar(
            operadora, emissoraSlug, VinculoEmissaoService.PapelConvite.OPERADORA);
        assertThat(convite.getStatus()).isEqualTo(VinculoEmissao.Status.CONVIDADO);

        // quem convidou não aceita; termo é obrigatório
        assertThatThrownBy(() -> vinculoService.aceitar(operadora, convite.getId(), true))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("convidada");
        TenantContext.setTenantId(emissora);
        assertThatThrownBy(() -> vinculoService.aceitar(emissora, convite.getId(), false))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("termo");

        VinculoEmissao ativo = vinculoService.aceitar(emissora, convite.getId(), true);
        assertThat(ativo.getStatus()).isEqualTo(VinculoEmissao.Status.ATIVO);
        assertThat(ativo.getTermoTexto()).contains("TERMO DE RESPONSABILIDADE");

        // estorno anti-fraude: bônus (5) zerado, comprados (10) preservados
        assertThat(saldoOperadora()).isEqualTo(10);
        Integer estornos = jdbc.queryForObject("SELECT count(*) FROM credito_lancamento "
            + "WHERE tenant_id = ? AND tipo = 'ESTORNO' AND referencia_id = ?",
            Integer.class, operadora, convite.getId());
        assertThat(estornos).isEqualTo(1);

        // re-aceite não duplica estorno
        assertThatThrownBy(() -> vinculoService.aceitar(emissora, convite.getId(), true))
            .isInstanceOf(ConflictException.class);
        assertThat(saldoOperadora()).isEqualTo(10);

        // segunda parceria viva para a mesma operadora é negada
        TenantContext.setTenantId(operadora);
        assertThatThrownBy(() -> vinculoService.convidar(
                operadora, emissoraSlug, VinculoEmissaoService.PapelConvite.OPERADORA))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("capitanias diferentes bloqueiam o convite (400 de negócio)")
    void capitaniaDiferenteNega() {
        UUID cprj = jdbc.queryForObject("SELECT id FROM capitania WHERE codigo = 'CPRJ'", UUID.class);
        jdbc.update("UPDATE tenant SET capitania_id = ? WHERE id = ?", cprj, operadora);
        assertThatThrownBy(() -> vinculoService.convidar(
                operadora, emissoraSlug, VinculoEmissaoService.PapelConvite.OPERADORA))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("MESMA capitania");
    }

    @Test
    @DisplayName("emissão delegada: identidade/instrutor da EAMA no snapshot, espelho no emissor, kill switch e painel")
    void emissaoDelegadaFimAFim() {
        seedCreditos(0, 20);
        vinculoAtivo();

        // instrutor do parceiro exposto com id+nome apenas
        var instrutores = vinculoService.instrutoresDoParceiro(operadora);
        assertThat(instrutores).anyMatch(r -> instrutorId.equals(r[0]));

        // cliente + reserva + habilitação (instrutor DA EMISSORA) + aceite
        Cliente cliente = clienteService.criarPreConta(Cliente.builder()
            .tenantId(operadora).nome("Maria Delegada").documento("123.456.789-09")
            .telefone("+5513988887777").rg("9.876.543-2").orgaoEmissor("SSP/SP")
            .nacionalidade("Brasileira").naturalidade("Santos/SP").build());
        UUID reservaId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO reserva (id, tenant_id, modelo_id, cliente_id, data_inicio, data_fim_prevista)
            VALUES (?, ?, ?, ?, now() + interval '1 day', now() + interval '1 day' + interval '2 hours')
            """, reservaId, operadora, modeloId, cliente.getId());
        habilitacaoService.registrar(reservaId, ReservaHabilitacao.builder()
            .via(ReservaHabilitacao.Via.EMA)
            .anexoSaude(true).anexoRegras(true).anexoResidencia(true).instrutorId(instrutorId)
            .gruNumero("GRU-DELEG-001").gruValor(new BigDecimal("23.13")).gruPago(true)
            .build());
        aceiteService.registrar(reservaId, ReservaAceite.Metodo.SIGNATURE_PAD,
            pngValido(), "127.0.0.1", "JUnit");

        // instrutor de fora da EAMA parceira é recusado na emissão
        jdbc.update("UPDATE reserva_habilitacao SET instrutor_id = ? WHERE reserva_id = ?",
            UUID.randomUUID(), reservaId);
        assertThatThrownBy(() -> emissaoService.emitir(reservaId))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("EAMA parceira");
        jdbc.update("UPDATE reserva_habilitacao SET instrutor_id = ? WHERE reserva_id = ?",
            instrutorId, reservaId);

        EmissaoService.ResultadoEmissao emissao = emissaoService.emitir(reservaId);
        assertThat(emissao.getDocumentoId()).isNotNull();

        // snapshot congelado: documento em nome da EAMA
        var doc = jdbc.queryForMap("SELECT emissor_tenant_id, emissor_snapshot::text AS snap, destinos::text AS dest "
            + "FROM documento_emitido WHERE id = ?", emissao.getDocumentoId());
        assertThat(doc.get("emissor_tenant_id")).isEqualTo(emissora);
        assertThat((String) doc.get("snap")).contains("EAMA Santos LTDA").contains("CHA-777");
        // destino Marinha = e-mail do EMISSOR (editável pela EAMA)
        assertThat((String) doc.get("dest")).contains("capitania-sp@example.com");

        // crédito debitado da OPERADORA
        assertThat(saldoOperadora()).isEqualTo(19);

        // espelho no tenant EMISSOR
        var espelho = jdbc.queryForMap("SELECT operadora_tenant_id, operadora_nome, condutor_nome, "
            + "instrutor_nome, gru_numero FROM emissao_delegada WHERE tenant_id = ? AND documento_id = ?",
            emissora, emissao.getDocumentoId());
        assertThat(espelho.get("operadora_tenant_id")).isEqualTo(operadora);
        assertThat(espelho.get("operadora_nome")).isEqualTo("Operadora Praia LTDA");
        assertThat(espelho.get("condutor_nome")).isEqualTo("Maria Delegada");
        assertThat(espelho.get("instrutor_nome")).isEqualTo("Instrutor da EAMA");
        assertThat(espelho.get("gru_numero")).isEqualTo("GRU-DELEG-001");

        // painel do emissor: lista + contagens + reenvio (sem novo débito)
        TenantContext.setTenantId(emissora);
        var linhas = delegadaService.listar(emissora, null, 50);
        assertThat(linhas).hasSize(1);
        var contagens = delegadaService.contagens(emissora);
        assertThat(contagens).hasSize(1);
        assertThat(((Number) contagens.get(0)[3]).intValue()).isEqualTo(1);
        var reenviada = delegadaService.reenviar(emissora, linhas.get(0).getId(), null);
        assertThat(reenviada.getReenviadoEm()).isNotNull();
        assertThat(reenviada.getReenviadoPara()).isEqualTo("capitania-sp@example.com");
        assertThat(saldoOperadora()).isEqualTo(19); // reenvio não debita
        // o ofício sai remetido pela EAMA (SMTP/"From" dela), não pelo "Meu Jet" genérico
        org.mockito.Mockito.verify(emailService).sendEmailComAnexo(
            org.mockito.ArgumentMatchers.eq("capitania-sp@example.com"), anyString(), anyString(),
            anyString(), any(), anyString(), org.mockito.ArgumentMatchers.nullable(String.class),
            org.mockito.ArgumentMatchers.eq(new EmailService.Remetente(emissora, "EAMA Santos LTDA")));

        // kill switch: bloqueia novas emissões, libera volta
        VinculoEmissao v = vinculoService.listar(emissora).get(0);
        vinculoService.bloquear(emissora, v.getId());
        TenantContext.setTenantId(operadora);
        UUID reserva2 = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO reserva (id, tenant_id, modelo_id, cliente_id, data_inicio, data_fim_prevista)
            VALUES (?, ?, ?, ?, now() + interval '2 day', now() + interval '2 day' + interval '2 hours')
            """, reserva2, operadora, modeloId, cliente.getId());
        habilitacaoService.registrar(reserva2, ReservaHabilitacao.builder()
            .via(ReservaHabilitacao.Via.EMA)
            .anexoSaude(true).anexoRegras(true).anexoResidencia(true).instrutorId(instrutorId)
            .gruNumero("GRU-DELEG-002").gruValor(new BigDecimal("23.13")).gruPago(true)
            .build());
        aceiteService.registrar(reserva2, ReservaAceite.Metodo.SIGNATURE_PAD,
            pngValido(), "127.0.0.1", "JUnit");
        assertThatThrownBy(() -> emissaoService.emitir(reserva2))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("suspensa pelo parceiro");

        TenantContext.setTenantId(emissora);
        vinculoService.liberar(emissora, v.getId());
        TenantContext.setTenantId(operadora);
        assertThat(emissaoService.emitir(reserva2).getDocumentoId()).isNotNull();
    }

    @Test
    @DisplayName("ofício delegado: destino, assinatura e REMETENTE sempre da EAMA — mesmo no reenvio pela operadora e com snapshot antigo")
    void oficioDelegadoSemprePelaEama() {
        seedCreditos(0, 20);
        vinculoAtivo();
        // A operadora tem Capitania, e-mail oficial e SMTP próprios: nada disso pode vazar no ofício.
        jdbc.update("UPDATE tenant SET marinha_email = 'capitania-da-operadora@example.com', "
            + "email_oficial = 'oficial@operadora.com', smtp_host = 'smtp.operadora', "
            + "smtp_username = 'op', smtp_password = 'x', smtp_from = 'contato@operadora.com' WHERE id = ?", operadora);
        // A EAMA como está HOJE (responsável/e-mail oficial/SMTP): é daqui que sai o que o snapshot não tem.
        jdbc.update("UPDATE tenant SET responsavel_nome = 'Ana Souza', email_oficial = 'oficial@eamasantos.com.br', "
            + "smtp_host = 'smtp.eama', smtp_username = 'eama', smtp_password = 'segredo', "
            + "smtp_from = 'oficios@eamasantos.com.br' WHERE id = ?", emissora);

        UUID docId = emitirDelegada("Carlos Delegado", "111.444.777-35", "GRU-DELEG-010", "3 day");

        // Snapshot anterior à V064 (só identidade): sem marinhaEmail, responsável ou e-mail oficial.
        jdbc.update("UPDATE documento_emitido SET emissor_snapshot = "
            + "'{\"razaoSocial\":\"EAMA Santos LTDA\",\"cnpj\":\"22.222.222/0001-22\"}'::jsonb WHERE id = ?", docId);
        org.mockito.Mockito.clearInvocations(emailService);

        // Reenvio disparado pela OPERADORA (contexto dela): o ofício continua 100% da EAMA.
        assertThat(TenantContext.getTenantId()).isEqualTo(operadora);
        EmissaoService.ResultadoReenvio r = emissaoService.reenviarEmail(docId);
        assertThat(r.isEnviadoMarinha()).isTrue();

        org.mockito.ArgumentCaptor<String> body = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(emailService).sendEmailComAnexo(
            org.mockito.ArgumentMatchers.eq("capitania-sp@example.com"), anyString(), body.capture(),
            anyString(), any(), anyString(),
            org.mockito.ArgumentMatchers.eq("oficial@eamasantos.com.br"),
            // cópia (Cc) para a operadora: o e-mail oficial dela
            org.mockito.ArgumentMatchers.eq(new EmailService.Remetente(emissora, "EAMA Santos LTDA", "oficial@operadora.com")));
        assertThat(body.getValue())
            .contains("Ana Souza").contains("EAMA-SP-999").contains("EAMA Santos LTDA")
            // a operadora só na assinatura, como quem opera pela EAMA; contatos dela não entram
            .contains("operado por <b>Operadora Praia LTDA</b>")
            .doesNotContain("O EAMA <b>Operadora").doesNotContain("operadora.com");
        org.mockito.Mockito.verify(emailService, org.mockito.Mockito.never()).sendEmailComAnexo(
            org.mockito.ArgumentMatchers.eq("capitania-da-operadora@example.com"), anyString(), anyString(),
            anyString(), any(), anyString(), org.mockito.ArgumentMatchers.nullable(String.class), any());

        // SMTP: a partir do contexto da operadora, o resolver entrega o servidor da EAMA...
        var smtpEama = smtpResolver.forTenant(emissora).orElseThrow();
        assertThat(smtpEama.host()).isEqualTo("smtp.eama");
        assertThat(smtpEama.from()).isEqualTo("oficios@eamasantos.com.br");
        assertThat(smtpEama.fromName()).isEqualTo("EAMA Santos LTDA");
        // ...sem contaminar o contexto do chamador (o da sessão continua sendo o da operadora).
        assertThat(TenantContext.getTenantId()).isEqualTo(operadora);
        assertThat(smtpResolver.forCurrentTenant().orElseThrow().host()).isEqualTo("smtp.operadora");
    }

    /** Cadastro → habilitação EMA com instrutor da EAMA → aceite → emissão delegada; devolve o documento. */
    private UUID emitirDelegada(String nome, String cpf, String gru, String offset) {
        Cliente cliente = clienteService.criarPreConta(Cliente.builder()
            .tenantId(operadora).nome(nome).documento(cpf)
            .telefone("+5513988887777").rg("9.876.543-2").orgaoEmissor("SSP/SP")
            .nacionalidade("Brasileira").naturalidade("Santos/SP").build());
        UUID reservaId = UUID.randomUUID();
        jdbc.update("INSERT INTO reserva (id, tenant_id, modelo_id, cliente_id, data_inicio, data_fim_prevista) "
            + "VALUES (?, ?, ?, ?, now() + interval '" + offset + "', now() + interval '" + offset + "' + interval '2 hours')",
            reservaId, operadora, modeloId, cliente.getId());
        habilitacaoService.registrar(reservaId, ReservaHabilitacao.builder()
            .via(ReservaHabilitacao.Via.EMA)
            .anexoSaude(true).anexoRegras(true).anexoResidencia(true).instrutorId(instrutorId)
            .gruNumero(gru).gruValor(new BigDecimal("23.13")).gruPago(true)
            .build());
        aceiteService.registrar(reservaId, ReservaAceite.Metodo.SIGNATURE_PAD,
            pngValido(), "127.0.0.1", "JUnit");
        return emissaoService.emitir(reservaId).getDocumentoId();
    }

    @Test
    @DisplayName("designação obrigatória (§8.L): operadora só vê/usa instrutores designados; vazio = nenhum da EAMA; só a EAMA designa")
    void designacaoDeInstrutores() {
        seedCreditos(0, 10);
        VinculoEmissao v = vinculoAtivoSemDesignacao();

        // segundo instrutor da EAMA
        UUID instrutor2 = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO instrutor (id, tenant_id, nome, cpf, cha, ativo)
            VALUES (?, ?, 'Instrutor Dois', '222.333.444-55', 'CHA-888', true)
            """, instrutor2, emissora);

        // sem designação → operadora não vê nenhum instrutor da EAMA
        assertThat(vinculoService.instrutoresDoParceiro(operadora)).isEmpty();

        // operadora NÃO designa
        assertThatThrownBy(() -> vinculoService.designarInstrutores(
                operadora, v.getId(), java.util.List.of(instrutorId)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("EAMA emissora");

        // EAMA designa só o instrutor 1 → operadora passa a ver só ele
        TenantContext.setTenantId(emissora);
        var designados = vinculoService.designarInstrutores(
            emissora, v.getId(), java.util.List.of(instrutorId));
        assertThat(designados).hasSize(1);
        // salvar a MESMA designação de novo não pode violar o unique (regressão: 500 no dev)
        assertThat(vinculoService.designarInstrutores(emissora, v.getId(), java.util.List.of(instrutorId)))
            .hasSize(1);
        // trocar o conjunto mantém o que ficou e remove o que saiu
        assertThat(vinculoService.designarInstrutores(emissora, v.getId(), java.util.List.of(instrutorId, instrutor2)))
            .hasSize(2);
        assertThat(vinculoService.designarInstrutores(emissora, v.getId(), java.util.List.of(instrutorId)))
            .hasSize(1);
        // instrutor de outro tenant/inativo é recusado na designação
        assertThatThrownBy(() -> vinculoService.designarInstrutores(
                emissora, v.getId(), java.util.List.of(UUID.randomUUID())))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Instrutor inválido");

        TenantContext.setTenantId(operadora);
        var visiveis = vinculoService.instrutoresDoParceiro(operadora);
        assertThat(visiveis).hasSize(1);
        assertThat(visiveis.get(0)[0]).isEqualTo(instrutorId);

        // emissão com instrutor NÃO designado é bloqueada (mesmo sendo da EAMA)
        // CPF exclusivo desta classe: criarPreConta busca por documento SEM
        // escopo de tenant nos testes (superuser bypassa RLS) — CPF repetido
        // em outra classe vira falha dependente de ordem.
        Cliente cliente = clienteService.criarPreConta(Cliente.builder()
            .tenantId(operadora).nome("Cliente Designacao").documento("242.821.368-71").build());
        UUID reservaId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO reserva (id, tenant_id, modelo_id, cliente_id, data_inicio, data_fim_prevista)
            VALUES (?, ?, ?, ?, now() + interval '1 day', now() + interval '1 day' + interval '2 hours')
            """, reservaId, operadora, modeloId, cliente.getId());
        habilitacaoService.registrar(reservaId, ReservaHabilitacao.builder()
            .via(ReservaHabilitacao.Via.EMA)
            .anexoSaude(true).anexoRegras(true).anexoResidencia(true).instrutorId(instrutor2)
            .gruNumero("GRU-DELEG-004").gruValor(new BigDecimal("23.13")).gruPago(true)
            .build());
        aceiteService.registrar(reservaId, ReservaAceite.Metodo.SIGNATURE_PAD,
            pngValido(), "127.0.0.1", "JUnit");
        assertThatThrownBy(() -> emissaoService.emitir(reservaId))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("designado");

        // com o designado, emite normalmente
        jdbc.update("UPDATE reserva_habilitacao SET instrutor_id = ? WHERE reserva_id = ?",
            instrutorId, reservaId);
        assertThat(emissaoService.emitir(reservaId).getDocumentoId()).isNotNull();

        // designação vazia = nenhum instrutor da EAMA (não volta a "todos")
        TenantContext.setTenantId(emissora);
        assertThat(vinculoService.designarInstrutores(emissora, v.getId(), java.util.List.of()))
            .isEmpty();
        TenantContext.setTenantId(operadora);
        assertThat(vinculoService.instrutoresDoParceiro(operadora)).isEmpty();
    }

    @Test
    @DisplayName("operadora sem vínculo ativo não emite via EMA (mensagem de parceria)")
    void semVinculoNega() {
        seedCreditos(0, 5);
        // CPF exclusivo desta classe (ver comentário no teste de designação)
        Cliente cliente = clienteService.criarPreConta(Cliente.builder()
            .tenantId(operadora).nome("Sem Parceria").documento("075.201.033-66").build());
        UUID reservaId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO reserva (id, tenant_id, modelo_id, cliente_id, data_inicio, data_fim_prevista)
            VALUES (?, ?, ?, ?, now() + interval '1 day', now() + interval '1 day' + interval '2 hours')
            """, reservaId, operadora, modeloId, cliente.getId());
        habilitacaoService.registrar(reservaId, ReservaHabilitacao.builder()
            .via(ReservaHabilitacao.Via.EMA)
            .gruNumero("GRU-DELEG-003").gruValor(new BigDecimal("23.13")).gruPago(true)
            .build());
        aceiteService.registrar(reservaId, ReservaAceite.Metodo.SIGNATURE_PAD,
            pngValido(), "127.0.0.1", "JUnit");
        assertThatThrownBy(() -> emissaoService.emitir(reservaId))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("parceria");
    }

    @Test
    @DisplayName("portão duplo (V050): emissão PRÓPRIA sem emissora_habilitada nega com 400 de negócio")
    void propriaSemHabilitacaoNega() {
        // tenant com todos os módulos (sem assinatura = '*') mas SEM habilitação cadastral
        UUID propria = UUID.randomUUID();
        UUID modeloProprio = UUID.randomUUID();
        jdbc.update("INSERT INTO tenant (id, slug, razao_social) VALUES (?, ?, 'Propria Sem Habilitacao')",
            propria, "propria-" + propria.toString().substring(0, 8));
        jdbc.update("""
            INSERT INTO modelo (id, tenant_id, nome, fabricante, potencia_hp, capacidade_pessoas,
                                preco_base_hora, tolerancia_min, taxa_hora_extra, caucao,
                                inclui_combustivel, ativo)
            VALUES (?, ?, 'SeaDoo GTI', 'Sea-Doo', 130, 2, 150.00, 5, 50.00, 300.00, FALSE, TRUE)
            """, modeloProprio, propria);
        jdbc.update("INSERT INTO credito_lancamento (tenant_id, tipo, quantidade, saldo_apos, motivo) "
            + "VALUES (?, 'AJUSTE', 5, 5, 'seed portao duplo')", propria);
        TenantContext.setTenantId(propria);

        Cliente cliente = clienteService.criarPreConta(Cliente.builder()
            .tenantId(propria).nome("Cliente Propria").documento("935.411.347-80").build());
        UUID reservaId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO reserva (id, tenant_id, modelo_id, cliente_id, data_inicio, data_fim_prevista)
            VALUES (?, ?, ?, ?, now() + interval '1 day', now() + interval '1 day' + interval '2 hours')
            """, reservaId, propria, modeloProprio, cliente.getId());
        habilitacaoService.registrar(reservaId, ReservaHabilitacao.builder()
            .via(ReservaHabilitacao.Via.EMA)
            .gruNumero("GRU-PROPRIA-1").gruValor(new BigDecimal("23.13")).gruPago(true)
            .build());
        aceiteService.registrar(reservaId, ReservaAceite.Metodo.SIGNATURE_PAD,
            pngValido(), "127.0.0.1", "JUnit");

        assertThatThrownBy(() -> emissaoService.emitir(reservaId))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("habilitada como EAMA emissora");

        // habilitando (como o superadmin faria), a mesma emissão passa
        jdbc.update("UPDATE tenant SET emissora_habilitada = true WHERE id = ?", propria);
        assertThat(emissaoService.emitir(reservaId).getDocumentoId()).isNotNull();
    }

    // ==================== papel exclusivo (§8.M) ====================

    @Test
    @DisplayName("papel exclusivo (§8.M): operadora com TODOS os módulos (Trial) e parceria em vigor emite pela EAMA")
    void operadoraComTodosOsModulosEmitePelaEama() {
        seedCreditos(0, 10);
        operadoraComTodosOsModulos();
        assertThat(vinculoService.modoEmissao(operadora).modo())
            .isEqualTo(VinculoEmissaoService.ModoEmissao.PROPRIA);

        vinculoAtivo();
        var modo = vinculoService.modoEmissao(operadora);
        assertThat(modo.modo()).isEqualTo(VinculoEmissaoService.ModoEmissao.DELEGADA);
        assertThat(modo.emissoraNome()).isEqualTo("EAMA Santos LTDA");
        assertThat(modo.planoPermitePropria()).isTrue();

        // Antes caía na emissão própria e batia no portão V050 ("não está habilitada").
        UUID reservaId = reservaPronta("Trial Delegada", "604.378.215-44", "GRU-DELEG-020", "4 day", instrutorId);
        EmissaoService.ResultadoEmissao emissao = emissaoService.emitir(reservaId);
        assertThat(jdbc.queryForObject("SELECT emissor_tenant_id FROM documento_emitido WHERE id = ?",
            UUID.class, emissao.getDocumentoId())).isEqualTo(emissora);
    }

    @Test
    @DisplayName("papel exclusivo (§8.M): aceite derruba a habilitação da operadora; ninguém é operadora e emissora; superadmin só re-habilita após revogar")
    void aceiteDerrubaHabilitacaoEPapelExclusivo() {
        jdbc.update("UPDATE tenant SET emissora_habilitada = true, eama_registro = 'EAMA-OP-1' WHERE id = ?", operadora);
        VinculoEmissao v = vinculoAtivo();
        assertThat(jdbc.queryForObject("SELECT emissora_habilitada FROM tenant WHERE id = ?",
            Boolean.class, operadora)).isFalse();

        UUID cpsp = jdbc.queryForObject("SELECT id FROM capitania WHERE codigo = 'CPSP'", UUID.class);
        String sufixo = operadora.toString().substring(0, 8);
        UUID terceira = UUID.randomUUID();
        jdbc.update("INSERT INTO tenant (id, slug, razao_social, capitania_id) VALUES (?, ?, 'Terceira Praia LTDA', ?)",
            terceira, "delegada-3a-" + sufixo, cpsp);
        String outraEamaSlug = "delegada-em2-" + sufixo;
        jdbc.update("INSERT INTO tenant (id, slug, razao_social, capitania_id, emissora_habilitada, eama_registro) "
            + "VALUES (?, ?, 'EAMA Guaruja LTDA', ?, true, 'EAMA-SP-777')", UUID.randomUUID(), outraEamaSlug, cpsp);

        // A operadora delegada não emite para terceiros — nem com a flag religada à mão.
        jdbc.update("UPDATE tenant SET emissora_habilitada = true WHERE id = ?", operadora);
        TenantContext.setTenantId(terceira);
        assertThatThrownBy(() -> vinculoService.convidar(
                terceira, "delegada-op-" + sufixo, VinculoEmissaoService.PapelConvite.OPERADORA))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("operadora (delegada)");
        jdbc.update("UPDATE tenant SET emissora_habilitada = false WHERE id = ?", operadora);

        // A EAMA de uma parceria viva não vira operadora de outra EAMA.
        TenantContext.setTenantId(emissora);
        assertThatThrownBy(() -> vinculoService.convidar(
                emissora, outraEamaSlug, VinculoEmissaoService.PapelConvite.OPERADORA))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("EAMA emissora de outra");

        // O superadmin não habilita como emissora quem é operadora em vigor...
        TenantContext.setTenantId(operadora);
        assertThatThrownBy(() -> platformTenantService.habilitarEmissora(operadora))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("emissora OU delegada");

        // ...e, revogada a parceria, volta a poder (nova validação).
        vinculoService.revogar(operadora, v.getId());
        assertThat(platformTenantService.habilitarEmissora(operadora).emissoraHabilitada()).isTrue();
    }

    @Test
    @DisplayName("terceiro estado (§8.M): empresa sem capitania vira delegada, herda a capitania da EAMA e não pode trocá-la")
    void delegadaHerdaCapitaniaDaEamaETrava() {
        UUID cpsp = jdbc.queryForObject("SELECT id FROM capitania WHERE codigo = 'CPSP'", UUID.class);
        UUID cprj = jdbc.queryForObject("SELECT id FROM capitania WHERE codigo = 'CPRJ'", UUID.class);
        jdbc.update("UPDATE tenant SET capitania_id = NULL WHERE id = ?", operadora);
        var nenhum = com.jetski.tenant.PapelEmissaoService.Papel.NENHUM;
        assertThat(papelEmissaoService.papelDe(operadora, false).papel()).isEqualTo(nenhum);
        assertThat(papelEmissaoService.papelDe(emissora, true).papel())
            .isEqualTo(com.jetski.tenant.PapelEmissaoService.Papel.EMISSORA);

        // Sem capitania, entra na parceria e herda a da EAMA.
        VinculoEmissao v = vinculoAtivo();
        assertThat(jdbc.queryForObject("SELECT capitania_id FROM tenant WHERE id = ?", UUID.class, operadora))
            .isEqualTo(cpsp);
        var papel = papelEmissaoService.papelDe(operadora, false);
        assertThat(papel.papel()).isEqualTo(com.jetski.tenant.PapelEmissaoService.Papel.DELEGADA);
        assertThat(papel.emissoraTenantId()).isEqualTo(emissora);
        assertThat(papel.vinculoStatus()).isEqualTo("ATIVO");

        // Capitania travada enquanto delegada; salvar a mesma continua valendo.
        assertThatThrownBy(() -> tenantConfigService.updateEmissoraConfig(operadora,
                new com.jetski.tenant.api.dto.EmissoraConfigRequest(cprj, null, null)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("EAMA emissora");
        tenantConfigService.updateEmissoraConfig(operadora,
            new com.jetski.tenant.api.dto.EmissoraConfigRequest(cpsp, null, null));

        // Revogada a parceria, volta a "nenhum" e a capitania destrava.
        vinculoService.revogar(operadora, v.getId());
        assertThat(papelEmissaoService.papelDe(operadora, false).papel()).isEqualTo(nenhum);
        tenantConfigService.updateEmissoraConfig(operadora,
            new com.jetski.tenant.api.dto.EmissoraConfigRequest(cprj, null, null));
        assertThat(jdbc.queryForObject("SELECT capitania_id FROM tenant WHERE id = ?", UUID.class, operadora))
            .isEqualTo(cprj);
    }

    @Test
    @DisplayName("portão comercial: operadora sem o módulo de emissão delegada no plano não entra em parceria")
    void operadoraSemModuloDelegadaNaoEntraEmParceria() {
        jdbc.update("INSERT INTO plano (nome, preco_mensal, modulos) "
            + "VALUES ('Propria Teste', 99, '[\"EMISSAO_PROPRIA\"]'::jsonb) "
            + "ON CONFLICT (nome) DO UPDATE SET modulos = EXCLUDED.modulos");
        jdbc.update("UPDATE assinatura SET plano_id = (SELECT id FROM plano WHERE nome = 'Propria Teste') "
            + "WHERE tenant_id = ?", operadora);
        limparCachePlano();
        assertThatThrownBy(() -> vinculoService.convidar(
                operadora, emissoraSlug, VinculoEmissaoService.PapelConvite.OPERADORA))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Emissão à Marinha — delegada");
    }

    // ==================== instrutores da operadora (V070) ====================

    @Test
    @DisplayName("instrutor da operadora (V070): só assina a delegada depois de aprovado pela EAMA; alteração volta a pendente; EAMA rejeita e remove")
    void instrutorDaOperadoraExigeAprovacaoDaEama() {
        seedCreditos(0, 10);
        VinculoEmissao v = vinculoAtivo();
        var aprovar = VinculoEmissaoService.DecisaoInstrutor.APROVAR;

        // Cadastro na operadora com parceria em vigor: o pedido à EAMA sai junto.
        Instrutor proprio = instrutorService.criar(Instrutor.builder()
            .nome("Instrutor da Operadora").rg("33.444.555-6").orgaoEmissor("SSP/SP")
            .cpf("718.253.964-00").cha("CHA-OP-1").build(), null);
        assertThat(vinculoService.listarInstrutoresOperadora(operadora, v.getId()))
            .singleElement()
            .satisfies(p -> {
                assertThat(p.instrutorId()).isEqualTo(proprio.getId());
                assertThat(p.status()).isEqualTo("PENDENTE");
                assertThat(p.cha()).isEqualTo("CHA-OP-1");
            });

        // Pendente: fora da lista do balcão e recusado na emissão.
        assertThat(vinculoService.instrutoresDoParceiro(operadora)).noneMatch(r -> proprio.getId().equals(r[0]));
        UUID reservaId = reservaPronta("Cliente Instrutor Proprio", "830.157.492-50", "GRU-DELEG-030", "5 day",
            proprio.getId());
        assertThatThrownBy(() -> emissaoService.emitir(reservaId))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("aprovado");

        // Só a EAMA decide.
        assertThatThrownBy(() -> vinculoService.decidirInstrutor(operadora, v.getId(), proprio.getId(), aprovar, null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Somente a EAMA");
        TenantContext.setTenantId(emissora);
        assertThat(vinculoService.decidirInstrutor(emissora, v.getId(), proprio.getId(), aprovar, null).getStatus())
            .isEqualTo(VinculoInstrutorOperadora.Status.APROVADO);

        // Aprovado: disponível com origem OPERADORA e assina a emissão em nome da EAMA.
        TenantContext.setTenantId(operadora);
        assertThat(vinculoService.instrutoresDoParceiro(operadora))
            .anyMatch(r -> proprio.getId().equals(r[0]) && "OPERADORA".equals(r[2]));
        EmissaoService.ResultadoEmissao emissao = emissaoService.emitir(reservaId);
        assertThat(jdbc.queryForObject("SELECT emissor_snapshot::text FROM documento_emitido WHERE id = ?",
            String.class, emissao.getDocumentoId())).contains("EAMA Santos LTDA").contains("CHA-OP-1");
        assertThat(jdbc.queryForObject("SELECT instrutor_nome FROM emissao_delegada WHERE tenant_id = ? AND documento_id = ?",
            String.class, emissora, emissao.getDocumentoId())).isEqualTo("Instrutor da Operadora");

        // Alterar os dados devolve o pedido à EAMA: sai da lista até nova aprovação.
        instrutorService.atualizar(proprio.getId(), Instrutor.builder().cha("CHA-OP-2").build(), null);
        assertThat(statusDoPedido(v, proprio.getId())).isEqualTo("PENDENTE");
        assertThat(vinculoService.instrutoresDoParceiro(operadora)).noneMatch(r -> proprio.getId().equals(r[0]));

        // Rejeição guarda o motivo; a operadora pede de novo; aprovado e depois removido.
        TenantContext.setTenantId(emissora);
        vinculoService.decidirInstrutor(emissora, v.getId(), proprio.getId(),
            VinculoEmissaoService.DecisaoInstrutor.REJEITAR, "CHA vencida");
        TenantContext.setTenantId(operadora);
        assertThat(vinculoService.listarInstrutoresOperadora(operadora, v.getId()).get(0).motivo())
            .isEqualTo("CHA vencida");
        assertThat(vinculoService.solicitarAprovacaoInstrutor(operadora, proprio.getId()).getStatus())
            .isEqualTo(VinculoInstrutorOperadora.Status.PENDENTE);
        TenantContext.setTenantId(emissora);
        vinculoService.decidirInstrutor(emissora, v.getId(), proprio.getId(), aprovar, null);
        assertThatThrownBy(() -> vinculoService.decidirInstrutor(emissora, v.getId(), proprio.getId(),
                VinculoEmissaoService.DecisaoInstrutor.REJEITAR, null))
            .isInstanceOf(ConflictException.class);
        vinculoService.decidirInstrutor(emissora, v.getId(), proprio.getId(),
            VinculoEmissaoService.DecisaoInstrutor.REMOVER, "desligado");
        TenantContext.setTenantId(operadora);
        assertThat(statusDoPedido(v, proprio.getId())).isEqualTo("REMOVIDO");
        assertThat(vinculoService.instrutoresDoParceiro(operadora)).noneMatch(r -> proprio.getId().equals(r[0]));
    }

    /** Operadora sem assinatura = todos os módulos, como a Trial (plano com modulos NULL). */
    private void operadoraComTodosOsModulos() {
        jdbc.update("DELETE FROM assinatura WHERE tenant_id = ?", operadora);
        limparCachePlano();
    }

    private void limparCachePlano() {
        var cache = cacheManager.getCache("plano-modulos");
        if (cache != null) {
            cache.clear();
        }
    }

    private String statusDoPedido(VinculoEmissao v, UUID instrutor) {
        return jdbc.queryForObject("SELECT status FROM vinculo_instrutor_operadora "
            + "WHERE vinculo_id = ? AND instrutor_id = ?", String.class, v.getId(), instrutor);
    }

    /** Cadastro → habilitação EMA com o instrutor informado → aceite; devolve a reserva pronta para emitir. */
    private UUID reservaPronta(String nome, String cpf, String gru, String offset, UUID instrutor) {
        Cliente cliente = clienteService.criarPreConta(Cliente.builder()
            .tenantId(operadora).nome(nome).documento(cpf)
            .telefone("+5513988887777").rg("9.876.543-2").orgaoEmissor("SSP/SP")
            .nacionalidade("Brasileira").naturalidade("Santos/SP").build());
        UUID reservaId = UUID.randomUUID();
        jdbc.update("INSERT INTO reserva (id, tenant_id, modelo_id, cliente_id, data_inicio, data_fim_prevista) "
            + "VALUES (?, ?, ?, ?, now() + interval '" + offset + "', now() + interval '" + offset + "' + interval '2 hours')",
            reservaId, operadora, modeloId, cliente.getId());
        habilitacaoService.registrar(reservaId, ReservaHabilitacao.builder()
            .via(ReservaHabilitacao.Via.EMA)
            .anexoSaude(true).anexoRegras(true).anexoResidencia(true).instrutorId(instrutor)
            .gruNumero(gru).gruValor(new BigDecimal("23.13")).gruPago(true)
            .build());
        aceiteService.registrar(reservaId, ReservaAceite.Metodo.SIGNATURE_PAD,
            pngValido(), "127.0.0.1", "JUnit");
        return reservaId;
    }

    /** PNG 1x1 válido (o OpenPDF precisa parsear o header da imagem). */
    private static byte[] pngValido() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
