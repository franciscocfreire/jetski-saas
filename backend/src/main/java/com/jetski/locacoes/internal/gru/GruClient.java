package com.jetski.locacoes.internal.gru;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cliente HTTP que reproduz, sem navegador, o fluxo de emissão da GRU da Marinha
 * (dpc1.marinha.mil.br) + geração do PIX no PagTesouro (Tesouro Nacional).
 *
 * <p>Fluxo de 7 passos documentado em {@code GRU_HTTP_CONTRACT.md}. Cada chamada a
 * {@link #gerar} cria uma sessão ASP nova (cookie jar próprio, single-use).
 *
 * <p><b>Respeito ao site gov:</b> sem paralelismo agressivo, 1 GRU por vez,
 * timeouts por requisição. Não loga PII (CPF/endereço).
 */
@Slf4j
@Component
public class GruClient {

    private static final String UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36";
    private static final ZoneId ZONE_BR = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter EXP_FMT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Pattern ID_GRU = Pattern.compile("id_gru=(\\d+)");
    private static final Pattern TOKEN =
        Pattern.compile("name=\"token\"[^>]*value=\"([^\"]+)\"");
    private static final Pattern ID_SESSAO =
        Pattern.compile("idSessao=([0-9a-fA-F-]{36})");

    private final ObjectMapper objectMapper;
    private final String marinhaBase;
    private final String pagtesouroBase;
    private final Duration timeout;
    private final boolean cascataHabilitada;

    // Códigos da Capitania de SP / Amador / Serviços Administrativos / CHA (v1 só SP).
    private final String cdOrgao;
    private final String tipoRecolhimento;
    private final String tipoServico;
    private final String itemServico;

    public GruClient(
            ObjectMapper objectMapper,
            @Value("${jetski.gru.marinha-base:https://dpc1.marinha.mil.br}") String marinhaBase,
            @Value("${jetski.gru.pagtesouro-base:https://pagtesouro.tesouro.gov.br}") String pagtesouroBase,
            @Value("${jetski.gru.timeout-seconds:20}") int timeoutSeconds,
            @Value("${jetski.gru.cascata-habilitada:true}") boolean cascataHabilitada,
            @Value("${jetski.gru.cd-orgao:89310}") String cdOrgao,
            @Value("${jetski.gru.tipo-recolhimento:1}") String tipoRecolhimento,
            @Value("${jetski.gru.tipo-servico:060}") String tipoServico,
            @Value("${jetski.gru.item-servico:060;288  ;408}") String itemServico) {
        this.objectMapper = objectMapper;
        this.marinhaBase = stripTrailingSlash(marinhaBase);
        this.pagtesouroBase = stripTrailingSlash(pagtesouroBase);
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.cascataHabilitada = cascataHabilitada;
        this.cdOrgao = cdOrgao;
        this.tipoRecolhimento = tipoRecolhimento;
        this.tipoServico = tipoServico;
        this.itemServico = itemServico;
    }

    /**
     * Executa o fluxo completo e devolve a GRU + PIX. Lança {@link GruException}
     * em qualquer falha (o serviço chamador cai no fallback manual).
     */
    public GruResultado gerar(GruContribuinte c) {
        if (c == null || isBlank(c.cpf()) || isBlank(c.nome())) {
            throw new GruException(GruException.Codigo.DADOS_INVALIDOS,
                "CPF e nome são obrigatórios para gerar a GRU");
        }

        // Cookie jar MANUAL: o CookieManager do JDK rejeita o Set-Cookie malformado
        // da Marinha ("httpOnly;secure;") e descarta a sessão ASP. Gerenciamos à mão.
        HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(timeout)
            .build();
        Map<String, String> cookies = new LinkedHashMap<>();

        String solicitarUrl = marinhaBase + "/scam/emitgruscam/solicitar_servico.asp";

        // Passo 1 — abrir o formulário (cria a sessão ASP / cookie)
        get(http, cookies, solicitarUrl, null, GruException.Codigo.MARINHA_INDISPONIVEL);

        // Passo 2 — cascata (prepara o estado da sessão); pode ser desabilitada
        if (cascataHabilitada) {
            try {
                executarCascata(http, cookies, c, solicitarUrl);
            } catch (GruException e) {
                log.warn("Cascata da GRU falhou (seguindo mesmo assim): {}", e.getMessage());
            }
        }

        // Passo 3 — gerar a GRU → 302 com id_gru no Location
        HttpResponse<String> r3 = postForm(http, cookies,
            marinhaBase + "/scam/emitgruscam/pagtesouro.asp",
            formGerarGru(c), solicitarUrl, GruException.Codigo.MARINHA_INDISPONIVEL);
        String location = r3.headers().firstValue("Location").orElse("");
        if (r3.statusCode() != 302 || location.isBlank()) {
            throw new GruException(GruException.Codigo.MARINHA_INDISPONIVEL,
                "pagtesouro.asp não retornou redirect (status " + r3.statusCode() + ")");
        }
        Matcher mGru = ID_GRU.matcher(location);
        if (!mGru.find()) {
            log.warn("Passo 3 sem id_gru. status={} location='{}'", r3.statusCode(), location);
            throw new GruException(GruException.Codigo.MARINHA_INDISPONIVEL,
                "id_gru não encontrado no Location");
        }
        String idGru = mGru.group(1);
        URI pontoUri = URI.create(solicitarUrl).resolve(location);

        // Passo 4 — página-ponte: pega o token (form auto-submit frm_envio)
        HttpResponse<String> r4 = get(http, cookies, pontoUri.toString(), solicitarUrl,
            GruException.Codigo.BRIDGE_FALHOU);
        String token = extrair(TOKEN, r4.body(), GruException.Codigo.BRIDGE_FALHOU,
            "token não encontrado em pagtesouro_form.asp");

        // Passo 5 — bridge → cria idSessao no PagTesouro
        HttpResponse<String> r5 = postForm(http, cookies,
            marinhaBase + "/pagtesouro/index.php",
            formBridge(c, idGru, token), pontoUri.toString(), GruException.Codigo.BRIDGE_FALHOU);
        Matcher mSes = ID_SESSAO.matcher(r5.body());
        if (!mSes.find()) {
            String body = r5.body();
            boolean authProblem = body.contains("autentica"); // "Houve um problema de autenticação!"
            log.warn("Passo 5 sem idSessao. status={} len={} tokenLen={} authProblem={}",
                r5.statusCode(), body.length(), token.length(), authProblem);
            throw new GruException(GruException.Codigo.BRIDGE_FALHOU, authProblem
                ? "Marinha recusou a autenticação no PagTesouro (token do site inválido/instável). "
                  + "Gere a GRU manualmente no site da Marinha."
                : "idSessao não encontrado na resposta do index.php");
        }
        String idSessao = mSes.group(1);

        // Passo 6 — dados do pagamento (valor, número da GRU, descrição)
        HttpResponse<String> r6 = get(http, cookies,
            pagtesouroBase + "/api/pagamentos/dados-pagamento?idSessao=" + idSessao,
            pagtesouroBase + "/", GruException.Codigo.PAGTESOURO_FALHOU);
        JsonNode dados = parseJson(r6.body());
        String gruNumero = texto(dados, "numeroReferencia");
        BigDecimal gruValor = dados.has("valor") ? dados.get("valor").decimalValue() : null;
        String descricao = texto(dados, "descricao");

        // Passo 7 — gerar o PIX
        HttpResponse<String> r7 = postJson(http, cookies,
            pagtesouroBase + "/api/pagamentos/meios-pagamento/pix",
            bodyPix(idSessao, descricao), GruException.Codigo.PAGTESOURO_FALHOU);
        JsonNode pix = parseJson(r7.body());
        String copiaECola = texto(pix, "conteudo");
        if (isBlank(copiaECola)) {
            throw new GruException(GruException.Codigo.PAGTESOURO_FALHOU,
                "PIX sem conteúdo (copia-e-cola)");
        }
        String qr = texto(pix, "imagem");
        Instant expiracao = parseExpiracao(texto(pix, "dataExpiracao"));

        log.info("GRU gerada: numero={}, valor={}, idGru={}", gruNumero, gruValor, idGru);
        return new GruResultado(gruNumero, gruValor, descricao, copiaECola, qr, expiracao, idGru);
    }

    /**
     * Gera o BOLETO da GRU (PDF) — caminho alternativo ao PIX. Fluxo:
     * solicitar_servico → cascata → POST atualiza_gru.asp (302 → imprime_gru.asp)
     * → GET imprime_gru.asp (302 → .pdf) → GET .pdf. Lança {@link GruException} em falha.
     */
    public GruBoletoResultado gerarBoleto(GruContribuinte c) {
        if (c == null || isBlank(c.cpf()) || isBlank(c.nome())) {
            throw new GruException(GruException.Codigo.DADOS_INVALIDOS,
                "CPF e nome são obrigatórios para gerar a GRU");
        }

        HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(timeout)
            .build();
        Map<String, String> cookies = new LinkedHashMap<>();

        String solicitarUrl = marinhaBase + "/scam/emitgruscam/solicitar_servico.asp";
        get(http, cookies, solicitarUrl, null, GruException.Codigo.MARINHA_INDISPONIVEL);
        if (cascataHabilitada) {
            try {
                executarCascata(http, cookies, c, solicitarUrl);
            } catch (GruException e) {
                log.warn("Cascata da GRU (boleto) falhou (seguindo): {}", e.getMessage());
            }
        }

        // POST atualiza_gru.asp → 302 imprime_gru.asp?v_id_gru=N&v_cd_recolhimento=...
        HttpResponse<String> r1 = postForm(http, cookies,
            marinhaBase + "/scam/emitgruscam/atualiza_gru.asp",
            formGerarGru(c), solicitarUrl, GruException.Codigo.MARINHA_INDISPONIVEL);
        String loc1 = r1.headers().firstValue("Location").orElse("");
        Matcher mGru = ID_GRU.matcher(loc1);
        if (r1.statusCode() != 302 || !mGru.find()) {
            log.warn("Boleto: atualiza_gru.asp sem id_gru. status={} location='{}'",
                r1.statusCode(), loc1);
            throw new GruException(GruException.Codigo.MARINHA_INDISPONIVEL,
                "atualiza_gru.asp não retornou a GRU (boleto)");
        }
        String idGru = mGru.group(1);
        URI imprimeUri = URI.create(solicitarUrl).resolve(loc1);

        // GET imprime_gru.asp → 302 .../gru/tmp/<arquivo>.pdf
        HttpResponse<String> r2 = get(http, cookies, imprimeUri.toString(), solicitarUrl,
            GruException.Codigo.MARINHA_INDISPONIVEL);
        String loc2 = r2.headers().firstValue("Location").orElse("");
        if (r2.statusCode() != 302 || loc2.isBlank()) {
            throw new GruException(GruException.Codigo.MARINHA_INDISPONIVEL,
                "imprime_gru.asp não retornou o PDF (status " + r2.statusCode() + ")");
        }
        URI pdfUri = URI.create(solicitarUrl).resolve(loc2);

        // GET o PDF (binário)
        HttpResponse<byte[]> r3 = getBytes(http, cookies, pdfUri.toString(),
            imprimeUri.toString(), GruException.Codigo.MARINHA_INDISPONIVEL);
        byte[] pdf = r3.body();
        if (pdf == null || pdf.length < 1000 || !looksLikePdf(pdf)) {
            throw new GruException(GruException.Codigo.MARINHA_INDISPONIVEL,
                "PDF do boleto inválido/vazio");
        }
        log.info("Boleto GRU gerado: idGru={}, pdfBytes={}", idGru, pdf.length);
        return new GruBoletoResultado(idGru, pdf);
    }

    private static boolean looksLikePdf(byte[] b) {
        return b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F';
    }

    // ---- passos auxiliares -------------------------------------------------

    private void executarCascata(HttpClient http, Map<String, String> cookies,
                                 GruContribuinte c, String referer) {
        String base = marinhaBase + "/scam/emitgruscam/";
        postForm(http, cookies, base + "objTipoRecolhimentoAjax.asp",
            Map.of("v_cd_orgao", cdOrgao), referer, GruException.Codigo.MARINHA_INDISPONIVEL);
        postForm(http, cookies, base + "objTipoServicoAjax.asp",
            ordered("v_cd_orgao", cdOrgao, "v_id_tipo_recolhimento", tipoRecolhimento),
            referer, GruException.Codigo.MARINHA_INDISPONIVEL);
        postForm(http, cookies, base + "objServicosAjax.asp",
            ordered("v_cd_orgao", cdOrgao, "v_id_tipo_recolhimento", tipoRecolhimento,
                "v_tipo_servico", tipoServico),
            referer, GruException.Codigo.MARINHA_INDISPONIVEL);
        postForm(http, cookies, base + "objQtdeServicoAjax.asp",
            Map.of("v_sel_servico_adm", itemServico), referer,
            GruException.Codigo.MARINHA_INDISPONIVEL);
        postForm(http, cookies, base + "objContribuinte.asp",
            ordered("v_nr_contribuinte", c.cpf(), "v_tipo_documento", "CPF"),
            referer, GruException.Codigo.MARINHA_INDISPONIVEL);
        postForm(http, cookies, base + "objCidadeAjax.asp",
            ordered("v_cep", nz(c.cep()), "v_nr_endereco", nz(c.numero()),
                "v_complemento_contribuinte", nz(c.complemento())),
            referer, GruException.Codigo.MARINHA_INDISPONIVEL);
    }

    private Map<String, String> formGerarGru(GruContribuinte c) {
        // Campos e ordem conforme o POST real capturado (HAR). NÃO enviar
        // telefone/email/sexo (o site não os submete); id_tipo_recolhimento e
        // tipo_servico são obrigatórios para o servidor gerar o id_gru.
        Map<String, String> f = new LinkedHashMap<>();
        f.put("cd_orgao", cdOrgao);
        f.put("id_tipo_recolhimento", tipoRecolhimento);
        f.put("tipo_servico", tipoServico);
        f.put("cd_servico", itemServico);
        f.put("tipo_documento", "CPF");
        f.put("nr_contribuinte", c.cpf());
        f.put("ds_contribuinte", c.nome());
        f.put("fg_incluir_contribuinte", "0");
        f.put("cep_contribuinte", nz(c.cep()));
        f.put("ender_contribuinte", nz(c.logradouro()));
        f.put("nr_endereco", nz(c.numero()));
        f.put("complemento_contribuinte", nz(c.complemento()));
        f.put("bairro_contribuinte", nz(c.bairro()));
        f.put("municipio_contribuinte", nz(c.municipio()));
        f.put("uf_contribuinte", nz(c.uf()));
        return f;
    }

    private Map<String, String> formBridge(GruContribuinte c, String idGru, String token) {
        return ordered(
            "req", hostOf(marinhaBase),
            "token", token,
            "id_gru", idGru,
            "cpf_cnpj", c.cpf(),
            "nome", c.nome(),
            "id_svc", itemServico,
            "qtd", "");
    }

    private String bodyPix(String idSessao, String descricao) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("idSessao", idSessao);
            body.put("informacoesAdicionais", new Object[]{
                Map.of("nome", "Origem", "valor", "PagTesouro"),
                Map.of("nome", "Serviço", "valor", nz(descricao))
            });
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new GruException(GruException.Codigo.PAGTESOURO_FALHOU,
                "falha ao montar corpo do PIX", e);
        }
    }

    // ---- HTTP helpers ------------------------------------------------------

    private HttpResponse<String> get(HttpClient http, Map<String, String> cookies, String url,
                                     String referer, GruException.Codigo erro) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
            .timeout(timeout).GET()
            .header("User-Agent", UA)
            .header("Accept-Language", "pt-BR,pt;q=0.9");
        if (referer != null) {
            b.header("Referer", referer);
        }
        return send(http, cookies, b, erro);
    }

    private HttpResponse<String> postForm(HttpClient http, Map<String, String> cookies, String url,
                                          Map<String, String> form, String referer,
                                          GruException.Codigo erro) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
            .timeout(timeout)
            .header("User-Agent", UA)
            .header("Accept-Language", "pt-BR,pt;q=0.9")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Referer", referer == null ? marinhaBase : referer)
            .POST(HttpRequest.BodyPublishers.ofString(urlEncode(form), StandardCharsets.UTF_8));
        return send(http, cookies, b, erro);
    }

    private HttpResponse<String> postJson(HttpClient http, Map<String, String> cookies, String url,
                                          String json, GruException.Codigo erro) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
            .timeout(timeout)
            .header("User-Agent", UA)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Origin", pagtesouroBase)
            .header("Referer", pagtesouroBase + "/")
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        return send(http, cookies, b, erro);
    }

    private HttpResponse<byte[]> getBytes(HttpClient http, Map<String, String> cookies, String url,
                                          String referer, GruException.Codigo erro) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
            .timeout(timeout).GET()
            .header("User-Agent", UA)
            .header("Accept-Language", "pt-BR,pt;q=0.9");
        if (referer != null) {
            b.header("Referer", referer);
        }
        if (!cookies.isEmpty()) {
            b.header("Cookie", cookieHeader(cookies));
        }
        HttpRequest req = b.build();
        try {
            HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            storeCookies(cookies, resp);
            if (resp.statusCode() >= 400) {
                throw new GruException(erro, "HTTP " + resp.statusCode() + " em " + req.uri().getPath());
            }
            return resp;
        } catch (GruException e) {
            throw e;
        } catch (Exception e) {
            throw new GruException(erro, "falha de rede em " + req.uri().getPath(), e);
        }
    }

    private HttpResponse<String> send(HttpClient http, Map<String, String> cookies,
                                      HttpRequest.Builder builder, GruException.Codigo erro) {
        if (!cookies.isEmpty()) {
            builder.header("Cookie", cookieHeader(cookies));
        }
        HttpRequest req = builder.build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            storeCookies(cookies, resp);
            int s = resp.statusCode();
            // 302 é esperado no passo 3; demais devem ser 2xx
            if (s >= 400) {
                throw new GruException(erro, "HTTP " + s + " em " + req.uri().getPath());
            }
            return resp;
        } catch (GruException e) {
            throw e;
        } catch (Exception e) {
            throw new GruException(erro, "falha de rede em " + req.uri().getPath(), e);
        }
    }

    /** Guarda cookies do Set-Cookie, ignorando headers malformados (sem name=value). */
    private static void storeCookies(Map<String, String> cookies, HttpResponse<?> resp) {
        for (String sc : resp.headers().allValues("Set-Cookie")) {
            String first = sc.split(";", 2)[0].trim();
            int eq = first.indexOf('=');
            if (eq > 0) {
                String name = first.substring(0, eq).trim();
                String value = first.substring(eq + 1).trim();
                if (!name.isEmpty()) {
                    cookies.put(name, value);
                }
            }
        }
    }

    private static String cookieHeader(Map<String, String> cookies) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : cookies.entrySet()) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    // ---- parsing / util ----------------------------------------------------

    private JsonNode parseJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new GruException(GruException.Codigo.PAGTESOURO_FALHOU,
                "resposta JSON inválida do PagTesouro", e);
        }
    }

    private static String extrair(Pattern p, String texto, GruException.Codigo erro, String msg) {
        Matcher m = p.matcher(texto == null ? "" : texto);
        if (!m.find()) {
            throw new GruException(erro, msg);
        }
        return m.group(1);
    }

    private static String texto(JsonNode node, String campo) {
        JsonNode v = node.get(campo);
        return v == null || v.isNull() ? null : v.asText();
    }

    private Instant parseExpiracao(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw.trim(), EXP_FMT).atZone(ZONE_BR).toInstant();
        } catch (Exception e) {
            log.warn("dataExpiracao do PIX em formato inesperado: {}", raw);
            return null;
        }
    }

    private static String urlEncode(Map<String, String> form) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(java.net.URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
              .append('=')
              .append(java.net.URLEncoder.encode(nz(e.getValue()), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static Map<String, String> ordered(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    private static String hostOf(String base) {
        return URI.create(base).getHost();
    }

    private static String stripTrailingSlash(String s) {
        return s != null && s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
