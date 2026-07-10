package com.jetski.tenant.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Config de compressão de imagem por tipo de documento — parametrizada pelo
 * super admin (global à plataforma), lida pelo navegador antes do upload.
 * A assinatura (PNG line-art) NÃO entra aqui: não é comprimida como JPEG.
 *
 * <p>Persistida como JSON no {@code plataforma_config.valor} (chave
 * {@code imagem_compressao}). Tipos previstos: IDENTIDADE, COMPROVANTE_RESIDENCIA,
 * SELFIE, CHA, GRU_COMPROVANTE.
 *
 * @param tipos preset por tipo de documento
 */
public record ImagemCompressaoConfig(
    @Valid Map<String, Preset> tipos
) {
    /**
     * @param maxDimensao lado maior (px) para o qual a imagem é reduzida antes do envio
     * @param qualidade   qualidade JPEG (0.3–1.0)
     */
    public record Preset(
        @Min(400) @Max(4000) int maxDimensao,
        @DecimalMin("0.3") @DecimalMax("1.0") double qualidade
    ) {}

    /** Defaults sensatos: documentos conservadores (Marinha), selfie mais leve. */
    public static ImagemCompressaoConfig defaults() {
        Map<String, Preset> t = new LinkedHashMap<>();
        t.put("IDENTIDADE", new Preset(2000, 0.85));
        t.put("COMPROVANTE_RESIDENCIA", new Preset(2000, 0.85));
        t.put("CHA", new Preset(2000, 0.85));
        t.put("GRU_COMPROVANTE", new Preset(2000, 0.85));
        t.put("SELFIE", new Preset(1280, 0.80));
        return new ImagemCompressaoConfig(t);
    }
}
