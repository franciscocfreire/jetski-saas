package com.jetski.locacoes.internal;

import com.jetski.locacoes.api.dto.OficioMarinhaPreviewRequest;
import com.jetski.locacoes.api.dto.OficioMarinhaPreviewResponse;
import com.jetski.shared.email.TenantSmtpResolver;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.tenant.TenantQueryService;
import com.jetski.tenant.domain.DocumentoConfig;
import com.jetski.tenant.domain.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Pré-visualização do ofício à Capitania na tela de configurações: o que está
 * digitado manda sobre o gravado, o envelope reflete SMTP próprio × plataforma e as
 * pendências apontam o que impede ou empobrece o envio real.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OficioMarinhaPreviewService (pré-visualização do e-mail à Capitania)")
class OficioMarinhaPreviewServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock private TenantQueryService tenantQueryService;
    @Mock private TenantSmtpResolver tenantSmtpResolver;

    private OficioMarinhaPreviewService service;

    @BeforeEach
    void setUp() {
        service = new OficioMarinhaPreviewService(tenantQueryService, tenantSmtpResolver);
        ReflectionTestUtils.setField(service, "plataformaFromEmail", "noreply@meujet.com.br");
        ReflectionTestUtils.setField(service, "plataformaFromName", "Meu Jet");
    }

    private Tenant tenantCompleto() {
        return Tenant.builder()
            .id(TENANT)
            .razaoSocial("Jet Save Turismo Náutico LTDA")
            .cnpj("65.455.888/0001-00")
            .eamaRegistro("Portaria 12/2026")
            .responsavelNome("Maria da Silva")
            .telefone("(24) 3333-4444")
            .emailOficial("eama@jetsave.com.br")
            .marinhaEmail("cpsp.secom@marinha.mil.br")
            .documentoConfig(DocumentoConfig.padrao())
            .build();
    }

    @Test
    @DisplayName("com tudo gravado e SMTP próprio: envelope completo, sem pendências, corpo do template")
    void completoComSmtpProprio() {
        when(tenantQueryService.findById(TENANT)).thenReturn(tenantCompleto());
        when(tenantSmtpResolver.forCurrentTenant()).thenReturn(Optional.of(
            new TenantSmtpResolver.SmtpSettings("smtp.gmail.com", 587, "eama@jetsave.com.br", "x",
                "eama@jetsave.com.br", "Jet Save", true)));

        OficioMarinhaPreviewResponse r = service.preview(TENANT, null);

        assertThat(r.de()).isEqualTo("Jet Save <eama@jetsave.com.br>");
        assertThat(r.deOrigem()).isEqualTo("SMTP_PROPRIO");
        assertThat(r.para()).isEqualTo("cpsp.secom@marinha.mil.br");
        assertThat(r.responderPara()).isEqualTo("eama@jetsave.com.br");
        assertThat(r.assunto())
            .isEqualTo("Solicitação de Emissão de CHA-MTA-E – Locatário de Exemplo – CPF 000.000.000-00 – reserva #00000000");
        assertThat(r.nomeAnexo()).isEqualTo("Locatário de Exemplo 00000000000.pdf");
        assertThat(r.corpoHtml())
            .contains("<b>Jet Save Turismo Náutico LTDA</b>")
            .contains("credenciamento nº Portaria 12/2026")
            .contains("<b>Maria da Silva</b>")
            .contains("Telefone: (24) 3333-4444")
            .contains("E-mail: eama@jetsave.com.br")
            .contains("Anexo 5-C")
            .contains("Anexo 5-B (5-B-1 e 5-B-2)");
        assertThat(r.anexos()).isNotEmpty();
        assertThat(r.avisos()).isEmpty();
        assertThat(r.bloqueado()).isFalse();
    }

    @Test
    @DisplayName("o que está digitado manda sobre o gravado; campo limpo some da assinatura")
    void digitadoPrevaleceSobreGravado() {
        when(tenantQueryService.findById(TENANT)).thenReturn(tenantCompleto());
        when(tenantSmtpResolver.forCurrentTenant()).thenReturn(Optional.empty());

        OficioMarinhaPreviewResponse r = service.preview(TENANT, OficioMarinhaPreviewRequest.builder()
            .responsavelNome("João Novo")
            .telefone("")                 // limpou o campo
            .emailOficial("novo@jetsave.com.br")
            .build());

        assertThat(r.corpoHtml())
            .contains("<b>João Novo</b>")
            .doesNotContain("Maria da Silva")
            .doesNotContain("Telefone:")
            .contains("E-mail: novo@jetsave.com.br");
        assertThat(r.responderPara()).isEqualTo("novo@jetsave.com.br");
        // razão social e e-mail da Marinha não vieram no request: valem os gravados
        assertThat(r.para()).isEqualTo("cpsp.secom@marinha.mil.br");
        assertThat(r.corpoHtml()).contains("Jet Save Turismo Náutico LTDA");
        assertThat(r.avisos()).extracting(OficioMarinhaPreviewResponse.Aviso::campo)
            .containsExactlyInAnyOrder("telefone", "smtp");
    }

    @Test
    @DisplayName("sem e-mail da Marinha: pendência ERRO e preview bloqueado; sem SMTP: remetente da plataforma")
    void semDestinatarioBloqueia() {
        Tenant t = tenantCompleto();
        t.setMarinhaEmail(null);
        t.setEamaRegistro(null);
        when(tenantQueryService.findById(TENANT)).thenReturn(t);
        when(tenantSmtpResolver.forCurrentTenant()).thenReturn(Optional.empty());

        OficioMarinhaPreviewResponse r = service.preview(TENANT, new OficioMarinhaPreviewRequest());

        assertThat(r.de()).isEqualTo("Meu Jet <noreply@meujet.com.br>");
        assertThat(r.deOrigem()).isEqualTo("PLATAFORMA");
        assertThat(r.para()).isNull();
        assertThat(r.bloqueado()).isTrue();
        assertThat(r.avisos()).extracting(OficioMarinhaPreviewResponse.Aviso::campo)
            .containsExactlyInAnyOrder("marinhaEmail", "eamaRegistro", "smtp");
        assertThat(r.avisos()).filteredOn(a -> a.campo().equals("marinhaEmail"))
            .extracting(OficioMarinhaPreviewResponse.Aviso::nivel).containsExactly("ERRO");
        // sem SMTP próprio o ofício não sai (nunca pela plataforma): também bloqueia
        assertThat(r.avisos()).filteredOn(a -> a.campo().equals("smtp"))
            .extracting(OficioMarinhaPreviewResponse.Aviso::nivel).containsExactly("ERRO");
        assertThat(r.corpoHtml()).doesNotContain("credenciamento nº");
    }

    @Test
    @DisplayName("SMTP próprio com remetente diferente do e-mail oficial: avisa a divergência")
    void smtpDivergeDoEmailOficial() {
        when(tenantQueryService.findById(TENANT)).thenReturn(tenantCompleto());
        when(tenantSmtpResolver.forCurrentTenant()).thenReturn(Optional.of(
            new TenantSmtpResolver.SmtpSettings("smtp.gmail.com", 587, "outro@gmail.com", "x",
                "outro@gmail.com", null, true)));

        OficioMarinhaPreviewResponse r = service.preview(TENANT, null);

        assertThat(r.de()).isEqualTo("Meu Jet <outro@gmail.com>"); // sem fromName → nome da plataforma
        assertThat(r.avisos()).extracting(OficioMarinhaPreviewResponse.Aviso::campo).containsExactly("smtp");
        assertThat(r.avisos().get(0).mensagem()).contains("outro@gmail.com").contains("eama@jetsave.com.br");
        assertThat(r.bloqueado()).isFalse();
    }

    @Test
    @DisplayName("tenant inexistente → 404")
    void tenantInexistente() {
        when(tenantQueryService.findById(TENANT)).thenReturn(null);
        assertThatThrownBy(() -> service.preview(TENANT, null)).isInstanceOf(NotFoundException.class);
    }
}
