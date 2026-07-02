package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.domain.Reserva;
import com.jetski.locacoes.internal.repository.ClienteRepository;
import com.jetski.locacoes.internal.repository.ReservaRepository;
import com.jetski.shared.email.EmailService;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.tenant.TenantQueryService;
import com.jetski.tenant.domain.AssinaturaConfig;
import com.jetski.tenant.domain.Tenant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

/**
 * OTP (código de confirmação) no aceite: vincula a assinatura ao canal do cliente
 * (e-mail ou WhatsApp), como fator de posse. Fase B do reforço jurídico.
 *
 * <p>O código fica no Redis com TTL curto; a verificação marca uma flag também no
 * Redis, exigida por {@link AceiteService} antes de gravar o aceite (quando o tenant
 * tem OTP ligado). WhatsApp usa link wa.me (sem API de envio automática).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AceiteOtpService {

    private static final Duration TTL_CODE = Duration.ofMinutes(10);
    private static final Duration TTL_OK = Duration.ofMinutes(15);
    private static final int MAX_TENTATIVAS = 5;

    private final ReservaRepository reservaRepository;
    private final ClienteRepository clienteRepository;
    private final TenantQueryService tenantQueryService;
    private final EmailService emailService;
    private final StringRedisTemplate redis;
    private final SecureRandom rng = new SecureRandom();

    public record EnvioResultado(boolean ativo, String canal, String destinoMascarado,
                                 String whatsappUrl, String mensagem) {}

    public record OtpStatus(boolean ativo, String canal, boolean verificado) {}

    /** OTP está ligado para o tenant? Qual canal? Já verificado nesta sessão? */
    public OtpStatus status(UUID reservaId) {
        AssinaturaConfig.Otp otp = otpConfig(reservaId);
        boolean ativo = otp != null && Boolean.TRUE.equals(otp.ativo());
        String canal = otp != null && otp.canal() != null ? otp.canal() : "EMAIL";
        return new OtpStatus(ativo, canal, ativo && verificacaoValida(reservaId) != null);
    }

    /** Gera e envia o código pelo canal configurado. Retorna dados p/ o front (destino/whatsappUrl). */
    public EnvioResultado enviar(UUID reservaId) {
        Reserva reserva = reservaRepository.findById(reservaId)
            .orElseThrow(() -> new NotFoundException("Reserva não encontrada: " + reservaId));
        AssinaturaConfig.Otp otp = otpConfig(reservaId);
        if (otp == null || !Boolean.TRUE.equals(otp.ativo())) {
            return new EnvioResultado(false, null, null, null, "OTP não está ativo para esta loja.");
        }
        Cliente cliente = clienteRepository.findById(reserva.getClienteId())
            .orElseThrow(() -> new NotFoundException("Cliente não encontrado"));

        String canal = otp.canal() != null ? otp.canal() : "EMAIL";
        String codigo = String.format("%06d", rng.nextInt(1_000_000));
        redis.opsForValue().set(keyCode(reservaId), codigo, TTL_CODE);
        redis.delete(keyTries(reservaId));

        if ("WHATSAPP".equalsIgnoreCase(canal)) {
            String tel = soDigitos(cliente.getTelefone() != null ? cliente.getTelefone() : cliente.getWhatsapp());
            if (tel.isBlank()) throw new BusinessException("Cliente sem telefone/WhatsApp para envio do código.");
            String texto = "Seu código de confirmação da assinatura é: " + codigo
                + "\nVálido por 10 minutos. Não compartilhe.";
            String url = "https://wa.me/" + comDdi(tel) + "?text="
                + URLEncoder.encode(texto, StandardCharsets.UTF_8);
            log.info("OTP aceite reserva={} canal=WHATSAPP destino={}", reservaId, mascararTel(tel));
            return new EnvioResultado(true, "WHATSAPP", mascararTel(tel), url,
                "Abra o WhatsApp e envie o código ao cliente.");
        }

        // EMAIL (padrão): envio direto (o operador não vê o código).
        String email = cliente.getEmail();
        if (email == null || email.isBlank())
            throw new BusinessException("Cliente sem e-mail para envio do código.");
        Tenant t = tenantQueryService.findById(reserva.getTenantId());
        String loja = t != null && t.getRazaoSocial() != null ? t.getRazaoSocial() : "a loja";
        String html = "<p>Olá" + (cliente.getNome() != null ? ", " + cliente.getNome() : "") + ".</p>"
            + "<p>Seu código de confirmação da assinatura em <b>" + loja + "</b> é:</p>"
            + "<p style=\"font-size:24px;font-weight:bold;letter-spacing:3px\">" + codigo + "</p>"
            + "<p>Válido por 10 minutos. Se não reconhece esta solicitação, ignore este e-mail.</p>";
        emailService.sendEmail(email, "Código de confirmação da assinatura", html);
        log.info("OTP aceite reserva={} canal=EMAIL destino={}", reservaId, mascararEmail(email));
        return new EnvioResultado(true, "EMAIL", mascararEmail(email), null,
            "Enviamos um código para o e-mail do cliente.");
    }

    /** Confere o código. Verdadeiro → marca a verificação (exigida pelo aceite). */
    public boolean verificar(UUID reservaId, String codigo) {
        String esperado = redis.opsForValue().get(keyCode(reservaId));
        if (esperado == null) {
            throw new BusinessException("Código expirado ou não solicitado. Envie um novo código.");
        }
        Long tentativas = redis.opsForValue().increment(keyTries(reservaId));
        if (tentativas != null && tentativas == 1L) {
            redis.expire(keyTries(reservaId), TTL_CODE);
        }
        if (tentativas != null && tentativas > MAX_TENTATIVAS) {
            redis.delete(keyCode(reservaId));
            throw new BusinessException("Muitas tentativas. Envie um novo código.");
        }
        if (codigo == null || !codigo.trim().equals(esperado)) {
            return false;
        }
        AssinaturaConfig.Otp otp = otpConfig(reservaId);
        String canal = otp != null && otp.canal() != null ? otp.canal() : "EMAIL";
        redis.opsForValue().set(keyOk(reservaId), canal + "|" + destinoAtual(reservaId, canal), TTL_OK);
        redis.delete(keyCode(reservaId));
        redis.delete(keyTries(reservaId));
        return true;
    }

    /** "canal|destino" se verificado e válido; null caso contrário. */
    public String verificacaoValida(UUID reservaId) {
        return redis.opsForValue().get(keyOk(reservaId));
    }

    /** Consome a verificação (uso único) após gravar o aceite. */
    public void consumir(UUID reservaId) {
        redis.delete(keyOk(reservaId));
    }

    // ---- helpers ----

    private AssinaturaConfig.Otp otpConfig(UUID reservaId) {
        Reserva reserva = reservaRepository.findById(reservaId).orElse(null);
        if (reserva == null) return null;
        Tenant t = tenantQueryService.findById(reserva.getTenantId());
        AssinaturaConfig cfg = t != null && t.getAssinaturaConfig() != null
            ? t.getAssinaturaConfig().comDefaults() : AssinaturaConfig.padrao();
        return cfg.otp();
    }

    private String destinoAtual(UUID reservaId, String canal) {
        Reserva reserva = reservaRepository.findById(reservaId).orElse(null);
        if (reserva == null) return "";
        Cliente c = clienteRepository.findById(reserva.getClienteId()).orElse(null);
        if (c == null) return "";
        if ("WHATSAPP".equalsIgnoreCase(canal)) {
            return mascararTel(soDigitos(c.getTelefone() != null ? c.getTelefone() : c.getWhatsapp()));
        }
        return mascararEmail(c.getEmail());
    }

    private String keyCode(UUID r) { return "otp:code:" + r; }
    private String keyTries(UUID r) { return "otp:tries:" + r; }
    private String keyOk(UUID r) { return "otp:ok:" + r; }

    private static String soDigitos(String s) { return s == null ? "" : s.replaceAll("\\D", ""); }

    private static String comDdi(String telDigitos) {
        return telDigitos.startsWith("55") ? telDigitos : "55" + telDigitos;
    }

    private static String mascararEmail(String email) {
        if (email == null || !email.contains("@")) return "e-mail cadastrado";
        int at = email.indexOf('@');
        String u = email.substring(0, at);
        String dom = email.substring(at);
        String vis = u.length() <= 2 ? u.charAt(0) + "*" : u.substring(0, 2) + "***";
        return vis + dom;
    }

    private static String mascararTel(String tel) {
        if (tel == null || tel.length() < 4) return "telefone cadastrado";
        return "****" + tel.substring(tel.length() - 4);
    }
}
