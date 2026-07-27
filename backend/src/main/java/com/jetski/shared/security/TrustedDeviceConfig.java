package com.jetski.shared.security;

import java.util.List;
import java.util.Map;

/**
 * Leitura e escrita da lista de clients que <strong>não</strong> honram dispositivo
 * confiável, gravada na condição {@code mj-trusted-device-check} do Keycloak.
 *
 * <p>Mesma inversão de {@link TenantAccessValidator} e {@link SessaoSuporteValidator}: o
 * módulo {@code shared} publica o contrato e o adaptador do Keycloak (que vive em
 * {@code shared.internal}) implementa. Sem isto, quem precisa da capacidade teria de
 * importar o {@code KeycloakAdminService} inteiro — e expor a classe que cria usuário,
 * troca senha e remove credencial para conseguir mexer numa configuração de flow é preço
 * alto demais. O {@code ModuleStructureTest} cobra isso.
 *
 * @since 0.9.0
 */
public interface TrustedDeviceConfig {

    /**
     * @param subflows aliases dos subflows de 2FA a inspecionar
     * @return alias do subflow → conteúdo bruto da chave; subflow sem a condição (ou sem
     *         config gravada) não aparece no mapa
     */
    Map<String, String> lerClientsSemTrustedDevice(List<String> subflows);

    /**
     * @param subflows aliases dos subflows a alterar
     * @param clients  lista separada por vírgula; vazio = todos os clients honram
     * @return quantos subflows foram efetivamente alterados
     */
    int definirClientsSemTrustedDevice(List<String> subflows, String clients);
}
