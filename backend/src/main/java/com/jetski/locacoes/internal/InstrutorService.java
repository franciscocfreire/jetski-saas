package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.Instrutor;
import com.jetski.locacoes.internal.repository.InstrutorRepository;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.security.TenantContext;
import com.jetski.shared.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

/** CRUD de instrutores (EAMA) — Anexo 5-B-1. */
@Service
@Slf4j
@RequiredArgsConstructor
public class InstrutorService {

    private final InstrutorRepository repository;
    private final StorageService storageService;

    @Transactional(readOnly = true)
    public List<Instrutor> listar(boolean includeInactive) {
        return includeInactive ? repository.findAllByOrderByNome() : repository.findByAtivoTrueOrderByNome();
    }

    @Transactional(readOnly = true)
    public Instrutor buscar(UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> new NotFoundException("Instrutor não encontrado: " + id));
    }

    @Transactional
    public Instrutor criar(Instrutor dados, String assinaturaBase64) {
        if (dados.getNome() == null || dados.getNome().isBlank()) {
            throw new BusinessException("Nome do instrutor é obrigatório");
        }
        dados.setTenantId(TenantContext.getTenantId());
        dados.setAtivo(true);
        Instrutor saved = repository.save(dados);
        if (assinaturaBase64 != null && !assinaturaBase64.isBlank()) {
            saved.setAssinaturaS3Key(arquivarAssinatura(saved.getId(), assinaturaBase64));
            saved = repository.save(saved);
        }
        log.info("Instrutor criado: id={}, nome={}, assinatura={}",
            saved.getId(), saved.getNome(), saved.getAssinaturaS3Key() != null);
        return saved;
    }

    @Transactional
    public Instrutor atualizar(UUID id, Instrutor updates, String assinaturaBase64) {
        Instrutor existing = buscar(id);
        if (updates.getNome() != null && !updates.getNome().isBlank()) existing.setNome(updates.getNome());
        if (updates.getRg() != null) existing.setRg(updates.getRg());
        if (updates.getOrgaoEmissor() != null) existing.setOrgaoEmissor(updates.getOrgaoEmissor());
        if (updates.getCpf() != null) existing.setCpf(updates.getCpf());
        if (updates.getCha() != null) existing.setCha(updates.getCha());
        if (updates.getDataEmissao() != null) existing.setDataEmissao(updates.getDataEmissao());
        if (assinaturaBase64 != null && !assinaturaBase64.isBlank()) {
            existing.setAssinaturaS3Key(arquivarAssinatura(existing.getId(), assinaturaBase64));
        }
        return repository.save(existing);
    }

    private String arquivarAssinatura(UUID instrutorId, String base64) {
        String pure = base64.contains(",") ? base64.substring(base64.indexOf(',') + 1) : base64;
        byte[] bytes = Base64.getDecoder().decode(pure.trim());
        String key = String.format("%s/instrutor/%s/assinatura.png", TenantContext.getTenantId(), instrutorId);
        storageService.putObject(key, bytes, "image/png");
        return key;
    }

    @Transactional
    public Instrutor definirAtivo(UUID id, boolean ativo) {
        Instrutor existing = buscar(id);
        existing.setAtivo(ativo);
        return repository.save(existing);
    }
}
