package com.jetski.dashboard.api.dto;

/**
 * Checklist "primeiros passos" da empresa — cada flag é auto-detectada a partir
 * dos dados reais do tenant (nada é persistido; copy/labels ficam no frontend).
 *
 * @param temModelo               há pelo menos um modelo ativo (pré-requisito de reserva)
 * @param temJetski               há pelo menos um jetski ativo (pré-requisito de check-in)
 * @param temInstrutor            há pelo menos um instrutor ativo (Atestado 5-B-1 da emissão)
 * @param marinhaEmailConfigurado tenant.marinha_email preenchido (envio de EMA à Marinha)
 * @param pixConfigurado          tenant.pix_chave preenchida (sinal PIX do portal)
 * @param equipeConvidada         mais de um membro ativo (além do admin fundador)
 * @param primeiraLocacaoFeita    existe alguma locação registrada
 * @param completo                todos os passos concluídos
 *
 * @author Jetski Team
 */
public record OnboardingChecklistResponse(
    boolean temModelo,
    boolean temJetski,
    boolean temInstrutor,
    boolean marinhaEmailConfigurado,
    boolean pixConfigurado,
    boolean equipeConvidada,
    boolean primeiraLocacaoFeita,
    boolean completo
) {
    public static OnboardingChecklistResponse of(
            boolean temModelo, boolean temJetski, boolean temInstrutor,
            boolean marinhaEmailConfigurado, boolean pixConfigurado,
            boolean equipeConvidada, boolean primeiraLocacaoFeita) {
        boolean completo = temModelo && temJetski && temInstrutor && marinhaEmailConfigurado
            && pixConfigurado && equipeConvidada && primeiraLocacaoFeita;
        return new OnboardingChecklistResponse(temModelo, temJetski, temInstrutor,
            marinhaEmailConfigurado, pixConfigurado, equipeConvidada, primeiraLocacaoFeita, completo);
    }
}
