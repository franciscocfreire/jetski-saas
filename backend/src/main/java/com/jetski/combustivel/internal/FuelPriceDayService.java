package com.jetski.combustivel.internal;

import com.jetski.combustivel.domain.FuelPriceDay;
import com.jetski.combustivel.internal.repository.FuelPriceDayRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service para gestão de preços médios diários de combustível.
 *
 * Responsabilidades:
 * - Calcular e armazenar preço médio do dia
 * - Buscar preço médio com fallback para média semanal/mensal
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FuelPriceDayService {

    private final FuelPriceDayRepository fuelPriceDayRepository;

    /**
     * Obter preço médio de combustível para uma data.
     * Se não existir preço para o dia, busca média dos últimos 7 dias.
     * Se ainda assim não existir, retorna preço padrão de R$ 6,00.
     *
     * @param tenantId ID do tenant
     * @param data Data para buscar preço
     * @return Preço médio do litro
     */
    @Transactional(readOnly = true)
    public BigDecimal obterPrecoMedioDia(UUID tenantId, LocalDate data) {
        log.debug("Buscando preço médio para tenant={}, data={}", tenantId, data);

        // 1. Tentar buscar preço exato do dia
        Optional<FuelPriceDay> priceDay = fuelPriceDayRepository.findByTenantIdAndData(tenantId, data);
        if (priceDay.isPresent()) {
            log.debug("Preço do dia encontrado: R$ {}", priceDay.get().getPrecoMedioLitro());
            return priceDay.get().getPrecoMedioLitro();
        }

        // 2. Fallback: média dos últimos 7 dias
        LocalDate dataInicio = data.minusDays(7);
        Optional<BigDecimal> avgWeekly = fuelPriceDayRepository.findAveragePrice(
            tenantId, dataInicio, data
        );

        if (avgWeekly.isPresent() && avgWeekly.get().compareTo(BigDecimal.ZERO) > 0) {
            log.debug("Preço médio semanal usado: R$ {}", avgWeekly.get());
            return avgWeekly.get();
        }

        // 3. Fallback final: preço padrão
        BigDecimal precoDefault = new BigDecimal("6.00");
        log.warn("Nenhum preço encontrado para tenant={}, data={}. Usando preço padrão: R$ {}",
            tenantId, data, precoDefault);
        return precoDefault;
    }

    /**
     * Atualizar preço médio do dia com base em um novo abastecimento.
     * Se já existir registro para o dia, atualiza a média.
     * Se não existir, cria novo registro.
     *
     * @param tenantId ID do tenant
     * @param data Data do abastecimento
     * @param litros Litros abastecidos
     * @param custo Custo total do abastecimento
     */
    @Transactional
    public void atualizarPrecoMedioDia(UUID tenantId, LocalDate data, BigDecimal litros, BigDecimal custo) {
        log.debug("Atualizando preço médio para tenant={}, data={}, litros={}, custo={}",
            tenantId, data, litros, custo);

        Optional<FuelPriceDay> existing = fuelPriceDayRepository.findByTenantIdAndData(tenantId, data);

        if (existing.isPresent()) {
            // Atualizar existente
            FuelPriceDay priceDay = existing.get();
            priceDay.setTotalLitrosAbastecidos(
                priceDay.getTotalLitrosAbastecidos().add(litros)
            );
            priceDay.setTotalCusto(
                priceDay.getTotalCusto().add(custo)
            );
            priceDay.setQtdAbastecimentos(
                priceDay.getQtdAbastecimentos() + 1
            );
            priceDay.recalcularPrecoMedio();

            fuelPriceDayRepository.save(priceDay);
            log.info("Preço médio atualizado: R$ {} (dia: {}, qtd: {})",
                priceDay.getPrecoMedioLitro(), data, priceDay.getQtdAbastecimentos());

        } else {
            // Criar novo
            BigDecimal precoMedio = custo.divide(litros, 2, java.math.RoundingMode.HALF_UP);

            FuelPriceDay priceDay = FuelPriceDay.builder()
                .tenantId(tenantId)
                .data(data)
                .precoMedioLitro(precoMedio)
                .totalLitrosAbastecidos(litros)
                .totalCusto(custo)
                .qtdAbastecimentos(1)
                .build();

            fuelPriceDayRepository.save(priceDay);
            log.info("Preço médio criado: R$ {} (dia: {})", precoMedio, data);
        }
    }

    /**
     * Salvar ou sobrescrever preço do dia (admin override).
     *
     * @param priceDay Dados do preço
     * @return Preço salvo
     */
    @Transactional
    public FuelPriceDay salvar(FuelPriceDay priceDay) {
        log.info("Salvando preço do dia: tenant={}, data={}, preco={}",
            priceDay.getTenantId(), priceDay.getData(), priceDay.getPrecoMedioLitro());

        // Verificar se já existe para o dia
        Optional<FuelPriceDay> existing = fuelPriceDayRepository.findByTenantIdAndData(
            priceDay.getTenantId(), priceDay.getData()
        );

        if (existing.isPresent()) {
            // Atualizar existente
            FuelPriceDay updated = existing.get();
            updated.setPrecoMedioLitro(priceDay.getPrecoMedioLitro());
            updated.setTotalLitrosAbastecidos(priceDay.getTotalLitrosAbastecidos());
            updated.setTotalCusto(priceDay.getTotalCusto());
            updated.setQtdAbastecimentos(priceDay.getQtdAbastecimentos());
            return fuelPriceDayRepository.save(updated);
        } else {
            // Criar novo
            return fuelPriceDayRepository.save(priceDay);
        }
    }

    /**
     * Buscar preço por data.
     *
     * @param tenantId ID do tenant
     * @param data Data
     * @return Optional contendo o preço se encontrado
     */
    @Transactional(readOnly = true)
    public Optional<FuelPriceDay> buscarPorData(UUID tenantId, LocalDate data) {
        return fuelPriceDayRepository.findByTenantIdAndData(tenantId, data);
    }

    /**
     * Listar preços com filtro de data.
     *
     * @param tenantId ID do tenant
     * @param dataInicio Data início (opcional)
     * @param dataFim Data fim (opcional)
     * @return Lista de preços
     */
    @Transactional(readOnly = true)
    public List<FuelPriceDay> listar(UUID tenantId, LocalDate dataInicio, LocalDate dataFim) {
        if (dataInicio != null && dataFim != null) {
            return fuelPriceDayRepository.findByTenantIdAndDataBetweenOrderByDataDesc(
                tenantId, dataInicio, dataFim
            );
        } else if (dataInicio != null) {
            return fuelPriceDayRepository.findByTenantIdAndDataGreaterThanEqualOrderByDataDesc(
                tenantId, dataInicio
            );
        } else if (dataFim != null) {
            return fuelPriceDayRepository.findByTenantIdAndDataLessThanEqualOrderByDataDesc(
                tenantId, dataFim
            );
        } else {
            return fuelPriceDayRepository.findByTenantIdOrderByDataDesc(tenantId);
        }
    }
}
