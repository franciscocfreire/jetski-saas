package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.Instrutor;
import com.jetski.locacoes.internal.repository.InstrutorRepository;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** CRUD de instrutores (EAMA) — Anexo 5-B-1. */
@Service
@Slf4j
@RequiredArgsConstructor
public class InstrutorService {

    private final InstrutorRepository repository;

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
    public Instrutor criar(Instrutor dados) {
        if (dados.getNome() == null || dados.getNome().isBlank()) {
            throw new BusinessException("Nome do instrutor é obrigatório");
        }
        dados.setTenantId(TenantContext.getTenantId());
        dados.setAtivo(true);
        Instrutor saved = repository.save(dados);
        log.info("Instrutor criado: id={}, nome={}", saved.getId(), saved.getNome());
        return saved;
    }

    @Transactional
    public Instrutor atualizar(UUID id, Instrutor updates) {
        Instrutor existing = buscar(id);
        if (updates.getNome() != null && !updates.getNome().isBlank()) existing.setNome(updates.getNome());
        if (updates.getRg() != null) existing.setRg(updates.getRg());
        if (updates.getOrgaoEmissor() != null) existing.setOrgaoEmissor(updates.getOrgaoEmissor());
        if (updates.getCpf() != null) existing.setCpf(updates.getCpf());
        if (updates.getCha() != null) existing.setCha(updates.getCha());
        return repository.save(existing);
    }

    @Transactional
    public Instrutor definirAtivo(UUID id, boolean ativo) {
        Instrutor existing = buscar(id);
        existing.setAtivo(ativo);
        return repository.save(existing);
    }
}
