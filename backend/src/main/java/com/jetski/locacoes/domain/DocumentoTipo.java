package com.jetski.locacoes.domain;

/**
 * Tipo do documento que identifica o locatário.
 *
 * <p>Existe porque a identificação acontece ANTES de o sistema saber qualquer
 * outra coisa sobre a pessoa: no passo 1 do balcão só há o documento na mão do
 * atendente. Sem o tipo, a busca assumia CPF (campo numérico, máscara de CPF) e
 * um estrangeiro simplesmente não era localizável — o {@code estrangeiro} só
 * aparecia três passos depois, no passo Documentos.
 *
 * <p>O tipo decide três coisas: como o valor é normalizado para busca, como o
 * documento é rotulado no ofício à Capitania (NORMAM-212/DPC 5.4.2) e como o
 * PDF anexo é nomeado.
 */
public enum DocumentoTipo {

    CPF(11),
    CNPJ(14),
    /** Alfanumérico, sem tamanho fixo — cada país emite do seu jeito. */
    PASSAPORTE(0);

    private final int digitos;

    DocumentoTipo(int digitos) {
        this.digitos = digitos;
    }

    /** Quantidade exata de dígitos, ou 0 quando o tipo não tem tamanho fixo. */
    public int digitos() {
        return digitos;
    }

    public boolean numerico() {
        return digitos > 0;
    }

    /**
     * Passaporte implica estrangeiro (anexos 5-B em inglês). A recíproca NÃO
     * vale: estrangeiro residente tem CPF e continua precisando dos anexos em
     * inglês — por isso quem consome isto só LIGA a flag, nunca desliga.
     */
    public boolean implicaEstrangeiro() {
        return this == PASSAPORTE;
    }

    /** Rótulo para humanos — cabeçalho do ofício, formulários, telas. */
    public String rotulo() {
        return this == PASSAPORTE ? "Passaporte" : name();
    }

    public static DocumentoTipo deNome(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
