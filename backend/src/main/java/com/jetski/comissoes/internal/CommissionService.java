package com.jetski.comissoes.internal;

import com.jetski.comissoes.domain.*;
import com.jetski.comissoes.internal.repository.ComissaoRepository;
import com.jetski.comissoes.internal.repository.PoliticaComissaoRepository;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for Commission calculation and management
 *
 * <p><strong>RN04 - Hierarquia de Políticas (primeiro match ganha):</strong></p>
 * <ol>
 *   <li>CAMPANHA - Promoções ativas com vigência</li>
 *   <li>MODELO - Específico por modelo de jetski</li>
 *   <li>DURACAO - Por faixa de duração da locação</li>
 *   <li>VENDEDOR - Padrão do vendedor</li>
 * </ol>
 *
 * <p><strong>Receita Comissionável:</strong></p>
 * <p>valor_total - combustível - multas - taxas (limpeza, danos)</p>
 *
 * @author Jetski Team
 * @since 0.7.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CommissionService {

    private final ComissaoRepository comissaoRepository;
    private final PoliticaComissaoRepository politicaRepository;

    /**
     * Calcula comissão para uma locação
     *
     * @param tenantId Tenant ID
     * @param locacaoId Locação ID
     * @param vendedorId Vendedor ID
     * @param modeloId Modelo do jetski
     * @param duracaoMinutos Duração da locação em minutos
     * @param valorTotalLocacao Valor total da locação
     * @param valorCombustivel Valor de combustível (não-comissionável)
     * @param valorMultas Valor de multas (não-comissionável)
     * @param valorTaxas Outras taxas não-comissionáveis
     * @param codigoCampanha Código de campanha ativa (opcional)
     * @return Comissão calculada
     */
    public Comissao calcularComissao(
            UUID tenantId,
            UUID locacaoId,
            UUID vendedorId,
            UUID modeloId,
            int duracaoMinutos,
            BigDecimal valorTotalLocacao,
            BigDecimal valorCombustivel,
            BigDecimal valorMultas,
            BigDecimal valorTaxas,
            String codigoCampanha
    ) {
        log.info("Calculando comissão para locação {} (vendedor: {}, modelo: {}, duração: {}min)",
                locacaoId, vendedorId, modeloId, duracaoMinutos);

        // 1. Buscar política aplicável (hierarquia RN04)
        PoliticaComissao politica = selecionarPoliticaAplicavel(
                tenantId, vendedorId, modeloId, duracaoMinutos, codigoCampanha
        );

        if (politica == null) {
            log.warn("Nenhuma política de comissão encontrada para locação {}", locacaoId);
            throw new BusinessException("Nenhuma política de comissão configurada para este vendedor/modelo");
        }

        log.info("Política selecionada: {} (nível: {}, tipo: {})",
                politica.getNome(), politica.getNivel(), politica.getTipo());

        // 2. Criar comissão
        Comissao comissao = Comissao.builder()
                .tenantId(tenantId)
                .locacaoId(locacaoId)
                .vendedorId(vendedorId)
                .politicaId(politica.getId())
                .status(StatusComissao.PENDENTE)
                .dataLocacao(Instant.now())
                .valorTotalLocacao(valorTotalLocacao)
                .valorCombustivel(valorCombustivel != null ? valorCombustivel : BigDecimal.ZERO)
                .valorMultas(valorMultas != null ? valorMultas : BigDecimal.ZERO)
                .valorTaxas(valorTaxas != null ? valorTaxas : BigDecimal.ZERO)
                .tipoComissao(politica.getTipo())
                .politicaNome(politica.getNome())
                .politicaNivel(politica.getNivel())
                .build();

        // 3. Calcular valor comissionável
        comissao.calcularValorComissionavel();

        // 4. Calcular valor da comissão baseado no tipo
        BigDecimal valorComissao = calcularValorComissao(politica, comissao.getValorComissionavel(), duracaoMinutos);
        comissao.setValorComissao(valorComissao);

        // 5. Registrar percentual aplicado (se aplicável)
        if (politica.getTipo() == TipoComissao.PERCENTUAL) {
            comissao.setPercentualAplicado(politica.getPercentualComissao());
        } else if (politica.getTipo() == TipoComissao.ESCALONADO) {
            BigDecimal percentual = duracaoMinutos >= politica.getDuracaoMinMinutos()
                    ? politica.getPercentualExtra()
                    : politica.getPercentualComissao();
            comissao.setPercentualAplicado(percentual);
        }

        Comissao comissaoSalva = comissaoRepository.save(comissao);
        log.info("Comissão calculada: R$ {} (comissionável: R$ {}, tipo: {})",
                comissaoSalva.getValorComissao(), comissaoSalva.getValorComissionavel(), politica.getTipo());

        return comissaoSalva;
    }

    /**
     * Seleciona política aplicável seguindo hierarquia RN04
     */
    private PoliticaComissao selecionarPoliticaAplicavel(
            UUID tenantId,
            UUID vendedorId,
            UUID modeloId,
            int duracaoMinutos,
            String codigoCampanha
    ) {
        // 1. CAMPANHA (prioridade 1) - se código fornecido
        if (codigoCampanha != null && !codigoCampanha.isBlank()) {
            List<PoliticaComissao> campanhas = politicaRepository.findCampanhaAtiva(
                    tenantId, codigoCampanha, Instant.now()
            );
            Optional<PoliticaComissao> campanha = campanhas.stream()
                    .filter(p -> p.aplicaParaDuracao(duracaoMinutos))
                    .findFirst();
            if (campanha.isPresent()) {
                log.debug("Política CAMPANHA aplicada: {}", campanha.get().getNome());
                return campanha.get();
            }
        }

        // 2. MODELO (prioridade 2)
        List<PoliticaComissao> politicasModelo = politicaRepository.findByTenantIdAndNivelAndModeloIdAndAtiva(
                tenantId, NivelPolitica.MODELO, modeloId, true
        );
        Optional<PoliticaComissao> modelo = politicasModelo.stream()
                .filter(p -> p.aplicaParaDuracao(duracaoMinutos))
                .findFirst();
        if (modelo.isPresent()) {
            log.debug("Política MODELO aplicada: {}", modelo.get().getNome());
            return modelo.get();
        }

        // 3. DURACAO (prioridade 3)
        List<PoliticaComissao> politicasDuracao = politicaRepository
                .findByTenantIdAndNivelAndAtivaOrderByDuracaoMinMinutosDesc(
                        tenantId, NivelPolitica.DURACAO, true
                );
        Optional<PoliticaComissao> duracao = politicasDuracao.stream()
                .filter(p -> p.aplicaParaDuracao(duracaoMinutos))
                .findFirst();
        if (duracao.isPresent()) {
            log.debug("Política DURACAO aplicada: {}", duracao.get().getNome());
            return duracao.get();
        }

        // 4. VENDEDOR (prioridade 4 - padrão)
        List<PoliticaComissao> politicasVendedor = politicaRepository.findByTenantIdAndNivelAndVendedorIdAndAtiva(
                tenantId, NivelPolitica.VENDEDOR, vendedorId, true
        );
        Optional<PoliticaComissao> vendedor = politicasVendedor.stream()
                .filter(p -> p.aplicaParaDuracao(duracaoMinutos))
                .findFirst();
        if (vendedor.isPresent()) {
            log.debug("Política VENDEDOR aplicada: {}", vendedor.get().getNome());
            return vendedor.get();
        }

        // Nenhuma política encontrada
        return null;
    }

    /**
     * Calcula valor da comissão baseado no tipo da política
     */
    private BigDecimal calcularValorComissao(
            PoliticaComissao politica,
            BigDecimal valorComissionavel,
            int duracaoMinutos
    ) {
        switch (politica.getTipo()) {
            case PERCENTUAL:
                return valorComissionavel
                        .multiply(politica.getPercentualComissao())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            case VALOR_FIXO:
                return politica.getValorFixo();

            case ESCALONADO:
                BigDecimal percentual;
                if (duracaoMinutos >= politica.getDuracaoMinMinutos()) {
                    percentual = politica.getPercentualExtra(); // Acima da duração = percentual extra
                } else {
                    percentual = politica.getPercentualComissao(); // Abaixo = percentual base
                }
                return valorComissionavel
                        .multiply(percentual)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            default:
                throw new BusinessException("Tipo de comissão não suportado: " + politica.getTipo());
        }
    }

    /**
     * Aprova comissão (GERENTE)
     */
    public Comissao aprovarComissao(UUID tenantId, UUID comissaoId, UUID aprovadoPor) {
        Comissao comissao = buscarPorId(tenantId, comissaoId);

        if (!comissao.podeAprovar()) {
            throw new BusinessException(
                    String.format("Comissão não pode ser aprovada. Status atual: %s", comissao.getStatus())
            );
        }

        comissao.setStatus(StatusComissao.APROVADA);
        comissao.setAprovadoPor(aprovadoPor);
        comissao.setAprovadoEm(Instant.now());

        Comissao comissaoSalva = comissaoRepository.save(comissao);
        log.info("Comissão {} aprovada por {} (valor: R$ {})",
                comissaoId, aprovadoPor, comissaoSalva.getValorComissao());

        return comissaoSalva;
    }

    /**
     * Marca comissão como paga (FINANCEIRO)
     */
    public Comissao marcarComoPaga(UUID tenantId, UUID comissaoId, UUID pagoPor, String referenciaPagamento) {
        Comissao comissao = buscarPorId(tenantId, comissaoId);

        if (!comissao.podePagar()) {
            throw new BusinessException("Apenas comissões aprovadas podem ser pagas");
        }

        comissao.setStatus(StatusComissao.PAGA);
        comissao.setPagoPor(pagoPor);
        comissao.setPagoEm(Instant.now());
        comissao.setReferenciaPagamento(referenciaPagamento);

        Comissao comissaoSalva = comissaoRepository.save(comissao);
        log.info("Comissão {} marcada como paga por {} (ref: {})",
                comissaoId, pagoPor, referenciaPagamento);

        return comissaoSalva;
    }

    /**
     * Busca comissão por ID
     */
    @Transactional(readOnly = true)
    public Comissao buscarPorId(UUID tenantId, UUID comissaoId) {
        return comissaoRepository.findById(comissaoId)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new NotFoundException("Comissão não encontrada: " + comissaoId));
    }

    /**
     * Busca comissões pendentes de aprovação
     */
    @Transactional(readOnly = true)
    public List<Comissao> listarPendentesAprovacao(UUID tenantId) {
        return comissaoRepository.findPendentesAprovacao(tenantId);
    }

    /**
     * Busca comissões aprovadas aguardando pagamento
     */
    @Transactional(readOnly = true)
    public List<Comissao> listarAguardandoPagamento(UUID tenantId) {
        return comissaoRepository.findAguardandoPagamento(tenantId);
    }

    /**
     * Busca comissões de um vendedor
     */
    @Transactional(readOnly = true)
    public List<Comissao> listarPorVendedor(UUID tenantId, UUID vendedorId) {
        return comissaoRepository.findByTenantIdAndVendedorIdOrderByDataLocacaoDesc(tenantId, vendedorId);
    }

    /**
     * Busca comissões por período (para fechamento mensal)
     */
    @Transactional(readOnly = true)
    public List<Comissao> buscarPorPeriodo(UUID tenantId, Instant inicio, Instant fim) {
        return comissaoRepository.findByPeriodo(tenantId, inicio, fim);
    }
}
