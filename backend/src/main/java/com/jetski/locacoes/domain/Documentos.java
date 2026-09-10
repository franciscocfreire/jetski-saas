package com.jetski.locacoes.domain;

/**
 * Normalização e formatação do documento de identificação.
 *
 * <p><b>O valor é guardado normalizado; a formatação é assunto de apresentação.</b>
 * Antes da V066 o documento era texto livre e a busca comparava a string crua:
 * {@code "847.215.903-50"} e {@code "84721590350"} eram duas pessoas diferentes.
 * Cada operador que digitasse noutro formato criava uma ficha nova — e a trava
 * anti-takeover do {@code criarPreConta}, que usa o mesmo match, era contornada
 * só trocando a pontuação.
 *
 * <p>Toda entrada e toda busca passam por {@link #normalizar}; quem exibe chama
 * {@link #formatar}.
 */
public final class Documentos {

    private Documentos() {
    }

    /**
     * Valor canônico para gravar e comparar: dígitos (CPF/CNPJ) ou alfanumérico
     * maiúsculo (passaporte). Sem tipo, cai na inferência de {@link #inferirTipo}.
     *
     * @return {@code null} quando não há nada aproveitável
     */
    public static String normalizar(DocumentoTipo tipo, String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        DocumentoTipo efetivo = tipo != null ? tipo : inferirTipo(valor);
        String limpo = efetivo == DocumentoTipo.PASSAPORTE
            ? valor.replaceAll("[^A-Za-z0-9]", "").toUpperCase()
            : valor.replaceAll("[^0-9]", "");
        return limpo.isBlank() ? null : limpo;
    }

    /** Atalho para quando o tipo ainda não é conhecido (busca livre). */
    public static String normalizar(String valor) {
        return normalizar(null, valor);
    }

    /**
     * Deduz o tipo pelo formato do valor.
     *
     * <p>Letra em qualquer posição só pode ser passaporte. Só dígitos: 11 é CPF,
     * 14 é CNPJ. Qualquer outra contagem é <b>documento malformado</b>, e aí
     * devolvemos {@code null} em vez de chutar: chutar PASSAPORTE marcaria a
     * pessoa como estrangeira e o ofício à Capitania sairia com os anexos 5-B em
     * inglês e o rótulo errado — foi o que a V066 pegou num CPF de 10 dígitos.
     */
    public static DocumentoTipo inferirTipo(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        if (valor.matches(".*[A-Za-z].*")) {
            return DocumentoTipo.PASSAPORTE;
        }
        String digitos = valor.replaceAll("[^0-9]", "");
        return switch (digitos.length()) {
            case 11 -> DocumentoTipo.CPF;
            case 14 -> DocumentoTipo.CNPJ;
            default -> null;
        };
    }

    /**
     * Devolve o valor pontuado para leitura humana — tela, PDF, corpo do ofício.
     * Passaporte e documento fora do formato saem como estão.
     */
    public static String formatar(DocumentoTipo tipo, String valor) {
        String limpo = normalizar(tipo, valor);
        if (limpo == null) {
            return null;
        }
        DocumentoTipo efetivo = tipo != null ? tipo : inferirTipo(limpo);
        if (efetivo == DocumentoTipo.CPF && limpo.length() == 11) {
            return limpo.substring(0, 3) + "." + limpo.substring(3, 6) + "."
                 + limpo.substring(6, 9) + "-" + limpo.substring(9);
        }
        if (efetivo == DocumentoTipo.CNPJ && limpo.length() == 14) {
            return limpo.substring(0, 2) + "." + limpo.substring(2, 5) + "."
                 + limpo.substring(5, 8) + "/" + limpo.substring(8, 12) + "-" + limpo.substring(12);
        }
        return limpo;
    }

    public static String formatar(String valor) {
        return formatar(null, valor);
    }
}
