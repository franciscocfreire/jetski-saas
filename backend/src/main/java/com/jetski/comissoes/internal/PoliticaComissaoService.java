package com.jetski.comissoes.internal;

import com.jetski.comissoes.domain.NivelPolitica;
import com.jetski.comissoes.domain.PoliticaComissao;
import com.jetski.comissoes.internal.repository.PoliticaComissaoRepository;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for Commission Policy management
 *
 * @author Jetski Team
 * @since 0.7.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PoliticaComissaoService {

    private final PoliticaComissaoRepository politicaRepository;

    /**
     * Cria nova política de comissão
     */
    public PoliticaComissao criar(UUID tenantId, PoliticaComissao politica) {
        politica.setTenantId(tenantId);

        // Set createdBy from TenantContext (already resolved from Keycloak UUID to PostgreSQL UUID)
        if (politica.getCreatedBy() == null) {
            try {
                UUID usuarioId = com.jetski.shared.security.TenantContext.getUsuarioId();
                if (usuarioId != null) {
                    politica.setCreatedBy(usuarioId);
                } else {
                    log.warn("Could not extract usuarioId from TenantContext");
                }
            } catch (Exception e) {
                log.warn("Error setting createdBy: {}", e.getMessage());
                // Will fail with NOT NULL constraint if not set
            }
        }

        // Validar regras de negócio
        validarPolitica(politica);

        PoliticaComissao salva = politicaRepository.save(politica);
        log.info("Política de comissão criada: {} (nível: {}, tipo: {})",
                salva.getNome(), salva.getNivel(), salva.getTipo());

        return salva;
    }

    /**
     * Atualiza política existente
     */
    public PoliticaComissao atualizar(UUID tenantId, UUID id, PoliticaComissao politicaAtualizada) {
        PoliticaComissao politica = buscarPorId(tenantId, id);

        // Atualizar campos
        politica.setNome(politicaAtualizada.getNome());
        politica.setTipo(politicaAtualizada.getTipo());
        politica.setPercentualComissao(politicaAtualizada.getPercentualComissao());
        politica.setPercentualExtra(politicaAtualizada.getPercentualExtra());
        politica.setValorFixo(politicaAtualizada.getValorFixo());
        politica.setDuracaoMinMinutos(politicaAtualizada.getDuracaoMinMinutos());
        politica.setDuracaoMaxMinutos(politicaAtualizada.getDuracaoMaxMinutos());
        politica.setVigenciaInicio(politicaAtualizada.getVigenciaInicio());
        politica.setVigenciaFim(politicaAtualizada.getVigenciaFim());
        politica.setAtiva(politicaAtualizada.getAtiva());
        politica.setDescricao(politicaAtualizada.getDescricao());

        // Validar
        validarPolitica(politica);

        PoliticaComissao salva = politicaRepository.save(politica);
        log.info("Política de comissão atualizada: {}", id);

        return salva;
    }

    /**
     * Ativa/desativa política
     */
    public PoliticaComissao toggleAtiva(UUID tenantId, UUID id) {
        PoliticaComissao politica = buscarPorId(tenantId, id);
        politica.setAtiva(!politica.getAtiva());

        PoliticaComissao salva = politicaRepository.save(politica);
        log.info("Política {} {}", id, salva.getAtiva() ? "ativada" : "desativada");

        return salva;
    }

    /**
     * Busca política por ID
     */
    @Transactional(readOnly = true)
    public PoliticaComissao buscarPorId(UUID tenantId, UUID id) {
        return politicaRepository.findById(id)
                .filter(p -> p.getTenantId().equals(tenantId))
                .orElseThrow(() -> new NotFoundException("Política de comissão não encontrada: " + id));
    }

    /**
     * Lista todas as políticas ativas
     */
    @Transactional(readOnly = true)
    public List<PoliticaComissao> listarAtivas(UUID tenantId) {
        return politicaRepository.findByTenantIdAndAtivaOrderByNivelAsc(tenantId, true);
    }

    /**
     * Lista todas as políticas (ativas e inativas)
     */
    @Transactional(readOnly = true)
    public List<PoliticaComissao> listarTodas(UUID tenantId) {
        return politicaRepository.findByTenantIdAndAtivaOrderByNivelAsc(tenantId, null);
    }

    /**
     * Valida regras de negócio da política
     */
    private void validarPolitica(PoliticaComissao politica) {
        // Validar campos obrigatórios por nível
        switch (politica.getNivel()) {
            case VENDEDOR:
                if (politica.getVendedorId() == null) {
                    throw new BusinessException("vendedorId é obrigatório para política de nível VENDEDOR");
                }
                break;
            case MODELO:
                if (politica.getModeloId() == null) {
                    throw new BusinessException("modeloId é obrigatório para política de nível MODELO");
                }
                break;
            case CAMPANHA:
                if (politica.getCodigoCampanha() == null || politica.getCodigoCampanha().isBlank()) {
                    throw new BusinessException("codigoCampanha é obrigatório para política de nível CAMPANHA");
                }
                break;
            case DURACAO:
                if (politica.getDuracaoMinMinutos() == null) {
                    throw new BusinessException("duracaoMinMinutos é obrigatório para política de nível DURACAO");
                }
                break;
        }

        // Validar campos obrigatórios por tipo
        switch (politica.getTipo()) {
            case PERCENTUAL:
                if (politica.getPercentualComissao() == null) {
                    throw new BusinessException("percentualComissao é obrigatório para tipo PERCENTUAL");
                }
                break;
            case VALOR_FIXO:
                if (politica.getValorFixo() == null) {
                    throw new BusinessException("valorFixo é obrigatório para tipo VALOR_FIXO");
                }
                break;
            case ESCALONADO:
                if (politica.getPercentualComissao() == null ||
                    politica.getPercentualExtra() == null ||
                    politica.getDuracaoMinMinutos() == null) {
                    throw new BusinessException(
                        "percentualComissao, percentualExtra e duracaoMinMinutos são obrigatórios para tipo ESCALONADO"
                    );
                }
                break;
        }

        // Validar faixas de duração
        if (politica.getDuracaoMinMinutos() != null && politica.getDuracaoMaxMinutos() != null) {
            if (politica.getDuracaoMaxMinutos() < politica.getDuracaoMinMinutos()) {
                throw new BusinessException("duracaoMaxMinutos deve ser >= duracaoMinMinutos");
            }
        }
    }
}
