package com.jetski.locacoes.internal;

import com.jetski.locacoes.api.dto.OficioMarinhaPreviewRequest;
import com.jetski.locacoes.api.dto.OficioMarinhaPreviewResponse;
import com.jetski.locacoes.api.dto.OficioMarinhaPreviewResponse.Aviso;
import com.jetski.locacoes.domain.DocumentoTipo;
import com.jetski.shared.email.TenantSmtpResolver;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.tenant.TenantQueryService;
import com.jetski.tenant.domain.DocumentoConfig;
import com.jetski.tenant.domain.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pré-visualização do ofício à Capitania (NORMAM-212 5.4.2) para a tela de
 * configurações: monta o MESMO e-mail da emissão ({@link MarinhaEmailTemplate}) com um
 * locatário fictício, usando os valores que o operador está digitando por cima do que
 * está gravado. Não envia, não persiste, não debita crédito.
 *
 * <p>Além do envelope e do corpo, devolve as pendências que só se descobrem na hora
 * do envio (sem e-mail da Marinha o documento fica {@code SEM_DESTINATARIO}; sem SMTP
 * próprio o e-mail sai da plataforma, o que a NORMAM não aceita como remetente) — a
 * pré-visualização é o lugar de ver isso ANTES da primeira emissão real.
 */
@Service
@RequiredArgsConstructor
public class OficioMarinhaPreviewService {

    /** Locatário fictício — obviamente de exemplo, para ninguém confundir com um envio real. */
    static final String LOCATARIO_EXEMPLO = "Locatário de Exemplo";
    static final String CPF_EXEMPLO = "00000000000";
    static final String GRU_EXEMPLO = "000000000000000000";
    static final UUID RESERVA_EXEMPLO = UUID.fromString("00000000-0000-0000-0000-000000000000");
    /** SHA-256 de um conteúdo vazio: um hash real, e reconhecível como exemplo. */
    static final String HASH_EXEMPLO = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private final TenantQueryService tenantQueryService;
    private final TenantSmtpResolver tenantSmtpResolver;

    @Value("${jetski.email.from:noreply@pegaojet.com.br}")
    private String plataformaFromEmail;

    @Value("${jetski.email.from-name:Meu Jet}")
    private String plataformaFromName;

    @Transactional(readOnly = true)
    public OficioMarinhaPreviewResponse preview(UUID tenantId, OficioMarinhaPreviewRequest req) {
        Tenant tenant = tenantQueryService.findById(tenantId);
        if (tenant == null) {
            throw new NotFoundException("Tenant não encontrado: " + tenantId);
        }
        OficioMarinhaPreviewRequest r = req != null ? req : new OficioMarinhaPreviewRequest();

        String razaoSocial = ou(r.getRazaoSocial(), tenant.getRazaoSocial());
        String marinhaEmail = ou(r.getMarinhaEmail(), tenant.getMarinhaEmail());
        String responsavel = ou(r.getResponsavelNome(), tenant.getResponsavelNome());
        String telefone = ou(r.getTelefone(), tenant.getTelefone());
        String emailOficial = ou(r.getEmailOficial(), tenant.getEmailOficial());

        DocumentoConfig cfg = DocumentoEnvioService.configDocumento(tenant);
        List<String> anexos = DocumentoEnvioService.rotulosAnexosOficio(
            cfg.marinha(), false, true, true, tipo -> true);

        MarinhaEmailTemplate.DadosOficio oficio = new MarinhaEmailTemplate.DadosOficio(
            razaoSocial, tenant.getCnpj(), tenant.getEamaRegistro(), responsavel, telefone, emailOficial,
            LOCATARIO_EXEMPLO, CPF_EXEMPLO, DocumentoTipo.CPF, false,
            GRU_EXEMPLO, RESERVA_EXEMPLO, anexos, HASH_EXEMPLO, false);

        Optional<TenantSmtpResolver.SmtpSettings> smtp = tenantSmtpResolver.forCurrentTenant();
        String de;
        String deOrigem;
        if (smtp.isPresent()) {
            TenantSmtpResolver.SmtpSettings s = smtp.get();
            String nome = has(s.fromName()) ? s.fromName() : plataformaFromName;
            de = nome + " <" + s.from() + ">";
            deOrigem = "SMTP_PROPRIO";
        } else {
            de = plataformaFromName + " <" + plataformaFromEmail + ">";
            deOrigem = "PLATAFORMA";
        }

        List<Aviso> avisos = avisos(tenant, marinhaEmail, responsavel, telefone, emailOficial, smtp, anexos);
        boolean bloqueado = avisos.stream().anyMatch(a -> "ERRO".equals(a.nivel()));

        return OficioMarinhaPreviewResponse.builder()
            .de(de)
            .deOrigem(deOrigem)
            .para(has(marinhaEmail) ? marinhaEmail.trim() : null)
            .responderPara(has(emailOficial) ? emailOficial.trim() : null)
            .assunto(MarinhaEmailTemplate.assunto(oficio))
            .nomeAnexo(MarinhaEmailTemplate.nomeArquivo(oficio))
            .corpoHtml(MarinhaEmailTemplate.corpoHtml(oficio))
            .anexos(anexos)
            .avisos(avisos)
            .bloqueado(bloqueado)
            .build();
    }

    private List<Aviso> avisos(Tenant tenant, String marinhaEmail, String responsavel, String telefone,
                               String emailOficial, Optional<TenantSmtpResolver.SmtpSettings> smtp,
                               List<String> anexos) {
        List<Aviso> a = new ArrayList<>();
        if (!has(marinhaEmail)) {
            a.add(new Aviso("ERRO", "marinhaEmail",
                "E-mail da Marinha (Capitania) não informado: a emissão fica como \"sem destinatário\" "
                + "e nada é enviado à Capitania."));
        }
        if (!has(responsavel)) {
            a.add(new Aviso("AVISO", "responsavelNome",
                "Responsável pelo EAMA em branco: a assinatura sai sem o nome de quem responde pelo ofício."));
        }
        if (!has(telefone)) {
            a.add(new Aviso("AVISO", "telefone", "Telefone em branco: omitido da assinatura."));
        }
        if (!has(emailOficial)) {
            a.add(new Aviso("AVISO", "emailOficial",
                "E-mail oficial (Anexo 5-A) em branco: o ofício sai sem \"responder-para\" e a "
                + "Capitania responderia ao remetente."));
        }
        if (!has(tenant.getEamaRegistro())) {
            a.add(new Aviso("AVISO", "eamaRegistro",
                "Nº de credenciamento do EAMA não cadastrado (aba Emissão): omitido do ofício."));
        }
        if (smtp.isEmpty()) {
            a.add(new Aviso("AVISO", "smtp",
                "Sem SMTP próprio: o e-mail sai do remetente da plataforma. A NORMAM-212 exige o envio "
                + "pelo e-mail do EAMA declarado no Anexo 5-A — configure o servidor de e-mail abaixo."));
        } else if (has(emailOficial) && !emailOficial.trim().equalsIgnoreCase(smtp.get().from().trim())) {
            a.add(new Aviso("AVISO", "smtp",
                "O remetente do SMTP próprio (" + smtp.get().from() + ") é diferente do e-mail oficial "
                + "do Anexo 5-A (" + emailOficial.trim() + "). A Capitania confere o remetente."));
        }
        if (anexos.isEmpty()) {
            a.add(new Aviso("AVISO", "documentos",
                "Nenhum documento marcado para a Marinha na aba Documentos: o ofício sairia sem a lista do 5.4.2-a."));
        }
        return a;
    }

    /** Valor digitado (mesmo em branco) manda; {@code null} = campo não enviado → valor gravado. */
    private static String ou(String digitado, String gravado) {
        return digitado != null ? digitado : gravado;
    }

    private static boolean has(String s) {
        return s != null && !s.isBlank();
    }
}
