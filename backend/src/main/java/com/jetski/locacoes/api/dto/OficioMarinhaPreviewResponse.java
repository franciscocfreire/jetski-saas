package com.jetski.locacoes.api.dto;

import lombok.Builder;

import java.util.List;

/**
 * Como o e-mail à Capitania vai sair, com um locatário fictício. Espelha o envelope
 * (De/Para/Responder-para/Assunto/Anexo) e o corpo HTML exatamente como o
 * {@code MarinhaEmailTemplate} produz na emissão, mais a lista de pendências que o
 * operador consegue resolver na própria tela de configurações.
 */
@Builder
public record OficioMarinhaPreviewResponse(
    /** Remetente exibido: {@code Nome <e-mail>}. */
    String de,
    /** {@code SMTP_PROPRIO} (from real da empresa) ou {@code PLATAFORMA} (remetente padrão). */
    String deOrigem,
    /** Destinatário (e-mail da Marinha/Capitania) — {@code null} quando não configurado. */
    String para,
    /** Cabeçalho Reply-To — {@code null} quando o e-mail oficial não foi informado. */
    String responderPara,
    String assunto,
    String nomeAnexo,
    String corpoHtml,
    /** Documentos listados no corpo (o recorte parametrizado em "Documentos"). */
    List<String> anexos,
    List<Aviso> avisos,
    /** {@code true} quando algum aviso é bloqueante (o envio não aconteceria). */
    boolean bloqueado
) {
    /** {@code nivel}: {@code ERRO} (não envia), {@code AVISO} (envia, mas incompleto) ou {@code INFO}. */
    public record Aviso(String nivel, String campo, String mensagem) {}
}
