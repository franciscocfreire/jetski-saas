package com.jetski.locacoes.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Como os e-mails da emissão saem: dentro do request (síncrono) ou depois do commit,
 * numa thread própria (assíncrono).
 *
 * <p>Guardada como JSON em {@code plataforma_config} (chave {@code emissao_envio}),
 * no padrão de {@code imagem_compressao} — o super admin liga/desliga pelo console e
 * vale na hora, sem redeploy. Serve de kill switch: se o envio assíncrono der
 * problema, um clique volta ao comportamento antigo.
 *
 * <p>{@code Boolean} (não {@code boolean}) para tolerar JSON gravado por uma versão
 * anterior sem o campo — mesma convenção de {@code DocumentoConfig}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EmissaoEnvioConfig(Boolean assincrono) {

    /** Usa o default vindo da configuração da aplicação quando a chave nunca foi gravada. */
    public static EmissaoEnvioConfig defaults(boolean assincronoPadrao) {
        return new EmissaoEnvioConfig(assincronoPadrao);
    }

    public boolean assincronoOn(boolean padrao) {
        return assincrono == null ? padrao : assincrono;
    }
}
