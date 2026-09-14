package com.jetski.locacoes.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Telefones - exibição de telefone brasileiro")
class TelefonesTest {

    @Test
    @DisplayName("celular e fixo saem mascarados, venham com máscara, só dígitos ou +55")
    void formataBrasileiro() {
        assertThat(Telefones.formatar("11955868220")).isEqualTo("(11) 95586-8220");
        assertThat(Telefones.formatar("+5511955868220")).isEqualTo("(11) 95586-8220");
        assertThat(Telefones.formatar("(13) 3000-1000")).isEqualTo("(13) 3000-1000");
        assertThat(Telefones.formatar("1130001000")).isEqualTo("(11) 3000-1000");
        assertThat(Telefones.formatar(" 11 95586 8220 ")).isEqualTo("(11) 95586-8220");
    }

    @Test
    @DisplayName("estrangeiro, incompleto e vazio voltam como vieram")
    void naoInventaFormato() {
        assertThat(Telefones.formatar("+1 415 555 0100")).isEqualTo("+1 415 555 0100");
        assertThat(Telefones.formatar("12345")).isEqualTo("12345");
        assertThat(Telefones.formatar("")).isEmpty();
        assertThat(Telefones.formatar(null)).isNull();
    }
}
