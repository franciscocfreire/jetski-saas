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
