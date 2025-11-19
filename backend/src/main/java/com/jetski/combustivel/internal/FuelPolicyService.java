package com.jetski.combustivel.internal;

import com.jetski.combustivel.domain.*;
import com.jetski.combustivel.internal.repository.AbastecimentoRepository;
import com.jetski.combustivel.internal.repository.FuelPolicyRepository;
import com.jetski.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service para gestão de políticas de combustível (RN03).
 *
 * Responsabilidades:
 * - Buscar política aplicável seguindo hierarquia: JETSKI → MODELO → GLOBAL
 * - Calcular custo de combustível conforme modo: INCLUSO, MEDIDO, TAXA_FIXA
 * - Criar e gerenciar políticas
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FuelPolicyService {

    private final FuelPolicyRepository fuelPolicyRepository;
    private final FuelPriceDayService fuelPriceDayService;
    private final AbastecimentoRepository abastecimentoRepository;

    /**
     * RN03: Buscar política aplicável seguindo hierarquia.
     * Ordem de busca (primeiro match ganha):
     * 1. JETSKI específico (jetskiId)
     * 2. MODELO específico (modeloId)
     * 3. GLOBAL (fallback)
     *
     * @param tenantId ID do tenant
     * @param jetskiId ID do jetski
     * @param modeloId ID do modelo do jetski
     * @return FuelPolicy aplicável
     * @throws NotFoundException se nenhuma política ativa encontrada
     */
    @Transactional(readOnly = true)
    public FuelPolicy buscarPoliticaAplicavel(UUID tenantId, UUID jetskiId, UUID modeloId) {
        log.debug("Buscando política de combustível para tenant={}, jetski={}, modelo={}",
            tenantId, jetskiId, modeloId);

        // 1. Tentar JETSKI específico
        Optional<FuelPolicy> jetskiPolicy = fuelPolicyRepository
            .findByTenantIdAndAplicavelAAndReferenciaIdAndAtivoTrue(
                tenantId, FuelPolicyType.JETSKI, jetskiId
            );

        if (jetskiPolicy.isPresent()) {
            log.debug("Política JETSKI encontrada: id={}, nome={}, tipo={}",
                jetskiPolicy.get().getId(), jetskiPolicy.get().getNome(), jetskiPolicy.get().getTipo());
            return jetskiPolicy.get();
        }

        // 2. Tentar MODELO
        Optional<FuelPolicy> modeloPolicy = fuelPolicyRepository
            .findByTenantIdAndAplicavelAAndReferenciaIdAndAtivoTrue(
                tenantId, FuelPolicyType.MODELO, modeloId
            );

        if (modeloPolicy.isPresent()) {
            log.debug("Política MODELO encontrada: id={}, nome={}, tipo={}",
                modeloPolicy.get().getId(), modeloPolicy.get().getNome(), modeloPolicy.get().getTipo());
            return modeloPolicy.get();
        }

        // 3. Fallback para GLOBAL
        Optional<FuelPolicy> globalPolicy = fuelPolicyRepository
            .findByTenantIdAndAplicavelAAndAtivoTrue(tenantId, FuelPolicyType.GLOBAL);

        if (globalPolicy.isPresent()) {
            log.debug("Política GLOBAL encontrada: id={}, nome={}, tipo={}",
                globalPolicy.get().getId(), globalPolicy.get().getNome(), globalPolicy.get().getTipo());
            return globalPolicy.get();
        }

        // Nenhuma política ativa encontrada
        throw new NotFoundException(
            "Nenhuma política de combustível ativa encontrada para tenant: " + tenantId +
            " (jetski: " + jetskiId + ", modelo: " + modeloId + ")"
        );
    }

    /**
     * RN03: Calcular custo de combustível para uma locação.
     *
     * Modos de cálculo:
     * - INCLUSO: retorna 0 (já incluído no preço/hora)
     * - MEDIDO: litros_consumidos × preço_dia
     * - TAXA_FIXA: valor_taxa_por_hora × horas_faturáveis
     *
     * @param locacaoData Dados da locação para calcular custo
     * @param modeloId ID do modelo do jetski (necessário para buscar política aplicável)
     * @return Custo de combustível calculado
     */
    @Transactional(readOnly = true)
    public BigDecimal calcularCustoCombustivel(LocacaoFuelData locacaoData, UUID modeloId) {
        log.debug("Calculando custo de combustível para locacao id={}", locacaoData.getId());

        FuelPolicy policy = buscarPoliticaAplicavel(
            locacaoData.getTenantId(),
            locacaoData.getJetskiId(),
            modeloId
        );

        BigDecimal custo;

        switch (policy.getTipo()) {
            case INCLUSO:
                custo = calcularCustoIncluso();
                break;

            case MEDIDO:
                custo = calcularCustoMedido(locacaoData);
                break;

            case TAXA_FIXA:
                custo = calcularCustoTaxaFixa(locacaoData, policy);
                break;

            default:
                throw new IllegalStateException("Tipo de política desconhecido: " + policy.getTipo());
        }

        log.info("Custo combustível calculado: R$ {} (política: {}, tipo: {})",
            custo, policy.getNome(), policy.getTipo());

        return custo;
    }

    /**
     * Modo INCLUSO: combustível já está incluído no preço/hora.
     * Cliente não é cobrado separadamente, mas custo é rastreado para controle operacional.
     *
     * @return Sempre BigDecimal.ZERO
     */
    private BigDecimal calcularCustoIncluso() {
        log.debug("Política INCLUSO: custo = R$ 0,00");
        return BigDecimal.ZERO;
    }

    /**
     * Modo MEDIDO: litros consumidos × preço do dia.
     * Busca abastecimentos PRE e POS da locação para calcular consumo.
     *
     * @param locacaoData Dados da locação
     * @return Custo calculado
     */
    private BigDecimal calcularCustoMedido(LocacaoFuelData locacaoData) {
        // Buscar abastecimentos desta locação
        List<Abastecimento> abastecimentos = abastecimentoRepository
            .findByTenantIdAndLocacaoIdOrderByDataHoraAsc(
                locacaoData.getTenantId(),
                locacaoData.getId()
            );

        if (abastecimentos.isEmpty()) {
            log.warn("Nenhum abastecimento encontrado para locacao id={}. Custo = R$ 0,00", locacaoData.getId());
            return BigDecimal.ZERO;
        }

        // Somar litros PRE e POS
        BigDecimal litrosPreLocacao = abastecimentos.stream()
            .filter(a -> a.getTipo() == TipoAbastecimento.PRE_LOCACAO)
            .map(Abastecimento::getLitros)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal litrosPosLocacao = abastecimentos.stream()
            .filter(a -> a.getTipo() == TipoAbastecimento.POS_LOCACAO)
            .map(Abastecimento::getLitros)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Litros consumidos = POS - PRE (valor absoluto)
        BigDecimal litrosConsumidos = litrosPosLocacao.subtract(litrosPreLocacao).abs();

        if (litrosConsumidos.compareTo(BigDecimal.ZERO) == 0) {
            log.debug("Litros consumidos = 0. Custo = R$ 0,00");
            return BigDecimal.ZERO;
        }

        // Preço do dia da locação (data do check-out)
        LocalDate dataLocacao = locacaoData.getDataCheckOut().atZone(ZoneId.systemDefault()).toLocalDate();

        BigDecimal precoDia = fuelPriceDayService.obterPrecoMedioDia(
            locacaoData.getTenantId(),
            dataLocacao
        );

        // Custo = litros × preço
        BigDecimal custo = litrosConsumidos.multiply(precoDia)
            .setScale(2, RoundingMode.HALF_UP);

        log.debug("Custo MEDIDO: {} litros × R$ {} = R$ {}",
            litrosConsumidos, precoDia, custo);

        return custo;
    }

    /**
     * Modo TAXA_FIXA: valor_taxa_por_hora × horas_faturáveis.
     *
     * @param locacaoData Dados da locação
     * @param policy Política com valor da taxa
     * @return Custo calculado
     */
    private BigDecimal calcularCustoTaxaFixa(LocacaoFuelData locacaoData, FuelPolicy policy) {
        if (policy.getValorTaxaPorHora() == null) {
            throw new IllegalStateException(
                "Política TAXA_FIXA sem valor configurado: " + policy.getId()
            );
        }

        // Converter minutos faturáveis em horas (2 decimais)
        BigDecimal horasFaturaveis = new BigDecimal(locacaoData.getMinutosFaturaveis())
            .divide(new BigDecimal(60), 2, RoundingMode.HALF_UP);

        BigDecimal custo = policy.getValorTaxaPorHora()
            .multiply(horasFaturaveis)
            .setScale(2, RoundingMode.HALF_UP);

        log.debug("Custo TAXA_FIXA: {} horas × R$ {} = R$ {}",
            horasFaturaveis, policy.getValorTaxaPorHora(), custo);

        return custo;
    }

    // ===== CRUD Methods =====

    @Transactional
    public FuelPolicy criar(UUID tenantId, FuelPolicy policy) {
        policy.setTenantId(tenantId);
        validatePolicy(policy);

        FuelPolicy saved = fuelPolicyRepository.save(policy);
        log.info("Política de combustível criada: id={}, nome={}, tipo={}, aplicavel_a={}",
            saved.getId(), saved.getNome(), saved.getTipo(), saved.getAplicavelA());

        return saved;
    }

    @Transactional(readOnly = true)
    public List<FuelPolicy> listarAtivas(UUID tenantId) {
        return fuelPolicyRepository.findByTenantIdAndAtivoTrueOrderByPrioridadeDescCreatedAtDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public List<FuelPolicy> listarTodas(UUID tenantId) {
        return fuelPolicyRepository.findByTenantIdOrderByAtivoDescPrioridadeDescCreatedAtDesc(tenantId);
    }

    @Transactional
    public FuelPolicy atualizar(UUID tenantId, Long id, FuelPolicy updates) {
        FuelPolicy existing = fuelPolicyRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("FuelPolicy não encontrada: " + id));

        if (!existing.getTenantId().equals(tenantId)) {
            throw new NotFoundException("FuelPolicy não pertence ao tenant: " + tenantId);
        }

        // Atualizar campos permitidos
        if (updates.getNome() != null) existing.setNome(updates.getNome());
        if (updates.getTipo() != null) existing.setTipo(updates.getTipo());
        if (updates.getValorTaxaPorHora() != null) existing.setValorTaxaPorHora(updates.getValorTaxaPorHora());
        if (updates.getComissionavel() != null) existing.setComissionavel(updates.getComissionavel());
        if (updates.getAtivo() != null) existing.setAtivo(updates.getAtivo());
        if (updates.getPrioridade() != null) existing.setPrioridade(updates.getPrioridade());
        if (updates.getDescricao() != null) existing.setDescricao(updates.getDescricao());

        validatePolicy(existing);

        return fuelPolicyRepository.save(existing);
    }

    @Transactional
    public void desativar(UUID tenantId, Long id) {
        FuelPolicy policy = fuelPolicyRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("FuelPolicy não encontrada: " + id));

        if (!policy.getTenantId().equals(tenantId)) {
            throw new NotFoundException("FuelPolicy não pertence ao tenant: " + tenantId);
        }

        policy.setAtivo(false);
        fuelPolicyRepository.save(policy);

        log.info("Política de combustível desativada: id={}", id);
    }

    private void validatePolicy(FuelPolicy policy) {
        // TAXA_FIXA deve ter valor configurado
        if (policy.getTipo() == FuelChargeMode.TAXA_FIXA && policy.getValorTaxaPorHora() == null) {
            throw new IllegalArgumentException(
                "Política TAXA_FIXA requer valor_taxa_por_hora configurado"
            );
        }

        // GLOBAL não pode ter referencia_id
        if (policy.getAplicavelA() == FuelPolicyType.GLOBAL && policy.getReferenciaId() != null) {
            throw new IllegalArgumentException(
                "Política GLOBAL não pode ter referencia_id"
            );
        }

        // MODELO e JETSKI devem ter referencia_id
        if ((policy.getAplicavelA() == FuelPolicyType.MODELO || policy.getAplicavelA() == FuelPolicyType.JETSKI)
            && policy.getReferenciaId() == null) {
            throw new IllegalArgumentException(
                "Política " + policy.getAplicavelA() + " requer referencia_id"
            );
        }
    }

    /**
     * Buscar política por ID.
     *
     * @param tenantId ID do tenant
     * @param id ID da política
     * @return Optional contendo a política se encontrada
     */
    @Transactional(readOnly = true)
    public Optional<FuelPolicy> buscarPorId(UUID tenantId, Long id) {
        return fuelPolicyRepository.findById(id)
            .filter(p -> p.getTenantId().equals(tenantId));
    }

    /**
     * Listar todas as políticas do tenant.
     *
     * @param tenantId ID do tenant
     * @param ativo Filtro opcional por status ativo
     * @return Lista de políticas
     */
    @Transactional(readOnly = true)
    public List<FuelPolicy> listarTodas(UUID tenantId, Boolean ativo) {
        if (ativo != null) {
            return fuelPolicyRepository.findByTenantIdAndAtivoOrderByPrioridadeDesc(tenantId, ativo);
        }
        return fuelPolicyRepository.findByTenantIdOrderByPrioridadeDesc(tenantId);
    }

    /**
     * Listar políticas por tipo (GLOBAL, MODELO, JETSKI).
     *
     * @param tenantId ID do tenant
     * @param tipo Tipo de política
     * @param ativo Filtro opcional por status ativo
     * @return Lista de políticas
     */
    @Transactional(readOnly = true)
    public List<FuelPolicy> listarPorTipo(UUID tenantId, FuelPolicyType tipo, Boolean ativo) {
        if (ativo != null) {
            return fuelPolicyRepository.findByTenantIdAndAplicavelAAndAtivoOrderByPrioridadeDesc(
                tenantId, tipo, ativo
            );
        }
        return fuelPolicyRepository.findByTenantIdAndAplicavelAOrderByPrioridadeDesc(tenantId, tipo);
    }

    /**
     * Atualizar política existente.
     *
     * @param tenantId ID do tenant
     * @param id ID da política
     * @param request Dados para atualização
     * @return Política atualizada
     */
    @Transactional
    public FuelPolicy atualizar(UUID tenantId, Long id, com.jetski.combustivel.api.dto.FuelPolicyUpdateRequest request) {
        FuelPolicy existing = buscarPorId(tenantId, id)
            .orElseThrow(() -> new NotFoundException("Política não encontrada: " + id));

        if (request.getNome() != null) {
            existing.setNome(request.getNome());
        }
        if (request.getValorTaxaPorHora() != null) {
            existing.setValorTaxaPorHora(request.getValorTaxaPorHora());
        }
        if (request.getComissionavel() != null) {
            existing.setComissionavel(request.getComissionavel());
        }
        if (request.getAtivo() != null) {
            existing.setAtivo(request.getAtivo());
        }
        if (request.getPrioridade() != null) {
            existing.setPrioridade(request.getPrioridade());
        }
        if (request.getDescricao() != null) {
            existing.setDescricao(request.getDescricao());
        }

        FuelPolicy updated = fuelPolicyRepository.save(existing);

        log.info("Política atualizada: id={}, nome={}", updated.getId(), updated.getNome());

        return updated;
    }

    /**
     * Deletar (inativar) política.
     *
     * @param tenantId ID do tenant
     * @param id ID da política
     */
    @Transactional
    public void deletar(UUID tenantId, Long id) {
        FuelPolicy policy = buscarPorId(tenantId, id)
            .orElseThrow(() -> new NotFoundException("Política não encontrada: " + id));

        policy.setAtivo(false);
        fuelPolicyRepository.save(policy);

        log.info("Política inativada: id={}, nome={}", id, policy.getNome());
    }
}
