package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.EmissaoDelegada;
import com.jetski.locacoes.internal.repository.EmissaoDelegadaRepository;
import com.jetski.shared.email.EmailService;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.storage.StorageService;
import com.jetski.tenant.TenantQueryService;
import com.jetski.tenant.domain.Tenant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Painel "Emissões em meu nome" da EAMA emissora (EMISSAO_DELEGADA_SPEC §4.4):
 * lista o espelho, contagens mensais por operadora (base do acerto por fora,
 * §8.C) e reenvio do MESMO PDF à Capitania — sem re-emissão, sem novo crédito.
 *
 * @author Jetski Team
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmissaoDelegadaService {

    private final EmissaoDelegadaRepository repository;
    private final StorageService storageService;
    private final EmailService emailService;
    private final TenantQueryService tenantQueryService;

    @Transactional(readOnly = true)
    public List<EmissaoDelegada> listar(UUID tenantId, UUID operadoraId, int limit) {
        return repository.listar(tenantId, operadoraId,
            PageRequest.of(0, Math.max(1, Math.min(limit, 200))));
    }

    /** Contagem mensal por operadora: [operadoraTenantId, nome, "YYYY-MM", total]. */
    @Transactional(readOnly = true)
    public List<Object[]> contagens(UUID tenantId) {
        return repository.contagensPorOperadoraMes(tenantId);
    }

    /** URL presignada do PDF espelhado (baixar no painel). */
    @Transactional(readOnly = true)
    public String downloadUrl(UUID tenantId, UUID id) {
        EmissaoDelegada e = require(tenantId, id);
        if (e.getS3Key() == null) {
            throw new BusinessException("PDF desta emissão não está mais disponível no storage");
        }
        return storageService.generatePresignedDownloadUrl(e.getS3Key(), 15).getUrl();
    }

    /**
     * Reenvia o PDF já emitido à Capitania (ou a destinatário pontual que a
     * EAMA informar). NÃO re-emite, NÃO debita crédito (§4.4). O rastro do
     * reenvio fica no espelho.
     */
    @Transactional
    public EmissaoDelegada reenviar(UUID tenantId, UUID id, String destinoOpcional) {
        EmissaoDelegada e = require(tenantId, id);
        if (e.getS3Key() == null) {
            throw new BusinessException("PDF desta emissão não está mais disponível no storage");
        }
        String destino = destinoOpcional != null && !destinoOpcional.isBlank()
            ? destinoOpcional.trim()
            : marinhaEmailDoEmissor(tenantId);
        if (destino == null || destino.isBlank()) {
            throw new BusinessException("Informe o destinatário ou configure o e-mail da "
                + "Capitania em Configurações");
        }
        byte[] pdf;
        try {
            pdf = storageService.getObject(e.getS3Key());
        } catch (Exception ex) {
            throw new BusinessException("PDF desta emissão não pôde ser lido do storage");
        }
        try {
            emailService.sendEmailComAnexo(destino,
                assuntoReenvio(e), corpoReenvio(e), "documentos.pdf", pdf, "application/pdf");
        } catch (Exception ex) {
            throw new BusinessException("Falha ao enviar o e-mail: " + ex.getMessage());
        }
        e.setReenviadoEm(Instant.now());
        e.setReenviadoPara(destino);
        repository.save(e);
        log.info("Emissão delegada reenviada: id={}, tenantEmissor={}, para={}", id, tenantId, destino);
        return e;
    }

    private EmissaoDelegada require(UUID tenantId, UUID id) {
        return repository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new NotFoundException("Emissão delegada não encontrada: " + id));
    }

    private String marinhaEmailDoEmissor(UUID tenantId) {
        Tenant t = tenantQueryService.findById(tenantId);
        return t != null ? t.getMarinhaEmail() : null;
    }

    private String assuntoReenvio(EmissaoDelegada e) {
        return e.getGruNumero() != null && !e.getGruNumero().isBlank()
            ? "Documentos NORMAM-212 — GRU " + e.getGruNumero() + " (reenvio)"
            : "Documentos NORMAM-212 (reenvio)";
    }

    private String corpoReenvio(EmissaoDelegada e) {
        StringBuilder sb = new StringBuilder();
        sb.append("<p>Segue em anexo a documentação (NORMAM-212/DPC) referente ao condutor <b>")
          .append(e.getCondutorNome() != null ? e.getCondutorNome() : "—").append("</b>");
        if (e.getCondutorCpf() != null) {
            sb.append(" (CPF ").append(e.getCondutorCpf()).append(")");
        }
        sb.append(".</p>");
        if (e.getDocumentoHash() != null) {
            sb.append("<p>Hash SHA-256 do documento: <code>").append(e.getDocumentoHash())
              .append("</code></p>");
        }
        return sb.toString();
    }
}
