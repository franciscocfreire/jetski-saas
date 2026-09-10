package com.jetski.locacoes.internal;

import com.jetski.shared.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Links para abrir PDFs por uma URL https real ({@code /api/v1/pdf/<token>}), em vez
 * de {@code blob:} — o iOS Safari não renderiza blob: em aba nova, mas abre PDFs por URL.
 *
 * <p>Dois sabores, porque os dois usos são diferentes:
 * <ul>
 *   <li><b>Efêmero</b> ({@link #criarLink}): conteúdo gerado na hora (ficha, prévia) que
 *       não existe em lugar nenhum — os bytes ficam no Redis por poucos minutos.</li>
 *   <li><b>Compartilhável</b> ({@link #criarLinkCompartilhavel}): guarda só a
 *       <b>referência</b> ao objeto no storage, não os bytes. Vale dias e serve para o
 *       operador mandar ao cliente por e-mail/WhatsApp.</li>
 * </ul>
 *
 * <p>Guardar bytes num link de dias seria caro à toa: o Redis roda com {@code appendonly}
 * e sem limite de memória, então cada compartilhamento de um PDF de ~1 MB (≈1,4 MB em
 * base64) entraria também no AOF. O documento emitido já vive no MinIO para sempre.
 *
 * <p><b>Multiuso desde a V065-b.</b> Antes o token era de uso único e morria em 2 minutos:
 * um F5 na aba do PDF já dava 404, e um link compartilhado nunca abria do outro lado.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PdfLinkService {

    private static final String PREFIX = "pdflink:";
    private static final String F_NOME = "nome";
    private static final String F_PDF = "pdf";
    private static final String F_S3KEY = "s3key";

    private final StringRedisTemplate redis;
    private final StorageService storageService;

    /** Janela para abrir um PDF gerado na hora. Curta, mas tolerante a refresh/retry. */
    @Value("${jetski.pdf-link.efemero-minutos:15}")
    private long efemeroMinutos;

    /** Validade do link de compartilhamento entregue ao cliente. */
    @Value("${jetski.pdf-link.compartilhavel-dias:7}")
    private long compartilhavelDias;

    /** PDF resolvido a partir do token, com o nome que o navegador deve exibir. */
    public record Pdf(byte[] conteudo, String filename) {}

    /** Link efêmero para conteúdo gerado na hora (ficha, prévia): os bytes vão para o Redis. */
    public String criarLink(byte[] pdf, String filename) {
        Map<String, String> campos = new LinkedHashMap<>();
        campos.put(F_NOME, filename);
        campos.put(F_PDF, Base64.getEncoder().encodeToString(pdf));
        return gravar(campos, Duration.ofMinutes(efemeroMinutos));
    }

    /**
     * Link de compartilhamento para um PDF que já está no storage: guarda a referência,
     * vale dias e pode ser aberto quantas vezes for preciso. Revogar = apagar a chave.
     */
    public String criarLinkCompartilhavel(String s3Key, String filename) {
        Map<String, String> campos = new LinkedHashMap<>();
        campos.put(F_NOME, filename);
        campos.put(F_S3KEY, s3Key);
        return gravar(campos, Duration.ofDays(compartilhavelDias));
    }

    private String gravar(Map<String, String> campos, Duration ttl) {
        String token = UUID.randomUUID().toString().replace("-", "")
            + Long.toHexString(System.nanoTime());
        redis.opsForHash().putAll(PREFIX + token, campos);
        redis.expire(PREFIX + token, ttl);
        return "/api/v1/pdf/" + token;
    }

    /**
     * Resolve o token. NÃO consome: o link é multiuso até expirar (ou até ser revogado).
     * Devolve null se inválido, expirado ou se o objeto sumiu do storage.
     */
    public Pdf abrir(String token) {
        String key = PREFIX + token;
        Map<Object, Object> campos = redis.opsForHash().entries(key);
        if (campos.isEmpty()) {
            return null;
        }
        String nome = str(campos.get(F_NOME));
        String s3Key = str(campos.get(F_S3KEY));
        if (s3Key != null) {
            try {
                return new Pdf(storageService.getObject(s3Key), nome);
            } catch (Exception e) {
                log.warn("Link de PDF aponta para objeto ausente no storage: key={}, erro={}",
                    s3Key, e.getMessage());
                return null;
            }
        }
        String b64 = str(campos.get(F_PDF));
        if (b64 == null) {
            return null;
        }
        try {
            return new Pdf(Base64.getDecoder().decode(b64), nome);
        } catch (IllegalArgumentException e) {
            log.warn("Token de PDF com conteúdo inválido: {}", token);
            return null;
        }
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }
}
