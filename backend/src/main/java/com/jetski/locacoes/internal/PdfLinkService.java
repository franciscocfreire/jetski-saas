package com.jetski.locacoes.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/**
 * Links temporários de uso único para abrir PDFs por uma URL https real
 * (ex.: {@code /api/v1/pdf/<token>}), em vez de blob: — o iOS Safari não
 * renderiza blob: em aba nova, mas abre URLs de PDF nativamente.
 *
 * <p>O PDF (já gerado e autorizado) é guardado no Redis sob um token aleatório
 * com TTL curto; o endpoint público o entrega uma vez e o remove.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PdfLinkService {

    private static final String PREFIX = "pdflink:";
    private static final Duration TTL = Duration.ofMinutes(2);

    private final StringRedisTemplate redis;

    /** Guarda os bytes e devolve a URL pública (relativa) para abrir o PDF. */
    public String criarLink(byte[] pdf) {
        String token = UUID.randomUUID().toString().replace("-", "")
            + Long.toHexString(System.nanoTime());
        redis.opsForValue().set(PREFIX + token, Base64.getEncoder().encodeToString(pdf), TTL);
        return "/api/v1/pdf/" + token;
    }

    /** Consome o token (uso único): devolve os bytes e remove a chave. Null se inválido/expirado. */
    public byte[] consumir(String token) {
        String key = PREFIX + token;
        String b64 = redis.opsForValue().get(key);
        if (b64 == null) {
            return null;
        }
        redis.delete(key);
        try {
            return Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            log.warn("Token de PDF com conteúdo inválido: {}", token);
            return null;
        }
    }
}
