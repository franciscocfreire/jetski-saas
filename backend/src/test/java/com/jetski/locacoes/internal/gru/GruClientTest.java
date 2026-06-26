package com.jetski.locacoes.internal.gru;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Valida o fluxo de 7 passos do {@link GruClient} contra um servidor HTTP local
 * que reproduz as respostas capturadas (HAR) da Marinha + PagTesouro.
 * Nunca toca no site real.
 */
class GruClientTest {

    private HttpServer server;
    private String base;
    private final List<String> hits = new CopyOnWriteArrayList<>();

    private static final String ID_SESSAO = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    private static final String PIX_EMV =
        "00020101021226930014br.gov.bcb.pix2571TESOURO520400005303986540660.32";

    private static final GruContribuinte CONTRIB = new GruContribuinte(
        "38248971805", "GARDENIA M L D S", "11999999999", "x@y.com", "F",
        "11095460", "Rua Teste", "100", "Apto 1", "Centro", "Santos", "SP");

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            hits.add(path);
            switch (path) {
                case "/scam/emitgruscam/solicitar_servico.asp" -> {
                    exchange.getResponseHeaders().add("Set-Cookie", "ASPSESSIONIDABC=XYZ; path=/");
                    send(exchange, 200, "<html>form</html>");
                }
                case "/scam/emitgruscam/pagtesouro.asp" -> {
                    exchange.getResponseHeaders().add("Location",
                        "pagtesouro_form.asp?id_gru=7976123&cpf_cnpj=38248971805"
                        + "&svc=060;288%20%20;408&nome=GARDENIA%20M%20L%20D%20S&qtd=");
                    send(exchange, 302, "");
                }
                case "/scam/emitgruscam/pagtesouro_form.asp" -> send(exchange, 200, """
                    <form name="frm_envio" action="/pagtesouro/index.php" method="post">
                      <input type="hidden" name="req" value="dpc1.marinha.mil.br">
                      <input type="hidden" name="token" value="141600814172120">
                      <input type="hidden" name="id_gru" value="7976123">
                    </form>""");
                case "/pagtesouro/index.php" -> send(exchange, 200,
                    "<html>redirect to /#/pagamento?idSessao=" + ID_SESSAO + "</html>");
                case "/api/pagamentos/dados-pagamento" -> send(exchange, 200, """
                    {"contribuinte":{"tipoIdentificador":"CPF"},
                     "descricao":"13508 - CHA EXAME/RENOV/2A VIA",
                     "valor":60.32,"referencia":"84512778",
                     "numeroReferencia":"60893100225672026"}""");
                case "/api/pagamentos/meios-pagamento/pix" -> send(exchange, 200,
                    "{\"conteudo\":\"" + PIX_EMV + "\",\"imagem\":\"QRBASE64\","
                    + "\"dataExpiracao\":\"24/06/2026 20:10\"}");
                case "/scam/emitgruscam/atualiza_gru.asp" -> {
                    exchange.getResponseHeaders().add("Location",
                        "imprime_gru.asp?v_id_gru=7977050&v_cd_recolhimento=060");
                    send(exchange, 302, "");
                }
                case "/scam/emitgruscam/imprime_gru.asp" -> {
                    exchange.getResponseHeaders().add("Location",
                        "/scam/emitgruscam/gru/tmp/4017977050.pdf");
                    send(exchange, 302, "");
                }
                case "/scam/emitgruscam/gru/tmp/4017977050.pdf" ->
                    send(exchange, 200, "%PDF-1.4\n" + "x".repeat(1100));
                case "/api/pagamentos/pix-stn/sonda" -> {
                    String q = exchange.getRequestURI().getQuery();
                    if (q != null && q.contains("pago")) {
                        send(exchange, 200, """
                            {"idPagamento":"75sG","numeroReferencia":"80893100021762026",
                             "descricao":"CHA","valor":8,"refTran":"E182",
                             "tipoPagamentoEscolhido":"PIX",
                             "contribuinte":{"nome":"THALIA","codigoIdentificador":"23472084898"},
                             "situacao":{"codigo":"CONCLUIDO","data":"2026-06-26T07:04:31Z"}}""");
                    } else if (q != null && q.contains("expirado")) {
                        send(exchange, 200, "[{\"codigo\":\"C0026\",\"descricao\":\"Sessão expirada.\"}]");
                    } else {
                        send(exchange, 200, "[{\"codigo\":\"C0008\",\"descricao\":\"Erro desconhecido.\"}]");
                    }
                }
                default -> {
                    if (path.startsWith("/scam/emitgruscam/obj")) {
                        send(exchange, 200, "ok");
                    } else {
                        send(exchange, 404, "nope");
                    }
                }
            }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private GruClient client() {
        return new GruClient(new ObjectMapper(), base, base, 5, true,
            "89310", "1", "060", "060;288  ;408");
    }

    @Test
    void geraGruEPixComSucesso() {
        GruResultado r = client().gerar(CONTRIB);

        assertThat(r.gruNumero()).isEqualTo("60893100225672026");
        assertThat(r.gruValor()).isEqualByComparingTo("60.32");
        assertThat(r.descricao()).contains("CHA");
        assertThat(r.pixCopiaECola()).isEqualTo(PIX_EMV);
        assertThat(r.pixQrPngBase64()).isEqualTo("QRBASE64");
        assertThat(r.idGru()).isEqualTo("7976123");
        assertThat(r.pixExpiracao()).isNotNull();
    }

    @Test
    void executaOsSetePassosNaOrdem() {
        client().gerar(CONTRIB);

        List<String> ordem = new ArrayList<>(hits);
        assertThat(ordem).startsWith("/scam/emitgruscam/solicitar_servico.asp");
        assertThat(ordem).containsSubsequence(
            "/scam/emitgruscam/solicitar_servico.asp",
            "/scam/emitgruscam/pagtesouro.asp",
            "/scam/emitgruscam/pagtesouro_form.asp",
            "/pagtesouro/index.php",
            "/api/pagamentos/dados-pagamento",
            "/api/pagamentos/meios-pagamento/pix");
    }

    @Test
    void geraBoletoPdf() {
        GruBoletoResultado r = client().gerarBoleto(CONTRIB);

        assertThat(r.idGru()).isEqualTo("7977050");
        assertThat(r.pdf()).isNotEmpty();
        assertThat(new String(r.pdf(), StandardCharsets.UTF_8)).startsWith("%PDF");
        assertThat(hits).containsSubsequence(
            "/scam/emitgruscam/solicitar_servico.asp",
            "/scam/emitgruscam/atualiza_gru.asp",
            "/scam/emitgruscam/imprime_gru.asp",
            "/scam/emitgruscam/gru/tmp/4017977050.pdf");
    }

    @Test
    void sondaPixPago() {
        GruPagamentoStatus s = client().consultarStatusPix("ses-pago");
        assertThat(s.pago()).isTrue();
        assertThat(s.situacao()).isEqualTo("CONCLUIDO");
        assertThat(s.numeroReferencia()).isEqualTo("80893100021762026");
        assertThat(s.nomeContribuinte()).isEqualTo("THALIA");
        assertThat(s.dataPagamento()).isEqualTo(java.time.Instant.parse("2026-06-26T07:04:31Z"));
    }

    @Test
    void sondaPixPendenteRetornaNaoPago() {
        GruPagamentoStatus s = client().consultarStatusPix("ses-qualquer");
        assertThat(s.pago()).isFalse();
        assertThat(s.situacao()).isEqualTo("PENDENTE");
    }

    @Test
    void sondaSessaoExpiradaC0026() {
        GruPagamentoStatus s = client().consultarStatusPix("ses-expirado");
        assertThat(s.pago()).isFalse();
        assertThat(s.situacao()).isEqualTo("EXPIRADO");
    }

    @Test
    void sentinelaDemoPagoRetornaConcluido() {
        // não toca na rede — sentinela DEMO
        GruPagamentoStatus s = client().consultarStatusPix("DEMO-PAGO-xyz");
        assertThat(s.pago()).isTrue();
        assertThat(s.situacao()).isEqualTo("CONCLUIDO");
        assertThat(s.numeroReferencia()).isEqualTo("80893100021762026");
    }

    @Test
    void rejeitaContribuinteSemCpf() {
        GruContribuinte semCpf = new GruContribuinte(
            "", "Nome", null, null, null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> client().gerar(semCpf))
            .isInstanceOf(GruException.class)
            .hasFieldOrPropertyWithValue("codigo", GruException.Codigo.DADOS_INVALIDOS);
    }

    private static void send(com.sun.net.httpserver.HttpExchange ex, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            ex.getResponseBody().write(bytes);
        }
        ex.close();
    }
}
