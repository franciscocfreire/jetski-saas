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
import java.util.Objects;
import java.util.UUID;

/**
 * CRUD de instrutores (EAMA) — Anexo 5-B-1.
 *
 * <p>Emissão delegada (V070, EMISSAO_DELEGADA_SPEC §8.N): na operadora de uma
 * parceria em vigor, instrutor novo vai direto para a aprovação da EAMA, e
 * alterar os dados de um instrutor já submetido devolve o pedido a PENDENTE.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InstrutorService {

    private final InstrutorRepository repository;
    private final StorageService storageService;
    private final VinculoEmissaoService vinculoEmissaoService;

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
        // Operadora de parceria em vigor: o instrutor só assina emissão delegada
        // depois de aprovado pela EAMA (NORMAM-212) — o pedido sai junto com o cadastro.
        if (vinculoEmissaoService.emissaoDelegadaEmVigor(saved.getTenantId())) {
            vinculoEmissaoService.solicitarAprovacaoInstrutor(saved.getTenantId(), saved.getId());
        }
        return saved;
    }

    @Transactional
    public Instrutor atualizar(UUID id, Instrutor updates, String assinaturaBase64) {
        Instrutor existing = buscar(id);
        boolean mudou = false;
        if (updates.getNome() != null && !updates.getNome().isBlank()
                && !updates.getNome().equals(existing.getNome())) {
            existing.setNome(updates.getNome());
            mudou = true;
        }
        if (updates.getRg() != null && !updates.getRg().equals(existing.getRg())) {
            existing.setRg(updates.getRg());
            mudou = true;
        }
        if (updates.getOrgaoEmissor() != null && !updates.getOrgaoEmissor().equals(existing.getOrgaoEmissor())) {
            existing.setOrgaoEmissor(updates.getOrgaoEmissor());
            mudou = true;
        }
        if (updates.getCpf() != null && !updates.getCpf().equals(existing.getCpf())) {
            existing.setCpf(updates.getCpf());
            mudou = true;
        }
        if (updates.getCha() != null && !updates.getCha().equals(existing.getCha())) {
            existing.setCha(updates.getCha());
            mudou = true;
        }
        if (updates.getDataEmissao() != null && !Objects.equals(updates.getDataEmissao(), existing.getDataEmissao())) {
            existing.setDataEmissao(updates.getDataEmissao());
            mudou = true;
        }
        if (assinaturaBase64 != null && !assinaturaBase64.isBlank()) {
            existing.setAssinaturaS3Key(arquivarAssinatura(existing.getId(), assinaturaBase64));
            mudou = true;
        }
        Instrutor saved = repository.save(existing);
        if (mudou) {
            // A EAMA aprovou os dados que viu: mudou algo, ela revisa de novo.
            vinculoEmissaoService.reenviarParaAprovacaoSeAlterado(saved.getTenantId(), saved.getId(), saved.getNome());
        }
        return saved;
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
