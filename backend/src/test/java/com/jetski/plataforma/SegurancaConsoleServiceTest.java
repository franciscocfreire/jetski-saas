package com.jetski.plataforma;

import com.jetski.plataforma.event.SegurancaConsoleAlteradaEvent;
import com.jetski.plataforma.internal.SegurancaConsoleService;
import com.jetski.shared.security.TrustedDeviceConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Toggle de 2FA do console.
 *
 * <p>O que estes testes travam é a tradução entre duas linguagens que se invertem: a UI
 * pergunta "exigir 2FA sempre?" e o Keycloak guarda "quais clients NÃO honram dispositivo
 * confiável". Ler isso ao contrário desligaria o 2FA achando que estava ligando.
 */
@DisplayName("Política de 2FA do console")
class SegurancaConsoleServiceTest {

    private static final String CONSOLE = SegurancaConsoleService.CLIENT_CONSOLE;

    private final TrustedDeviceConfig keycloak = mock(TrustedDeviceConfig.class);
    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
    private final SegurancaConsoleService service = new SegurancaConsoleService(keycloak, eventos);

    private void configNoRealm(Map<String, String> porSubflow) {
        when(keycloak.lerClientsSemTrustedDevice(anyList())).thenReturn(porSubflow);
    }

    @Test
    @DisplayName("Console na lista = 2FA a cada login")
    void consoleNaListaExigeSempre() {
        configNoRealm(Map.of("post-broker-2fa-cond", CONSOLE, "portal-2fa", CONSOLE));

        var estado = service.consultar();

        assertThat(estado.exigeSempre()).isTrue();
        assertThat(estado.configurado()).isTrue();
    }

    @Test
    @DisplayName("Lista vazia = navegador confiável dispensa o desafio")
    void listaVaziaHonraDispositivo() {
        configNoRealm(Map.of("post-broker-2fa-cond", "", "portal-2fa", ""));

        assertThat(service.consultar().exigeSempre()).isFalse();
    }

    /**
     * Um caminho ainda exigindo é exigência de verdade para quem passa por ele. Reportar
     * "desligado" porque o outro subflow já foi limpo esconderia justamente a inconsistência.
     */
    @Test
    @DisplayName("Um subflow ainda listando o console já conta como exige sempre")
    void bastaUmSubflowListando() {
        var parcial = new LinkedHashMap<String, String>();
        parcial.put("post-broker-2fa-cond", "");
        parcial.put("portal-2fa", CONSOLE);
        configNoRealm(parcial);

        assertThat(service.consultar().exigeSempre()).isTrue();
    }

    @Test
    @DisplayName("Realm sem a condição: reporta não configurado e assume o padrão seguro")
    void semCondicaoAssumeSeguro() {
        configNoRealm(Map.of());

        var estado = service.consultar();

        assertThat(estado.exigeSempre()).as("o SPI aplica o default, que protege o console").isTrue();
        assertThat(estado.configurado()).isFalse();
    }

    @Test
    @DisplayName("Desligar retira o console da lista; ligar recoloca")
    void gravaOValorCerto() {
        configNoRealm(Map.of("portal-2fa", CONSOLE));
        when(keycloak.definirClientsSemTrustedDevice(anyList(), any())).thenReturn(2);

        service.definir(false);
        verify(keycloak).definirClientsSemTrustedDevice(anyList(), eq(""));

        service.definir(true);
        verify(keycloak).definirClientsSemTrustedDevice(anyList(), eq(CONSOLE));
    }

    @Test
    @DisplayName("Mudança vai para a trilha com o valor anterior")
    void publicaEventoDeAuditoria() {
        configNoRealm(Map.of("portal-2fa", CONSOLE));
        when(keycloak.definirClientsSemTrustedDevice(anyList(), any())).thenReturn(2);

        service.definir(false);

        var evento = org.mockito.ArgumentCaptor.forClass(SegurancaConsoleAlteradaEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().antes()).isTrue();
        assertThat(evento.getValue().depois()).isFalse();
    }

    /**
     * Sem a condição no realm, gravar não faz nada — e devolver 200 nesse caso faria a tela
     * afirmar que o 2FA foi afrouxado quando nada mudou.
     */
    @Test
    @DisplayName("Nenhum subflow alterado vira erro, não sucesso silencioso")
    void nadaAlteradoFalha() {
        configNoRealm(Map.of());
        when(keycloak.definirClientsSemTrustedDevice(anyList(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.definir(false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("configure-keycloak-console-2fa.sh");
        verify(eventos, org.mockito.Mockito.never()).publishEvent(any(SegurancaConsoleAlteradaEvent.class));
    }

    @Test
    @DisplayName("Os dois caminhos de 2FA são configurados juntos")
    void mexeNosDoisSubflows() {
        configNoRealm(Map.of("portal-2fa", CONSOLE));
        when(keycloak.definirClientsSemTrustedDevice(anyList(), any())).thenReturn(2);

        service.definir(false);

        var subflows = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(keycloak).definirClientsSemTrustedDevice(subflows.capture(), any());
        assertThat(subflows.getValue())
            .as("Google e senha precisam decidir igual")
            .containsExactlyInAnyOrder("post-broker-2fa-cond", "portal-2fa");
    }
}
