package com.jetski.tenant.api.dto;

import java.util.List;
import java.util.Map;

/**
 * Dry-run do import: o que o zip contém × o que a empresa tem hoje.
 *
 * @param key           chave do zip analisado
 * @param slugNoZip     slug da empresa quando o export foi gerado (manifest)
 * @param slugAtual     slug atual — divergência vira aviso, não bloqueio
 * @param geradoEm      instante do export (manifest)
 * @param linhasNoZip   contagem por tabela importável dentro do zip
 * @param linhasAtuais  contagem atual por tabela (o que será substituído)
 * @param arquivosNoZip objetos do storage dentro do zip
 * @param avisos        divergências não bloqueantes (slug, tabelas puladas…)
 */
public record ImportPreviewDTO(
    String key,
    String slugNoZip,
    String slugAtual,
    String geradoEm,
    Map<String, Long> linhasNoZip,
    Map<String, Long> linhasAtuais,
    int arquivosNoZip,
    List<String> avisos
) {}
