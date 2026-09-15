package com.jetski.tenant.internal;

import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.security.TenantContext;
import com.jetski.tenant.domain.Tenant;
import com.jetski.tenant.domain.event.TenantStatusChangedEvent;
import com.jetski.tenant.internal.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Cadastro da empresa editado pelo console da plataforma.
 *
 * <p>Campos cadastrais e de contato: razão social, CNPJ, responsável, telefone, WhatsApp,
 * e-mail oficial, cidade e UF. Configuração operacional (SMTP, PIX, perfil de emissão,
 * branding) continua sendo da própria empresa.
 *
 * <p>O <b>slug não é editável</b> (decisão de produto): é o endereço da vitrine
 * ({@code {slug}.meujet.com.br}), a URL {@code /loja/{slug}} já divulgada e a confirmação
 * digitada de reset/exclusão.
 *
 * <p>Toda alteração exige motivo e grava a diferença campo a campo na trilha da empresa
 * ({@code TENANT_CADASTRO_ALTERADO}). {@code tenant} com RLS da V042 libera a leitura e
 * escrita sem tenant na sessão para o operador irrestrito — mesmo caminho do
 * {@code PlatformLimiteService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformCadastroService {

    private static final Set<String> UFS = Set.of(
        "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA",
        "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final TenantRepository tenantRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** Cadastro exibido no console. {@code slug} é só leitura. */
    public record CadastroEmpresa(
        String slug, String razaoSocial, String cnpj, String responsavelNome, String telefone,
        String whatsapp, String emailOficial, String cidade, String uf) {}

    /**
     * Novo cadastro (substitui os campos; vazio = limpar, exceto razão social).
     *
     * @param motivo obrigatório — vai para a auditoria
     */
    public record AlteracaoCadastro(
        String razaoSocial, String cnpj, String responsavelNome, String telefone,
        String whatsapp, String emailOficial, String cidade, String uf, String motivo) {}

    @Transactional(readOnly = true)
    public CadastroEmpresa ver(UUID tenantId) {
        return cadastro(buscar(tenantId));
    }

    @Transactional
    public CadastroEmpresa alterar(UUID tenantId, AlteracaoCadastro req) {
        com.jetski.tenant.TenantQueryService.exigirViva(buscar(tenantId));
        if (req == null) {
            throw new BusinessException("Informe os dados do cadastro.");
        }
        String motivo = aparar(req.motivo());
        if (motivo == null) {
            throw new BusinessException("Informe o motivo da alteração (fica registrado na auditoria).");
        }
        String razaoSocial = tamanho(aparar(req.razaoSocial()), 200, "Razão social");
        if (razaoSocial == null) {
            throw new BusinessException("Informe a razão social.");
        }
        String cnpj = cnpj(req.cnpj());
        String responsavel = tamanho(aparar(req.responsavelNome()), 120, "Responsável");
        String telefone = tamanho(aparar(req.telefone()), 30, "Telefone");
        String whatsapp = whatsapp(req.whatsapp());
        String email = email(req.emailOficial());
        String cidade = tamanho(aparar(req.cidade()), 100, "Cidade");
        String uf = uf(req.uf());

        Tenant tenant = buscar(tenantId);
        CadastroEmpresa antes = cadastro(tenant);

        tenant.setRazaoSocial(razaoSocial);
        tenant.setCnpj(cnpj);
        tenant.setResponsavelNome(responsavel);
        tenant.setTelefone(telefone);
        tenant.setWhatsapp(whatsapp);
        tenant.setEmailOficial(email);
        tenant.setCidade(cidade);
        tenant.setUf(uf);
        tenantRepository.save(tenant);

        CadastroEmpresa depois = cadastro(tenant);
        List<String> mudancas = diferencas(antes, depois);
        if (mudancas.isEmpty()) {
            return depois;
        }
        String detalhe = String.join("; ", mudancas) + " — " + motivo;
        String status = tenant.getStatus().name();
        eventPublisher.publishEvent(TenantStatusChangedEvent.of(
            tenantId, "TENANT_CADASTRO_ALTERADO", status, status,
            TenantContext.getUsuarioId(), detalhe, tenant.getRazaoSocial(), tenant.getSlug()));
        log.warn("[PLATFORM] Cadastro da empresa alterado: tenant={}, {}", tenantId, detalhe);
        return depois;
    }

    // ------------------------------------------------------------------

    private Tenant buscar(UUID tenantId) {
        return tenantRepository.findById(tenantId)
            .orElseThrow(() -> new NotFoundException("Empresa não encontrada: " + tenantId));
    }

    private static CadastroEmpresa cadastro(Tenant t) {
        return new CadastroEmpresa(t.getSlug(), t.getRazaoSocial(), t.getCnpj(), t.getResponsavelNome(),
            t.getTelefone(), t.getWhatsapp(), t.getEmailOficial(), t.getCidade(), t.getUf());
    }

    private static List<String> diferencas(CadastroEmpresa a, CadastroEmpresa d) {
        List<String> m = new ArrayList<>();
        comparar(m, "razão social", a.razaoSocial(), d.razaoSocial());
        comparar(m, "CNPJ", a.cnpj(), d.cnpj());
        comparar(m, "responsável", a.responsavelNome(), d.responsavelNome());
        comparar(m, "telefone", a.telefone(), d.telefone());
        comparar(m, "WhatsApp", a.whatsapp(), d.whatsapp());
        comparar(m, "e-mail oficial", a.emailOficial(), d.emailOficial());
        comparar(m, "cidade", a.cidade(), d.cidade());
        comparar(m, "UF", a.uf(), d.uf());
        return m;
    }

    private static void comparar(List<String> m, String campo, String antes, String depois) {
        if (!Objects.equals(antes, depois)) {
            m.add(campo + ": " + (antes == null ? "(vazio)" : antes) + " → " + (depois == null ? "(vazio)" : depois));
        }
    }

    private static String aparar(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String tamanho(String s, int max, String campo) {
        if (s != null && s.length() > max) {
            throw new BusinessException(campo + " deve ter no máximo " + max + " caracteres.");
        }
        return s;
    }

    /** CNPJ com dígitos verificadores; aceita com ou sem máscara e grava mascarado. */
    static String cnpj(String valor) {
        String s = aparar(valor);
        if (s == null) {
            return null;
        }
        String d = s.replaceAll("\\D", "");
        if (d.length() != 14 || d.chars().distinct().count() == 1 || !digitosCnpjValidos(d)) {
            throw new BusinessException("CNPJ inválido.");
        }
        return d.substring(0, 2) + "." + d.substring(2, 5) + "." + d.substring(5, 8) + "/"
            + d.substring(8, 12) + "-" + d.substring(12);
    }

    private static boolean digitosCnpjValidos(String d) {
        int[] pesos1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] pesos2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        return d.charAt(12) - '0' == digito(d, pesos1) && d.charAt(13) - '0' == digito(d, pesos2);
    }

    private static int digito(String d, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += (d.charAt(i) - '0') * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    /** WhatsApp só com dígitos (formato do marketplace: país + DDD + número). */
    private static String whatsapp(String valor) {
        String s = aparar(valor);
        if (s == null) {
            return null;
        }
        String d = s.replaceAll("\\D", "");
        if (d.length() < 10 || d.length() > 15) {
            throw new BusinessException("WhatsApp inválido: informe DDD e número (ex.: 5548999999999).");
        }
        return d;
    }

    private static String email(String valor) {
        String s = tamanho(aparar(valor), 255, "E-mail oficial");
        if (s != null && !EMAIL.matcher(s).matches()) {
            throw new BusinessException("E-mail oficial inválido.");
        }
        return s;
    }

    private static String uf(String valor) {
        String s = aparar(valor);
        if (s == null) {
            return null;
        }
        String u = s.toUpperCase();
        if (!UFS.contains(u)) {
            throw new BusinessException("UF inválida: use a sigla do estado (ex.: SC).");
        }
        return u;
    }
}
