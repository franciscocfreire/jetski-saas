package com.jetski.shared.assinatura;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Carimbo de tempo sobre um hash de documento, para reforço jurídico da assinatura
 * (prova de anterioridade/integridade). Tenta uma TSA RFC 3161 gratuita; se
 * indisponível/desligada, cai numa "âncora interna" (HMAC do hash+instante com a
 * chave da plataforma) — grátis e sem bloquear a emissão. Ver Lei 14.063/2020.
 */
@Service
@Slf4j
public class CarimboTempoService {

    /** Resultado do carimbo. token = base64 do TimeStampToken (TSA) ou do HMAC (interno). */
    @lombok.Value(staticConstructor = "of")
    public static class Carimbo {
        String fonte;       // "TSA" | "INTERNO"
        String autoridade;  // URL da TSA ou "âncora interna"
        Instant data;       // genTime da TSA ou horário do servidor
        String serial;      // resumo p/ referência (SHA-256 do token, 16 hex)
        String token;       // base64 do token/HMAC (verificação posterior)
    }

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final SecureRandom rng = new SecureRandom();

    @Value("${jetski.secret.key:}")
    private String secretKey;

    /** Carimba o hash SHA-256 de {@code conteudo}. Nunca lança — degrada p/ âncora interna. */
    public Carimbo carimbar(byte[] conteudo, boolean usarTsa, String tsaUrl) {
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(conteudo);
        } catch (Exception e) {
            return ancoraInterna(new byte[0]);
        }
        if (usarTsa && tsaUrl != null && !tsaUrl.isBlank()) {
            try {
                return viaTsa(digest, tsaUrl);
            } catch (Exception e) {
                log.warn("Carimbo TSA falhou ({}), usando âncora interna: {}", tsaUrl, e.getMessage());
            }
        }
        return ancoraInterna(digest);
    }

    private Carimbo viaTsa(byte[] digest, String tsaUrl) throws Exception {
        TimeStampRequestGenerator gen = new TimeStampRequestGenerator();
        gen.setCertReq(true);
        TimeStampRequest req = gen.generate(TSPAlgorithms.SHA256, digest, new BigInteger(64, rng));
        byte[] reqBytes = req.getEncoded();

        HttpRequest httpReq = HttpRequest.newBuilder(URI.create(tsaUrl))
                .header("Content-Type", "application/timestamp-query")
                .timeout(Duration.ofSeconds(8))
                .POST(HttpRequest.BodyPublishers.ofByteArray(reqBytes))
                .build();
        HttpResponse<byte[]> resp = http.send(httpReq, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("TSA HTTP " + resp.statusCode());
        }
        TimeStampResponse tsResp = new TimeStampResponse(resp.body());
        tsResp.validate(req);
        TimeStampToken token = tsResp.getTimeStampToken();
        if (token == null) {
            throw new IllegalStateException("TSA sem token: " + tsResp.getStatusString());
        }
        byte[] tokenBytes = token.getEncoded();
        Instant data = token.getTimeStampInfo().getGenTime().toInstant();
        return Carimbo.of("TSA", tsaUrl, data, serial(tokenBytes),
                Base64.getEncoder().encodeToString(tokenBytes));
    }

    private Carimbo ancoraInterna(byte[] digest) {
        Instant agora = Instant.now();
        byte[] base = (HexFormat.of().formatHex(digest) + "|" + agora.toEpochMilli())
                .getBytes(StandardCharsets.UTF_8);
        byte[] token = base;
        try {
            if (secretKey != null && !secretKey.isBlank()) {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                token = mac.doFinal(base);
            }
        } catch (Exception e) {
            log.debug("HMAC da âncora indisponível: {}", e.getMessage());
        }
        return Carimbo.of("INTERNO", "âncora interna (sem TSA)", agora, serial(token),
                Base64.getEncoder().encodeToString(token));
    }

    private static String serial(byte[] token) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(token);
            return HexFormat.of().formatHex(h).substring(0, 16).toUpperCase();
        } catch (Exception e) {
            return "";
        }
    }
}
