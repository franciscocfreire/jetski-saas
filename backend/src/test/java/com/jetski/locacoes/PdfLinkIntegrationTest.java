package com.jetski.locacoes;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.locacoes.internal.PdfLinkService;
import com.jetski.shared.storage.StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Link de PDF ({@code /v1/pdf/<token>}).
 *
 * <p>Cobre os dois defeitos que apareceram em produção: o PDF abria sempre como
 * {@code documento.pdf} (não dava para saber de quem era) e o token era de uso único
 * com 2 minutos de vida — um refresh na aba já dava 404 e um link compartilhado por
 * e-mail/WhatsApp nunca abria do outro lado.
 */
@AutoConfigureMockMvc
@DisplayName("Link de PDF — nome do arquivo e compartilhamento")
class PdfLinkIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired PdfLinkService pdfLinkService;
    @Autowired StorageService storageService;

    private static final byte[] PDF = "%PDF-1.4 conteudo".getBytes(StandardCharsets.UTF_8);

    /** O link devolvido inclui o context-path /api, que o MockMvc não usa. */
    private static String rota(String url) {
        return url.replaceFirst("^/api", "");
    }

    @Test
    @DisplayName("O nome do locatário chega ao navegador (filename* em UTF-8)")
    void entregaComNomeDoLocatario() throws Exception {
        String url = pdfLinkService.criarLink(PDF, "Rogério Ferreira 36744561849.pdf");

        String disposition = mockMvc.perform(get(rota(url)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);

        // RFC 5987: acento e espaços não sobrevivem a um filename= simples.
        assertThat(disposition).contains("filename*=UTF-8''");
        assertThat(java.net.URLDecoder.decode(disposition, StandardCharsets.UTF_8))
            .contains("Rogério Ferreira 36744561849.pdf");
    }

    @Test
    @DisplayName("O link é multiuso: abrir duas vezes continua funcionando")
    void linkNaoEhConsumidoNaPrimeiraAbertura() throws Exception {
        String url = pdfLinkService.criarLink(PDF, "Ana Lima 123.pdf");

        mockMvc.perform(get(rota(url))).andExpect(status().isOk());
        // Era aqui que o compartilhamento morria: o primeiro acesso apagava o token.
        mockMvc.perform(get(rota(url))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Link compartilhável guarda a referência ao storage, não os bytes")
    void compartilhavelResolveDoStorage() throws Exception {
        String key = "tenant-teste/reserva/" + UUID.randomUUID() + "/documento.pdf";
        storageService.putObject(key, PDF, "application/pdf");

        String url = pdfLinkService.criarLinkCompartilhavel(key, "FELIPE H M 49811586888.pdf");

        byte[] body = mockMvc.perform(get(rota(url)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsByteArray();
        assertThat(body).isEqualTo(PDF);

        // O conteúdo veio do storage — guardar ~1,4 MB em base64 por dias no Redis
        // (que roda com appendonly) é o que se quis evitar.
        PdfLinkService.Pdf resolvido = pdfLinkService.abrir(
            url.substring(url.lastIndexOf('/') + 1));
        assertThat(resolvido.filename()).isEqualTo("FELIPE H M 49811586888.pdf");
    }

    @Test
    @DisplayName("Token inexistente → 404 (e não um PDF vazio)")
    void tokenDesconhecido() throws Exception {
        mockMvc.perform(get("/v1/pdf/{t}", "naoexiste123")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Referência apontando para objeto que sumiu do storage → 404")
    void referenciaOrfa() throws Exception {
        String url = pdfLinkService.criarLinkCompartilhavel(
            "tenant-teste/nao/existe.pdf", "Sumido 1.pdf");

        mockMvc.perform(get(rota(url))).andExpect(status().isNotFound());
    }
}
