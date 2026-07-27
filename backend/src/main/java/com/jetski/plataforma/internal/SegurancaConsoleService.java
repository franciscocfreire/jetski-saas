package com.jetski.plataforma.internal;

import com.jetski.plataforma.event.SegurancaConsoleAlteradaEvent;
import com.jetski.shared.security.TenantContext;
import com.jetski.shared.security.TrustedDeviceConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Liga/desliga o "lembrar este navegador" no console da plataforma.
 *
 * <p><strong>O que a chave significa.</strong> O 2FA do console vem de um autenticador
 * compartilhado com backoffice e portal — o post-broker do Google é vinculado ao IdP, não ao
 * client. A distinção por aplicação mora na condição {@code mj-trusted-device-check}, que lê
 * uma lista de clients que <em>não</em> honram dispositivo confiável. Com o console nessa
 * lista, todo login é desafiado; fora dela, um navegador marcado como confiável pula o 2FA
 * por 30 dias, como no backoffice.
 *
 * <p><strong>Por que existe uma tela para isto.</strong> Antes só dava para mudar rodando
 * {@code infra/prod/configure-keycloak-console-2fa.sh} com uma variável de ambiente — quem
 * opera a plataforma não tem (nem deveria ter) shell no servidor para uma decisão que é de
 * política de acesso, não de infraestrutura.
 *
 * <p><strong>Alcance.</strong> Vale para quem entra por login social (Google), que é o
 * caminho com post-broker. O login por senha no console tem OTP obrigatório no próprio
 * flow ({@code console-browser-forms}) e não passa por esta condição — a tela diz isso.
 *
 * @since 0.9.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SegurancaConsoleService {

    /** Client OIDC do console — é ele que entra ou sai da lista. */
    public static final String CLIENT_CONSOLE = "jetski-platform-console";

    /**
     * Subflows onde a condição vive. Os dois porque o operador pode chegar pelo
     * post-broker (Google) ou pelo 2FA comum; a decisão precisa ser a mesma nos dois.
     */
    private static final List<String> SUBFLOWS = List.of("post-broker-2fa-cond", "portal-2fa");

    private final TrustedDeviceConfig keycloak;
    private final ApplicationEventPublisher eventos;

    /**
     * @param exigeSempre          true = 2FA a cada login no console
     * @param configurado          false = o realm ainda não tem a condição (script não rodou)
     * @param subflowsConfigurados quantos subflows têm a condição gravada
     */
    public record Estado(boolean exigeSempre, boolean configurado, int subflowsConfigurados) {}

    public Estado consultar() {
        Map<String, String> porSubflow = keycloak.lerClientsSemTrustedDevice(SUBFLOWS);
        if (porSubflow.isEmpty()) {
            // Sem config gravada o SPI aplica o default, que protege o console.
            return new Estado(true, false, 0);
        }
        // Basta um subflow ainda listando o console para o desafio acontecer por ali —
        // reportar "desligado" com um caminho ainda exigindo seria mentira útil a ninguém.
        boolean exige = porSubflow.values().stream().anyMatch(this::contemConsole);
        return new Estado(exige, true, porSubflow.size());
    }

    /**
     * @param exigeSempre true recoloca o console na lista (2FA sempre); false o retira
     * @return estado depois da mudança
     */
    public Estado definir(boolean exigeSempre) {
        Estado antes = consultar();
        String valor = exigeSempre ? CLIENT_CONSOLE : "";
        int alterados = keycloak.definirClientsSemTrustedDevice(SUBFLOWS, valor);

        if (alterados == 0) {
            throw new IllegalStateException(
                "O realm não tem a condição de dispositivo confiável nos flows de 2FA. "
                + "Rode infra/prod/configure-keycloak-console-2fa.sh uma vez para criá-la.");
        }

        // Mudança de política de acesso à plataforma: vai para a trilha GLOBAL, com quem
        // mudou e o valor anterior. Sem isso, "por que o console parou de pedir 2FA?"
        // não teria resposta.
        eventos.publishEvent(new SegurancaConsoleAlteradaEvent(
            TenantContext.getUsuarioId(), antes.exigeSempre(), exigeSempre));

        log.warn("[SEGURANCA] 2FA sempre no console: {} -> {} (subflows alterados={})",
            antes.exigeSempre(), exigeSempre, alterados);
        return new Estado(exigeSempre, true, alterados);
    }

    private boolean contemConsole(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            return false;
        }
        for (String c : bruto.split(",")) {
            if (c.trim().equals(CLIENT_CONSOLE)) {
                return true;
            }
        }
        return false;
    }
}
