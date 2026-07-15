package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.CustomerProfile;
import com.jetski.locacoes.event.ContaCpfMergeEvent;
import com.jetski.locacoes.internal.repository.CustomerProfileRepository;
import com.jetski.shared.email.EmailService;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.InternalServerException;
import com.jetski.shared.security.FederatedIdentity;
import com.jetski.shared.security.UserProvisioningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Optional;

/**
 * Unificação de contas por CPF (portal do cliente): quando alguém entra com
 * Google e informa um CPF que já pertence a OUTRA conta, oferece o merge
 * seguro — OTP enviado ao e-mail da conta dona do CPF; confirmado o código,
 * a identidade Google da conta duplicada é transferida para a dona e a
 * duplicata é descartada.
 *
 * <p>Anti-takeover: o código vai SÓ para o e-mail da conta dona (nunca em
 * claro na resposta); posse comprovada — nunca merge automático por e-mail.
 * Merge só para duplicata de origem Google (sem identidade federada não há o
 * que transferir — a pessoa deve entrar na conta original).
 *
 * <p>OTP no Redis (padrão {@link AceiteOtpService}), keyed por sub — global,
 * sem tenant e sem tabela nova.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CustomerCpfMergeService {

    private static final String PROVIDER = "keycloak";
    private static final Duration TTL_CODE = Duration.ofMinutes(10);
    private static final Duration TTL_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_TENTATIVAS = 5;

    private final CustomerProfileRepository repository;
    private final CustomerProfileService customerProfileService;
    private final CustomerAccountService customerAccountService;
    private final UserProvisioningService userProvisioningService;
    private final EmailService emailService;
    private final StringRedisTemplate redis;
    private final ApplicationEventPublisher eventPublisher;
    private final SecureRandom rng = new SecureRandom();

    @Value("${portal.google-idp-alias:google}")
    private String idpAlias;

    public record EnvioResultado(boolean disponivel, String emailMascarado,
                                 String motivo, String mensagem) {}

    public record MergeResultado(boolean verificado, boolean mergeConcluido, String mensagem) {}

    /**
     * Verifica a elegibilidade do merge e, se elegível, envia o OTP ao e-mail
     * da conta dona do CPF.
     */
    @Transactional
    public EnvioResultado enviar(String sub, String nomeJwt, String cpfInformado) {
        String cpfDigits = soDigitos(cpfInformado);
        if (cpfDigits.length() != 11) {
            throw new BusinessException("CPF inválido.");
        }

        CustomerProfile atual = customerProfileService.obter(sub, nomeJwt);
        if (atual.getCpf() != null && !atual.getCpf().isBlank()) {
            throw new BusinessException("Sua conta já tem CPF definido.");
        }

        String ownerSub = resolverDonoDoCpf(cpfDigits);
        if (ownerSub == null) {
            return new EnvioResultado(false, null, "CPF_LIVRE",
                "Este CPF não está em uso — salve normalmente no seu perfil.");
        }
        if (ownerSub.equals(sub)) {
            throw new BusinessException("Este CPF já pertence à sua conta.");
        }

        // Guarda anti-perda de dados: a conta atual será descartada no merge —
        // se ela já tem histórico com alguma loja, a unificação é assistida.
        if (!customerAccountService.vinculos(sub).isEmpty()) {
            return new EnvioResultado(false, null, "CONTA_COM_VINCULOS",
                "Sua conta atual já tem histórico com uma loja — fale com a loja para unificar os cadastros.");
        }

        // Merge só para duplicata de origem Google: sem identidade federada não
        // há o que transferir — a pessoa deve entrar na conta original.
        FederatedIdentity fed = userProvisioningService.findFederatedIdentity(sub, idpAlias);
        if (fed == null) {
            return new EnvioResultado(false, null, "SEM_IDENTIDADE_GOOGLE",
                "Sua conta atual foi criada com e-mail e senha. Entre com a conta que já usa este CPF — "
                + "se não lembra a senha, use \"Esqueci minha senha\" na tela de login.");
        }

        String ownerEmail = userProvisioningService.findEmailById(ownerSub);
        if (ownerEmail == null || ownerEmail.isBlank()) {
            return new EnvioResultado(false, null, "SEM_EMAIL",
                "A conta dona deste CPF não tem e-mail utilizável — fale com a loja para regularizar.");
        }

        if (Boolean.TRUE.equals(redis.hasKey(keyCooldown(sub)))) {
            throw new BusinessException("Aguarde um instante antes de reenviar o código.");
        }

        String codigo = String.format("%06d", rng.nextInt(1_000_000));
        redis.opsForValue().set(keyCode(sub), codigo + "|" + ownerSub + "|" + cpfDigits, TTL_CODE);
        redis.delete(keyTries(sub));
        redis.opsForValue().set(keyCooldown(sub), "1", TTL_COOLDOWN);

        String html = "<p>Olá.</p>"
            + "<p>Alguém entrou no <b>Meu Jet</b> com o Google e informou o seu CPF. "
            + "Se foi você, use o código abaixo para unificar as contas:</p>"
            + "<p style=\"font-size:24px;font-weight:bold;letter-spacing:3px\">" + codigo + "</p>"
            + "<p>Válido por 10 minutos. Se não reconhece esta solicitação, "
            + "ignore este e-mail — nada será alterado.</p>";
        emailService.sendEmail(ownerEmail, "Código para unificar suas contas — Meu Jet", html);

        log.info("OTP de merge por CPF enviado: sub={}, ownerSub={}, destino={}",
            sub, ownerSub, mascararEmail(ownerEmail));
        return new EnvioResultado(true, mascararEmail(ownerEmail), null,
            "Enviamos um código de 6 dígitos para o e-mail da conta que já usa este CPF.");
    }

    /**
     * Confere o OTP e executa o merge: transfere a identidade Google da conta
     * duplicada (sub atual) para a dona do CPF e descarta a duplicata. O
     * chamador deve encerrar a sessão (logout federado) — o próximo login com
     * Google resolve para a conta dona.
     */
    @Transactional
    public MergeResultado verificar(String sub, String cpfInformado, String codigo) {
        String guardado = redis.opsForValue().get(keyCode(sub));
        if (guardado == null) {
            throw new BusinessException("Código expirado ou não solicitado. Envie um novo código.");
        }
        Long tentativas = redis.opsForValue().increment(keyTries(sub));
        if (tentativas != null && tentativas == 1L) {
            redis.expire(keyTries(sub), TTL_CODE);
        }
        if (tentativas != null && tentativas > MAX_TENTATIVAS) {
            redis.delete(keyCode(sub));
            throw new BusinessException("Muitas tentativas. Envie um novo código.");
        }

        String[] partes = guardado.split("\\|", 3);
        String esperado = partes[0];
        String ownerSub = partes[1];
        String cpfDigits = partes[2];
        if (!cpfDigits.equals(soDigitos(cpfInformado))) {
            throw new BusinessException("O CPF informado não confere com o da solicitação. Recomece o processo.");
        }
        if (codigo == null || !codigo.trim().equals(esperado)) {
            return new MergeResultado(false, false, "Código incorreto — confira e tente de novo.");
        }

        // Re-valida o estado (pode ter mudado entre enviar e verificar)
        String donoAtual = resolverDonoDoCpf(cpfDigits);
        boolean semVinculos = customerAccountService.vinculos(sub).isEmpty();
        if (!ownerSub.equals(donoAtual) || !semVinculos
                || userProvisioningService.findFederatedIdentity(sub, idpAlias) == null) {
            redis.delete(keyCode(sub));
            redis.delete(keyTries(sub));
            throw new BusinessException("A situação da conta mudou — recomece o processo.");
        }

        if (!userProvisioningService.transferFederatedIdentity(sub, ownerSub, idpAlias)) {
            throw new InternalServerException(
                "Não foi possível unificar as contas agora — tente novamente em instantes.");
        }

        // Perfil global da duplicata (criado no gate, sem CPF) não serve mais.
        repository.findByProviderAndProviderUserId(PROVIDER, sub).ifPresent(repository::delete);

        // Best-effort: conta órfã sem link Google é inócua (sem senha Google e
        // sem vínculos); não aborta o merge já concluído.
        if (!userProvisioningService.deleteUser(sub)) {
            log.error("Merge concluído mas a conta duplicada não foi removida do provedor: sub={}", sub);
        }

        redis.delete(keyCode(sub));
        redis.delete(keyTries(sub));
        redis.delete(keyCooldown(sub));

        eventPublisher.publishEvent(ContaCpfMergeEvent.of(ownerSub, sub, mascararCpf(cpfDigits)));
        log.info("Contas unificadas por CPF: owner={}, duplicataDescartada={}", ownerSub, sub);
        return new MergeResultado(true, true,
            "Contas unificadas. Saia e entre novamente com o Google.");
    }

    // ---- helpers ----

    /** Dono do CPF: customer_profile primeiro; fallback username do Keycloak (=CPF). */
    private String resolverDonoDoCpf(String cpfDigits) {
        Optional<CustomerProfile> porPerfil = repository.findByCpfNormalizado(cpfDigits);
        if (porPerfil.isPresent()) {
            return porPerfil.get().getProviderUserId();
        }
        return userProvisioningService.findUserIdByUsername(cpfDigits);
    }

    private String keyCode(String sub) { return "otp:cpfmerge:code:" + sub; }
    private String keyTries(String sub) { return "otp:cpfmerge:tries:" + sub; }
    private String keyCooldown(String sub) { return "otp:cpfmerge:cooldown:" + sub; }

    private static String soDigitos(String s) { return s == null ? "" : s.replaceAll("\\D", ""); }

    private static String mascararCpf(String cpfDigits) {
        if (cpfDigits == null || cpfDigits.length() != 11) return "***";
        return "***.***." + cpfDigits.substring(6, 9) + "-" + cpfDigits.substring(9);
    }

    private static String mascararEmail(String email) {
        if (email == null || !email.contains("@")) return "e-mail cadastrado";
        int at = email.indexOf('@');
        String u = email.substring(0, at);
        String dom = email.substring(at);
        String vis = u.length() <= 2 ? u.charAt(0) + "*" : u.substring(0, 2) + "***";
        return vis + dom;
    }
}
