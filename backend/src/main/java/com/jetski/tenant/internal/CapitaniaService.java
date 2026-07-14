package com.jetski.tenant.internal;

import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.ConflictException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.tenant.api.dto.CapitaniaRequest;
import com.jetski.tenant.api.dto.CapitaniaResponse;
import com.jetski.tenant.domain.Capitania;
import com.jetski.tenant.internal.repository.CapitaniaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Catálogo de capitanias (V047): leitura para qualquer usuário autenticado
 * (cadastro/configuração), manutenção só pelo super admin (platform:*).
 *
 * @author Jetski Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CapitaniaService {

    private final CapitaniaRepository capitaniaRepository;

    /** Catálogo público (autenticado): só capitanias ativas. */
    @Transactional(readOnly = true)
    public List<CapitaniaResponse> listarAtivas() {
        return capitaniaRepository.findByAtivaTrueOrderByNome().stream()
            .map(CapitaniaResponse::of)
            .toList();
    }

    /** Visão do super admin: todas, inclusive inativas. */
    @Transactional(readOnly = true)
    public List<CapitaniaResponse> listarTodas() {
        return capitaniaRepository.findAllByOrderByNome().stream()
            .map(CapitaniaResponse::of)
            .toList();
    }

    @Transactional
    public CapitaniaResponse criar(CapitaniaRequest req) {
        validar(req);
        String codigo = req.codigo().trim().toUpperCase();
        capitaniaRepository.findByCodigoIgnoreCase(codigo).ifPresent(c -> {
            throw new ConflictException("Já existe capitania com o código " + codigo);
        });
        Capitania c = Capitania.builder()
            .codigo(codigo)
            .nome(req.nome().trim())
            .uf(normalizarUf(req.uf()))
            .emailOficial(blankToNull(req.emailOficial()))
            .ativa(req.ativa() == null || req.ativa())
            .build();
        capitaniaRepository.save(c);
        log.info("[PLATFORM] Capitania criada: {} ({})", c.getCodigo(), c.getId());
        return CapitaniaResponse.of(c);
    }

    @Transactional
    public CapitaniaResponse atualizar(UUID id, CapitaniaRequest req) {
        validar(req);
        Capitania c = capitaniaRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Capitania não encontrada: " + id));
        String codigo = req.codigo().trim().toUpperCase();
        capitaniaRepository.findByCodigoIgnoreCase(codigo)
            .filter(outra -> !outra.getId().equals(id))
            .ifPresent(outra -> {
                throw new ConflictException("Já existe capitania com o código " + codigo);
            });
        c.setCodigo(codigo);
        c.setNome(req.nome().trim());
        c.setUf(normalizarUf(req.uf()));
        c.setEmailOficial(blankToNull(req.emailOficial()));
        if (req.ativa() != null) {
            c.setAtiva(req.ativa());
        }
        capitaniaRepository.save(c);
        log.info("[PLATFORM] Capitania atualizada: {} ({})", c.getCodigo(), c.getId());
        return CapitaniaResponse.of(c);
    }

    /** Capitania ativa obrigatória (para vínculo do tenant). */
    @Transactional(readOnly = true)
    public Capitania requireAtiva(UUID id) {
        Capitania c = capitaniaRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Capitania não encontrada: " + id));
        if (!Boolean.TRUE.equals(c.getAtiva())) {
            throw new BusinessException("Capitania " + c.getCodigo() + " está inativa no catálogo");
        }
        return c;
    }

    private void validar(CapitaniaRequest req) {
        if (req == null || req.codigo() == null || req.codigo().isBlank()) {
            throw new BusinessException("Código da capitania é obrigatório");
        }
        if (req.nome() == null || req.nome().isBlank()) {
            throw new BusinessException("Nome da capitania é obrigatório");
        }
    }

    private static String normalizarUf(String uf) {
        return uf == null || uf.isBlank() ? null : uf.trim().toUpperCase();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
