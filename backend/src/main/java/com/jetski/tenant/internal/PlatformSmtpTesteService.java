package com.jetski.tenant.internal;

import com.jetski.shared.email.SmtpSenderFactory;
import com.jetski.shared.email.TenantSmtpResolver;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.security.TenantContext;
import com.jetski.tenant.domain.Tenant;
import com.jetski.tenant.domain.event.TenantStatusChangedEvent;
import com.jetski.tenant.internal.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Envio de teste pelo SMTP próprio de uma empresa, disparado pelo console da plataforma:
 * sai do servidor da EMPRESA para o e-mail da PLATAFORMA ({@code jetski.email.from}),
 * para o operador conferir na caixa da plataforma que a configuração funciona.
 *
 * <p>Chama o {@link SmtpSenderFactory} direto — o {@code EmailService} é best-effort e
 * engoliria justamente o erro que o operador precisa ver. Sem transação: o SMTP pode
 * levar dezenas de segundos. Todo teste (ok ou falha) fica na trilha da empresa.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformSmtpTesteService {

    static final String ACAO_AUDITORIA = "TENANT_SMTP_TESTE";
    private static final int ERRO_MAX = 500;
    private static final DateTimeFormatter QUANDO =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.of("America/Sao_Paulo"));

    private final TenantRepository tenantRepository;
    private final TenantSmtpResolver tenantSmtpResolver;
    private final SmtpSenderFactory senderFactory;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${jetski.email.from:noreply@pegaojet.com.br}")
    private String emailPlataforma;

    /**
     * @param enviado  o servidor da empresa aceitou a mensagem
     * @param de       remetente usado (o "From" do SMTP da empresa)
     * @param usuario  conta que autenticou no SMTP — é por ela que a mensagem sai de fato
     * @param para     e-mail da plataforma que deve receber o teste
     * @param servidor host:porta do SMTP da empresa
     * @param erro     causa raiz da falha (null quando enviado)
     */
    public record ResultadoTeste(boolean enviado, String de, String usuario, String para, String servidor,
                                 String erro, Instant em) {}

    public ResultadoTeste testar(UUID tenantId) {
        Tenant t = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new NotFoundException("Empresa não encontrada: " + tenantId));
        com.jetski.tenant.TenantQueryService.exigirViva(t);
        TenantSmtpResolver.SmtpSettings s = tenantSmtpResolver.forTenant(tenantId)
            .orElseThrow(() -> new BusinessException("SMTP não cadastrado: a empresa precisa preencher "
                + "host, usuário e senha em Configurações antes do teste"));

        Instant agora = Instant.now();
        String servidor = s.host() + ":" + s.port();
        String erro = null;
        try {
            senderFactory.send(senderFactory.build(s), s.from(), s.fromName(), emailPlataforma,
                "Teste de SMTP — " + t.getRazaoSocial(), corpo(t, s, servidor, agora), null, null, null);
            log.info("Teste de SMTP ok: tenant={}, servidor={}, conta={}, from={}, para={}",
                tenantId, servidor, s.username(), s.from(), emailPlataforma);
        } catch (Exception e) {
            erro = causaRaiz(e);
            log.warn("Teste de SMTP falhou: tenant={}, servidor={}, erro={}", tenantId, servidor, erro);
        }

        String detalhe = (erro == null ? "enviado" : "falhou: " + erro)
            + " — conta " + s.username() + ", de " + s.from() + " via " + servidor + " para " + emailPlataforma;
        eventPublisher.publishEvent(TenantStatusChangedEvent.of(tenantId, ACAO_AUDITORIA,
            t.getStatus().name(), t.getStatus().name(), TenantContext.getUsuarioId(), detalhe,
            t.getRazaoSocial(), t.getSlug()));

        return new ResultadoTeste(erro == null, s.from(), s.username(), emailPlataforma, servidor, erro, agora);
    }

    private static String corpo(Tenant t, TenantSmtpResolver.SmtpSettings s, String servidor, Instant agora) {
        return "<p>E-mail de teste disparado pelo console da plataforma Meu Jet.</p>"
            + "<p>Se esta mensagem chegou, o SMTP próprio de <b>" + HtmlUtils.htmlEscape(t.getRazaoSocial())
            + "</b> está funcionando.</p>"
            + "<ul><li>Servidor: " + HtmlUtils.htmlEscape(servidor) + "</li>"
            + "<li>Conta do SMTP: " + HtmlUtils.htmlEscape(s.username()) + "</li>"
            + "<li>Remetente: " + HtmlUtils.htmlEscape(s.from()) + "</li>"
            + "<li>Enviado em: " + QUANDO.format(agora) + "</li></ul>";
    }

    /** A mensagem que interessa é a da raiz ("Authentication failed"), não o wrapper. */
    private static String causaRaiz(Exception e) {
        Throwable raiz = e;
        while (raiz.getCause() != null && raiz.getCause() != raiz) raiz = raiz.getCause();
        String m = raiz.getMessage() != null ? raiz.getMessage() : raiz.getClass().getSimpleName();
        return m.length() <= ERRO_MAX ? m : m.substring(0, ERRO_MAX);
    }
}
