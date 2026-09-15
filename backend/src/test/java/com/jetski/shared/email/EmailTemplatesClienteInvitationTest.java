package com.jetski.shared.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmailTemplates - convite de ativação do cliente")
class EmailTemplatesClienteInvitationTest {

    @Test
    @DisplayName("com a empresa: assunto e corpo dizem quem criou o cadastro (nome com escape)")
    void dizQualEmpresaCriouACadastro() {
        assertThat(EmailTemplates.clienteInvitationSubject("Francisco Freire LTDA"))
            .isEqualTo("Ative sua conta no Meu Jet — cadastro feito por Francisco Freire LTDA");

        String html = EmailTemplates.clienteInvitationHtml("Reinaldo", "https://cliente/ativar?token=t", "Senha#1",
            "Jet & Cia <Praia>");
        assertThat(html)
            .contains("A empresa <strong>Jet &amp; Cia &lt;Praia&gt;</strong> criou um cadastro para você")
            .doesNotContain("A loja criou")
            .contains("Reinaldo").contains("Senha#1").contains("https://cliente/ativar?token=t");
    }

    @Test
    @DisplayName("sem a empresa: volta ao texto genérico")
    void semEmpresaTextoGenerico() {
        assertThat(EmailTemplates.clienteInvitationSubject(null)).isEqualTo("Ative sua conta no Meu Jet");
        assertThat(EmailTemplates.clienteInvitationSubject("  ")).isEqualTo("Ative sua conta no Meu Jet");
        assertThat(EmailTemplates.clienteInvitationHtml("Reinaldo", "link", "senha", null))
            .contains("A loja criou um cadastro para você");
    }
}
