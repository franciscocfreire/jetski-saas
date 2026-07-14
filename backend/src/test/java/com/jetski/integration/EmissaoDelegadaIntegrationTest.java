package com.jetski.integration;

import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.domain.ReservaAceite;
import com.jetski.locacoes.domain.ReservaHabilitacao;
import com.jetski.locacoes.domain.VinculoEmissao;
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

    private VinculoEmissao vinculoAtivo() {
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
    @DisplayName("designação (V049): operadora só vê/usa instrutores designados; vazio = todos; só a EAMA designa")
    void designacaoDeInstrutores() {
        seedCreditos(0, 10);
        VinculoEmissao v = vinculoAtivo();

        // segundo instrutor da EAMA
        UUID instrutor2 = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO instrutor (id, tenant_id, nome, cpf, cha, ativo)
            VALUES (?, ?, 'Instrutor Dois', '222.333.444-55', 'CHA-888', true)
            """, instrutor2, emissora);

        // sem designação → operadora vê os dois
        assertThat(vinculoService.instrutoresDoParceiro(operadora)).hasSize(2);

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
        Cliente cliente = clienteService.criarPreConta(Cliente.builder()
            .tenantId(operadora).nome("Cliente Designacao").documento("111.444.777-35").build());
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

        // designação vazia volta ao padrão "todos"
        TenantContext.setTenantId(emissora);
        assertThat(vinculoService.designarInstrutores(emissora, v.getId(), java.util.List.of()))
            .isEmpty();
        TenantContext.setTenantId(operadora);
        assertThat(vinculoService.instrutoresDoParceiro(operadora)).hasSize(2);
    }

    @Test
    @DisplayName("operadora sem vínculo ativo não emite via EMA (mensagem de parceria)")
    void semVinculoNega() {
        seedCreditos(0, 5);
        Cliente cliente = clienteService.criarPreConta(Cliente.builder()
            .tenantId(operadora).nome("Sem Parceria").documento("390.533.447-05").build());
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
