package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.Instrutor;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.GoneException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.security.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Link único para o instrutor assinar remotamente (V071).
 *
 * <p>O instrutor não é usuário do sistema: até aqui a assinatura só podia ser desenhada no
 * cadastro, com ele presente. A empresa gera um link de <b>uso único</b> (7 dias) e manda ao
 * instrutor; ao assinar, a imagem substitui a do cadastro por {@link InstrutorService#atualizar}
 * — mesmas regras da edição, inclusive a volta para aprovação da EAMA na emissão delegada.
 *
 * <p>Segurança: só o SHA-256 do token vai para o banco; gerar um link novo desativa os
 * anteriores daquele instrutor; o conhecimento do token é a autorização (como o claim-token
 * do cliente). As evidências do ato (IP, user-agent, hash da imagem, data) ficam na linha.
 *
 * <p>RLS: a página pública não tem tenant na sessão — a policy da V071 libera a busca pelo
 * hash nesse caso; depois de achar o link, a transação fixa a empresa dele
 * ({@link #fixarEmpresa}) antes de ler o instrutor e gravar a assinatura.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstrutorAssinaturaLinkService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String ALFANUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int TOKEN_LEN = 40;
    private static final int VALIDADE_DIAS = 7;
    /** Assinatura desenhada vira um PNG pequeno; teto folgado contra abuso da rota pública. */
    private static final int MAX_BYTES = 1_000_000;
    private static final byte[] PNG_ASSINATURA = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;
    private final InstrutorService instrutorService;

    @Value("${jetski.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    /** Link recém-gerado (a URL carrega o token em claro — só existe nesta resposta). */
    public record LinkGerado(String url, Instant expiraEm) {}

    /** O que a página pública mostra antes de assinar. */
    public record LinkInfo(String instrutorNome, String empresa, Instant expiraEm, boolean temAssinatura) {}

    /** Resultado do envio da assinatura. */
    public record AssinaturaRegistrada(String instrutorNome) {}

    /** Gera um link novo para o instrutor da empresa corrente e desativa os anteriores. */
    @Transactional
    public LinkGerado gerar(UUID instrutorId) {
        Instrutor instrutor = instrutorService.buscar(instrutorId);
        if (!Boolean.TRUE.equals(instrutor.getAtivo())) {
            throw new BusinessException("Instrutor inativo: reative o cadastro antes de pedir a assinatura.");
        }
        UUID tenantId = instrutor.getTenantId();
        jdbc.update("UPDATE instrutor_assinatura_link SET ativo = false "
            + "WHERE tenant_id = ? AND instrutor_id = ? AND ativo", tenantId, instrutorId);

        String token = gerarToken();
        Instant expiraEm = Instant.now().plus(VALIDADE_DIAS, ChronoUnit.DAYS);
        jdbc.update("""
            INSERT INTO instrutor_assinatura_link (tenant_id, instrutor_id, token_hash, expira_em, criado_por)
            VALUES (?, ?, ?, ?, ?)
            """, tenantId, instrutorId, sha256(token.getBytes(StandardCharsets.UTF_8)),
            Timestamp.from(expiraEm), usuarioAtualOuNulo());

        log.info("Link de assinatura gerado: tenant={}, instrutor={}, expiraEm={}", tenantId, instrutorId, expiraEm);
        return new LinkGerado(frontendUrl + "/assinar-instrutor?token=" + token, expiraEm);
    }

    /** Dados do link para a página pública. Link inválido → 404; usado/expirado/substituído → 410. */
    @Transactional
    public LinkInfo consultar(String token) {
        Map<String, Object> link = linkValido(token, false);
        UUID tenantId = (UUID) link.get("tenant_id");
        fixarEmpresa(tenantId);
        Instrutor instrutor = instrutorService.buscar((UUID) link.get("instrutor_id"));
        return new LinkInfo(instrutor.getNome(), razaoSocial(tenantId),
            ((Timestamp) link.get("expira_em")).toInstant(), instrutor.getAssinaturaS3Key() != null);
    }

    /** Registra a assinatura enviada pelo link e consome o link (uso único). */
    @Transactional
    public AssinaturaRegistrada assinar(String token, String assinaturaBase64, String ip, String userAgent) {
        Map<String, Object> link = linkValido(token, true);
        byte[] png = decodificarPng(assinaturaBase64);
        UUID tenantId = (UUID) link.get("tenant_id");
        UUID instrutorId = (UUID) link.get("instrutor_id");

        fixarEmpresa(tenantId);
        Instrutor atualizado = instrutorService.atualizar(instrutorId, new Instrutor(), assinaturaBase64);

        jdbc.update("""
            UPDATE instrutor_assinatura_link
               SET usado_em = now(), ativo = false, ip = ?, user_agent = ?, assinatura_sha256 = ?
             WHERE id = ?
            """, cortar(ip, 64), cortar(userAgent, 500), sha256(png), link.get("id"));

        log.info("Assinatura do instrutor registrada por link: tenant={}, instrutor={}, ip={}",
            tenantId, instrutorId, ip);
        return new AssinaturaRegistrada(atualizado.getNome());
    }

    // ------------------------------------------------------------------

    private Map<String, Object> linkValido(String token, boolean travar) {
        if (token == null || token.length() != TOKEN_LEN) {
            throw new NotFoundException("Link de assinatura inválido.");
        }
        List<Map<String, Object>> linhas = jdbc.queryForList(
            "SELECT id, tenant_id, instrutor_id, expira_em, ativo, usado_em FROM instrutor_assinatura_link "
                + "WHERE token_hash = ?" + (travar ? " FOR UPDATE" : ""),
            sha256(token.getBytes(StandardCharsets.UTF_8)));
        if (linhas.isEmpty()) {
            throw new NotFoundException("Link de assinatura inválido.");
        }
        Map<String, Object> link = linhas.get(0);
        if (link.get("usado_em") != null) {
            throw new GoneException("Este link já foi usado. Peça um novo link à empresa, se precisar assinar de novo.");
        }
        if (!Boolean.TRUE.equals(link.get("ativo"))) {
            throw new GoneException("Este link foi substituído por um mais novo. Use o último link enviado pela empresa.");
        }
        if (((Timestamp) link.get("expira_em")).toInstant().isBefore(Instant.now())) {
            throw new GoneException("Este link expirou. Peça um novo link à empresa.");
        }
        return link;
    }

    /**
     * Fixa a empresa do link na transação e no contexto: o instrutor tem RLS estrita e a
     * key da assinatura no storage é montada a partir do tenant do contexto.
     */
    private void fixarEmpresa(UUID tenantId) {
        entityManager.createNativeQuery("SELECT set_config('app.tenant_id', :tid, true)")
            .setParameter("tid", tenantId.toString())
            .getSingleResult();
        TenantContext.setTenantId(tenantId);
    }

    private String razaoSocial(UUID tenantId) {
        List<String> nomes = jdbc.queryForList("SELECT razao_social FROM tenant WHERE id = ?", String.class, tenantId);
        return nomes.isEmpty() ? null : nomes.get(0);
    }

    private static byte[] decodificarPng(String dataUrl) {
        if (dataUrl == null || dataUrl.isBlank()) {
            throw new BusinessException("Desenhe a assinatura antes de enviar.");
        }
        String puro = dataUrl.contains(",") ? dataUrl.substring(dataUrl.indexOf(',') + 1) : dataUrl;
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(puro.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Imagem da assinatura inválida.");
        }
        if (bytes.length < PNG_ASSINATURA.length || bytes.length > MAX_BYTES) {
            throw new BusinessException("Imagem da assinatura inválida.");
        }
        for (int i = 0; i < PNG_ASSINATURA.length; i++) {
            if (bytes[i] != PNG_ASSINATURA[i]) {
                throw new BusinessException("Imagem da assinatura inválida (esperado PNG).");
            }
        }
        return bytes;
    }

    private static String gerarToken() {
        StringBuilder sb = new StringBuilder(TOKEN_LEN);
        for (int i = 0; i < TOKEN_LEN; i++) {
            sb.append(ALFANUM.charAt(SECURE_RANDOM.nextInt(ALFANUM.length())));
        }
        return sb.toString();
    }

    private static String sha256(byte[] dados) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(dados));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String cortar(String s, int max) {
        return s == null ? null : (s.length() > max ? s.substring(0, max) : s);
    }

    private static UUID usuarioAtualOuNulo() {
        try {
            return TenantContext.getUsuarioId();
        } catch (Exception e) {
            return null;
        }
    }
}
