package com.jetski.combustivel.internal;

import com.jetski.combustivel.domain.Abastecimento;
import com.jetski.combustivel.internal.repository.AbastecimentoRepository;
import com.jetski.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service para gestão de abastecimentos (RF06).
 *
 * Responsabilidades:
 * - Registrar abastecimentos (PRE_LOCACAO, POS_LOCACAO, FROTA)
 * - Listar abastecimentos com filtros
 * - Atualizar preço médio do dia automaticamente
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AbastecimentoService {

    private final AbastecimentoRepository abastecimentoRepository;
    private final FuelPriceDayService fuelPriceDayService;

    /**
     * Registrar novo abastecimento.
     * Atualiza automaticamente o preço médio do dia.
     *
     * @param tenantId ID do tenant
     * @param responsavelId ID do usuário responsável
     * @param abastecimento Dados do abastecimento
     * @return Abastecimento salvo
     */
    @Transactional
    public Abastecimento registrar(UUID tenantId, UUID responsavelId, Abastecimento abastecimento) {
        log.debug("Registrando abastecimento para tenant={}, jetski={}, tipo={}",
            tenantId, abastecimento.getJetskiId(), abastecimento.getTipo());

        abastecimento.setTenantId(tenantId);
        abastecimento.setResponsavelId(responsavelId);

        // Garantir cálculo do custo total
        if (abastecimento.getCustoTotal() == null) {
            abastecimento.recalcularCustoTotal();
        }

        Abastecimento saved = abastecimentoRepository.save(abastecimento);

        // Atualizar preço médio do dia
        LocalDate dataAbastecimento = saved.getDataHora()
            .atZone(ZoneId.systemDefault())
            .toLocalDate();

        fuelPriceDayService.atualizarPrecoMedioDia(
            tenantId,
            dataAbastecimento,
            saved.getLitros(),
            saved.getCustoTotal()
        );

        log.info("Abastecimento registrado: id={}, jetski={}, litros={}, custo=R$ {}",
            saved.getId(), saved.getJetskiId(), saved.getLitros(), saved.getCustoTotal());

        return saved;
    }

    /**
     * Buscar abastecimento por ID.
     *
     * @param tenantId ID do tenant
     * @param id ID do abastecimento
     * @return Optional contendo o abastecimento se encontrado
     */
    @Transactional(readOnly = true)
    public java.util.Optional<Abastecimento> buscarPorId(UUID tenantId, Long id) {
        return abastecimentoRepository.findById(id)
            .filter(a -> a.getTenantId().equals(tenantId));
    }

    /**
     * Listar abastecimentos com filtros.
     *
     * @param tenantId ID do tenant
     * @param jetskiId Filtro opcional por jetski
     * @param locacaoId Filtro opcional por locação
     * @param dataInicio Filtro opcional data início
     * @param dataFim Filtro opcional data fim
     * @param pageable Paginação
     * @return Page de abastecimentos
     */
    @Transactional(readOnly = true)
    public Page<Abastecimento> listar(
            UUID tenantId,
            UUID jetskiId,
            UUID locacaoId,
            LocalDate dataInicio,
            LocalDate dataFim,
            Pageable pageable) {

        Instant instantInicio = dataInicio != null ?
            dataInicio.atStartOfDay(ZoneId.systemDefault()).toInstant() : null;

        Instant instantFim = dataFim != null ?
            dataFim.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant() : null;

        return abastecimentoRepository.findWithFilters(
            tenantId,
            jetskiId,
            locacaoId,
            instantInicio,
            instantFim,
            pageable
        );
    }

    /**
     * Listar abastecimentos por jetski com filtro de data.
     *
     * @param tenantId ID do tenant
     * @param jetskiId ID do jetski
     * @param dataInicio Data início (opcional)
     * @param dataFim Data fim (opcional)
     * @return Lista de abastecimentos
     */
    @Transactional(readOnly = true)
    public List<Abastecimento> listarPorJetski(UUID tenantId, UUID jetskiId, LocalDate dataInicio, LocalDate dataFim) {
        if (dataInicio == null && dataFim == null) {
            return abastecimentoRepository.findByTenantIdAndJetskiIdOrderByDataHoraDesc(tenantId, jetskiId);
        }

        Instant instantInicio = dataInicio != null ?
            dataInicio.atStartOfDay(ZoneId.systemDefault()).toInstant() : Instant.MIN;

        Instant instantFim = dataFim != null ?
            dataFim.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant() : Instant.MAX;

        return abastecimentoRepository.findByTenantIdAndJetskiIdAndDataHoraBetweenOrderByDataHoraDesc(
            tenantId, jetskiId, instantInicio, instantFim
        );
    }

    /**
     * Listar abastecimentos por locação.
     *
     * @param tenantId ID do tenant
     * @param locacaoId ID da locação
     * @return Lista de abastecimentos
     */
    @Transactional(readOnly = true)
    public List<Abastecimento> listarPorLocacao(UUID tenantId, UUID locacaoId) {
        return abastecimentoRepository.findByTenantIdAndLocacaoIdOrderByDataHoraAsc(tenantId, locacaoId);
    }

    /**
     * Listar todos os abastecimentos com filtro de data.
     *
     * @param tenantId ID do tenant
     * @param dataInicio Data início (opcional)
     * @param dataFim Data fim (opcional)
     * @return Lista de abastecimentos
     */
    @Transactional(readOnly = true)
    public List<Abastecimento> listarTodos(UUID tenantId, LocalDate dataInicio, LocalDate dataFim) {
        if (dataInicio == null && dataFim == null) {
            return abastecimentoRepository.findByTenantIdOrderByDataHoraDesc(tenantId);
        }

        Instant instantInicio = dataInicio != null ?
            dataInicio.atStartOfDay(ZoneId.systemDefault()).toInstant() : Instant.MIN;

        Instant instantFim = dataFim != null ?
            dataFim.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant() : Instant.MAX;

        return abastecimentoRepository.findByTenantIdAndDataHoraBetweenOrderByDataHoraDesc(
            tenantId, instantInicio, instantFim
        );
    }

    /**
     * Listar abastecimentos por tipo com filtro de data.
     *
     * @param tenantId ID do tenant
     * @param tipo Tipo de abastecimento
     * @param dataInicio Data início (opcional)
     * @param dataFim Data fim (opcional)
     * @return Lista de abastecimentos
     */
    @Transactional(readOnly = true)
    public List<Abastecimento> listarPorTipo(UUID tenantId, com.jetski.combustivel.domain.TipoAbastecimento tipo, LocalDate dataInicio, LocalDate dataFim) {
        if (dataInicio == null && dataFim == null) {
            return abastecimentoRepository.findByTenantIdAndTipoOrderByDataHoraDesc(tenantId, tipo);
        }

        Instant instantInicio = dataInicio != null ?
            dataInicio.atStartOfDay(ZoneId.systemDefault()).toInstant() : Instant.MIN;

        Instant instantFim = dataFim != null ?
            dataFim.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant() : Instant.MAX;

        return abastecimentoRepository.findByTenantIdAndTipoAndDataHoraBetweenOrderByDataHoraDesc(
            tenantId, tipo, instantInicio, instantFim
        );
    }
}
