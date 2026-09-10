package com.jetski.locacoes.internal;

/**
 * Nome de arquivo dos PDFs entregues ao operador e ao cliente.
 *
 * <p>Uma convenção só para o sistema inteiro: <b>nome completo + CPF</b> (passaporte,
 * para estrangeiro). A regra nasceu como exigência da NORMAM-212/DPC item 5.4.2 para o
 * anexo do ofício à Capitania e foi promovida a padrão — antes cada caminho inventava o
 * seu ({@code documento.pdf} no link, {@code documento-1478104e.pdf} no download direto,
 * {@code documentos.pdf} no e-mail ao cliente), e quem abria o PDF não sabia de quem era.
 */
public final class DocumentoNome {

    private DocumentoNome() {
    }

    /** {@code "FELIPE H M 49811586888.pdf"}. Prefixo opcional distingue ficha/prévia. */
    public static String de(String prefixo, String nome, String documento) {
        String base = (nz(nome, "Locatario") + " " + nz(documento, "")).trim();
        if (prefixo != null && !prefixo.isBlank()) {
            base = prefixo.trim() + " " + base;
        }
        return sanitizar(base) + ".pdf";
    }

    public static String de(String nome, String documento) {
        return de(null, nome, documento);
    }

    /**
     * Remove separadores de caminho e caracteres proibidos em nome de arquivo.
     * Acentos ficam — o Content-Disposition os carrega em UTF-8 (RFC 5987).
     */
    static String sanitizar(String base) {
        return base.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "").replaceAll("\\s+", " ").trim();
    }

    private static String nz(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }
}
