package com.jetski.comissoes.internal;

import com.jetski.comissoes.domain.*;
import com.jetski.comissoes.event.ComissaoCalculadaEvent;
import com.jetski.comissoes.internal.repository.ComissaoRepository;
import com.jetski.comissoes.internal.repository.PoliticaComissaoRepository;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.tenant.TenantQueryService;
import com.jetski.tenant.domain.ComissaoConfig;
import com.jetski.tenant.domain.Tenant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
public class CommissionService {

    private final ComissaoRepository comissaoRepository;
    private final PoliticaComissaoRepository politicaRepository;
    private final TenantQueryService tenantQueryService;
    private final ApplicationEventPublisher eventPublisher;

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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
        // Delegate to new method with null precoBaseHora (uses policy system)
        return calcularComissao(tenantId, locacaoId, vendedorId, modeloId, duracaoMinutos,
                valorTotalLocacao, valorCombustivel, valorMultas, valorTaxas, codigoCampanha, null);
    }

    /**
     * Calcula comissão para uma locação com suporte a comissão diferenciada
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
     * @param valorBaseLocacao Valor base esperado da locação (para determinar se venda foi acima/abaixo do base)
     * @return Comissão calculada
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
            String codigoCampanha,
            BigDecimal valorBaseLocacao
    ) {
        log.info("Calculando comissão para locação {} (vendedor: {}, modelo: {}, duração: {}min)",
                locacaoId, vendedorId, modeloId, duracaoMinutos);

        // 1. Buscar configuração do tenant
        ComissaoConfig tenantConfig = getTenantComissaoConfig(tenantId);

        // 2. Calcular valor comissionável (valor total - combustível - multas - taxas)
        // para determinar corretamente se venda foi acima ou abaixo do preço base
        BigDecimal valorComissionavel = valorTotalLocacao
                .subtract(valorCombustivel != null ? valorCombustivel : BigDecimal.ZERO)
                .subtract(valorMultas != null ? valorMultas : BigDecimal.ZERO)
                .subtract(valorTaxas != null ? valorTaxas : BigDecimal.ZERO);

        // 3. Determinar se venda foi acima ou abaixo do preço base
        // Compara valor_comissionavel (sem combustível/extras) com valor_base tabelado
        boolean vendaAcimaPrecoBase = determinarVendaAcimaBase(valorComissionavel, valorBaseLocacao);
        log.info("Determinação preço base: valorComissionavel={}, valorBase={}, acimaBase={}",
                valorComissionavel, valorBaseLocacao, vendaAcimaPrecoBase);

        // 4. Buscar política aplicável (hierarquia RN04)
        PoliticaComissao politica = selecionarPoliticaAplicavel(
                tenantId, vendedorId, modeloId, duracaoMinutos, codigoCampanha
        );

        if (politica == null && tenantConfig == null) {
            log.warn("Nenhuma política de comissão encontrada para locação {}", locacaoId);
            throw new BusinessException("Nenhuma política de comissão configurada para este vendedor/modelo");
        }

        // 5. Criar comissão
        Comissao comissao = Comissao.builder()
                .tenantId(tenantId)
                .locacaoId(locacaoId)
                .vendedorId(vendedorId)
                .politicaId(politica != null ? politica.getId() : null)
                .status(StatusComissao.PENDENTE)
                .dataLocacao(Instant.now())
                .valorTotalLocacao(valorTotalLocacao)
                .valorCombustivel(valorCombustivel != null ? valorCombustivel : BigDecimal.ZERO)
                .valorMultas(valorMultas != null ? valorMultas : BigDecimal.ZERO)
                .valorTaxas(valorTaxas != null ? valorTaxas : BigDecimal.ZERO)
                .tipoComissao(politica != null ? politica.getTipo() : TipoComissao.PERCENTUAL)
                .politicaNome(politica != null ? politica.getNome() : "Configuração Tenant")
                .politicaNivel(politica != null ? politica.getNivel() : null)
                .vendaAcimaPrecoBase(vendaAcimaPrecoBase)
                .build();

        // 6. Calcular valor comissionável na entidade (para persistência)
        comissao.calcularValorComissionavel();

        // 7. Determinar percentual a aplicar (tenant config tem prioridade se configurado)
        BigDecimal percentualAplicado;
        BigDecimal valorComissao;

        if (tenantConfig != null && tenantConfig.percentualPadrao() != null) {
            // Usar configuração do tenant (percentual diferenciado)
            if (vendaAcimaPrecoBase) {
                percentualAplicado = tenantConfig.percentualPadrao();
            } else {
                // Usar percentualAbaixoBase se disponível, senão usar percentualPadrao como fallback
                percentualAplicado = tenantConfig.percentualAbaixoBase() != null
                        ? tenantConfig.percentualAbaixoBase()
                        : tenantConfig.percentualPadrao();
            }

            valorComissao = comissao.getValorComissionavel()
                    .multiply(percentualAplicado)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            log.info("Usando config tenant: {}% (vendaAcimaBase: {}, percentualAbaixoBase: {})",
                    percentualAplicado, vendaAcimaPrecoBase, tenantConfig.percentualAbaixoBase());
        } else if (politica != null) {
            // Usar sistema de políticas tradicional
            valorComissao = calcularValorComissao(politica, comissao.getValorComissionavel(), duracaoMinutos);
            percentualAplicado = extrairPercentualAplicado(politica, duracaoMinutos);

            log.info("Usando política: {} (nível: {}, tipo: {})",
                    politica.getNome(), politica.getNivel(), politica.getTipo());
        } else {
            // Fallback para 10% padrão
            percentualAplicado = new BigDecimal("10.0");
            valorComissao = comissao.getValorComissionavel()
                    .multiply(percentualAplicado)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            log.info("Usando percentual padrão: 10%");
        }

        comissao.setPercentualAplicado(percentualAplicado);
        comissao.setValorComissao(valorComissao);

        Comissao comissaoSalva = comissaoRepository.save(comissao);
        log.info("Comissão calculada: R$ {} (comissionável: R$ {}, percentual: {}%, acimaBase: {})",
                comissaoSalva.getValorComissao(), comissaoSalva.getValorComissionavel(),
                percentualAplicado, vendaAcimaPrecoBase);

        // Verificar e criar bonus se venda foi acima do preço base
        if (vendaAcimaPrecoBase && vendedorId != null) {
            try {
                eventPublisher.publishEvent(new ComissaoCalculadaEvent(tenantId, vendedorId));
            } catch (Exception e) {
                // Log but don't fail the commission calculation
                log.warn("Erro ao verificar bonus para vendedor {}: {}", vendedorId, e.getMessage());
            }
        }

        return comissaoSalva;
    }

    /**
     * Obtém a configuração de comissão do tenant
     */
    private ComissaoConfig getTenantComissaoConfig(UUID tenantId) {
        Tenant tenant = tenantQueryService.findById(tenantId);
        return tenant != null ? tenant.getComissaoConfig() : null;
    }

    /**
     * Determina se a venda foi acima ou abaixo do preço base.
     * Compara o valor comissionável (sem combustível/extras) com o valor base tabelado.
     *
     * @param valorComissionavel Valor cobrado pela locação sem combustível/multas/taxas
     * @param valorBaseLocacao Valor base esperado (preço tabelado pela duração)
     * @return true se venda >= valor base, false se venda < valor base (com desconto)
     */
    private boolean determinarVendaAcimaBase(BigDecimal valorComissionavel, BigDecimal valorBaseLocacao) {
        if (valorBaseLocacao == null || valorComissionavel == null) {
            log.debug("Valores nulos, considerando venda no preço base: valorComissionavel={}, valorBase={}",
                    valorComissionavel, valorBaseLocacao);
            return true; // Default: considera como venda no preço base
        }

        // Venda acima do base se valor comissionável >= valor base tabelado
        boolean acimaBase = valorComissionavel.compareTo(valorBaseLocacao) >= 0;

        log.debug("Comparação de preço: valorComissionavel={}, valorBase={}, acimaBase={}",
                valorComissionavel, valorBaseLocacao, acimaBase);

        return acimaBase;
    }

    /**
     * Extrai o percentual aplicado de uma política
     */
    private BigDecimal extrairPercentualAplicado(PoliticaComissao politica, int duracaoMinutos) {
        if (politica.getTipo() == TipoComissao.PERCENTUAL) {
            return politica.getPercentualComissao();
        } else if (politica.getTipo() == TipoComissao.ESCALONADO) {
            return duracaoMinutos >= politica.getDuracaoMinMinutos()
                    ? politica.getPercentualExtra()
                    : politica.getPercentualComissao();
        }
        return null;
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
    @Transactional
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
    @Transactional
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

    // ========== RECÁLCULO DE COMISSÃO ==========

    /**
     * Recalcula ou cria comissão quando uma locação finalizada é editada.
     *
     * Cenários:
     * - Vendedor alterado: deleta comissão antiga, cria nova
     * - Vendedor adicionado: cria nova comissão
     * - Vendedor removido: deleta comissão existente
     * - Valores alterados: atualiza comissão existente
     *
     * @param tenantId Tenant ID
     * @param locacaoId Locação ID
     * @param vendedorIdAntigo Vendedor ID anterior (pode ser null)
     * @param vendedorIdNovo Vendedor ID novo (pode ser null)
     * @param modeloId Modelo do jetski
     * @param duracaoMinutos Duração em minutos faturáveis
     * @param valorTotalLocacao Valor total da locação
     * @param valorCombustivel Valor de combustível
     * @param valorBaseLocacao Valor base esperado da locação (para comissão diferenciada)
     */
    @Transactional
    public void recalcularComissaoLocacao(
            UUID tenantId,
            UUID locacaoId,
            UUID vendedorIdAntigo,
            UUID vendedorIdNovo,
            UUID modeloId,
            int duracaoMinutos,
            BigDecimal valorTotalLocacao,
            BigDecimal valorCombustivel,
            BigDecimal valorBaseLocacao
    ) {
        log.info("Recalculando comissão para locação {} (vendedorAntigo: {}, vendedorNovo: {})",
                locacaoId, vendedorIdAntigo, vendedorIdNovo);

        // Buscar comissão existente
        Optional<Comissao> comissaoExistente = comissaoRepository.findByTenantIdAndLocacaoId(tenantId, locacaoId);

        // Cenário 1: Vendedor foi removido
        if (vendedorIdNovo == null) {
            if (comissaoExistente.isPresent()) {
                Comissao comissao = comissaoExistente.get();
                // Só deleta se ainda estiver PENDENTE
                if (comissao.getStatus() == StatusComissao.PENDENTE) {
                    comissaoRepository.delete(comissao);
                    log.info("Comissão {} deletada (vendedor removido)", comissao.getId());
                } else {
                    log.warn("Comissão {} não pode ser deletada (status: {}). Mantendo com vendedor original.",
                            comissao.getId(), comissao.getStatus());
                }
            }
            return;
        }

        // Cenário 2: Vendedor alterado ou adicionado
        boolean vendedorAlterado = vendedorIdAntigo != null && !vendedorIdAntigo.equals(vendedorIdNovo);
        boolean vendedorAdicionado = vendedorIdAntigo == null && vendedorIdNovo != null;

        if (vendedorAlterado) {
            // Deletar comissão antiga se existir e estiver PENDENTE
            if (comissaoExistente.isPresent()) {
                Comissao comissaoAntiga = comissaoExistente.get();
                if (comissaoAntiga.getStatus() == StatusComissao.PENDENTE) {
                    comissaoRepository.delete(comissaoAntiga);
                    log.info("Comissão antiga {} deletada (vendedor alterado)", comissaoAntiga.getId());
                } else {
                    log.warn("Comissão {} não pode ser deletada (status: {}). Nova comissão será criada para novo vendedor.",
                            comissaoAntiga.getId(), comissaoAntiga.getStatus());
                    // Ainda assim, criar nova comissão para o novo vendedor
                }
            }
        }

        // Criar nova comissão para o vendedor (alterado ou adicionado)
        if (vendedorAlterado || vendedorAdicionado) {
            try {
                calcularComissao(
                        tenantId,
                        locacaoId,
                        vendedorIdNovo,
                        modeloId,
                        duracaoMinutos,
                        valorTotalLocacao,
                        valorCombustivel,
                        BigDecimal.ZERO,  // multas
                        BigDecimal.ZERO,  // taxas
                        null,  // codigoCampanha
                        valorBaseLocacao
                );
                log.info("Nova comissão criada para vendedor {} na locação {}", vendedorIdNovo, locacaoId);
            } catch (Exception e) {
                log.warn("Falha ao criar comissão para vendedor {}: {}", vendedorIdNovo, e.getMessage());
            }
            return;
        }

        // Cenário 3: Mesmo vendedor, mas valores podem ter mudado
        if (comissaoExistente.isPresent()) {
            Comissao comissao = comissaoExistente.get();

            // Só atualiza se ainda estiver PENDENTE
            if (comissao.getStatus() != StatusComissao.PENDENTE) {
                log.warn("Comissão {} não pode ser atualizada (status: {})", comissao.getId(), comissao.getStatus());
                return;
            }

            // Atualizar valores
            comissao.setValorTotalLocacao(valorTotalLocacao);
            comissao.setValorCombustivel(valorCombustivel != null ? valorCombustivel : BigDecimal.ZERO);

            // Recalcular valor comissionável e comissão
            comissao.calcularValorComissionavel();

            BigDecimal percentualAplicado = comissao.getPercentualAplicado();
            if (percentualAplicado != null) {
                BigDecimal valorComissao = comissao.getValorComissionavel()
                        .multiply(percentualAplicado)
                        .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                comissao.setValorComissao(valorComissao);
            }

            comissaoRepository.save(comissao);
            log.info("Comissão {} atualizada: R$ {} (comissionável: R$ {})",
                    comissao.getId(), comissao.getValorComissao(), comissao.getValorComissionavel());
        } else if (vendedorIdNovo != null) {
            // Não tinha comissão mas tem vendedor - criar
            try {
                calcularComissao(
                        tenantId,
                        locacaoId,
                        vendedorIdNovo,
                        modeloId,
                        duracaoMinutos,
                        valorTotalLocacao,
                        valorCombustivel,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        null,
                        valorBaseLocacao
                );
                log.info("Comissão criada para vendedor {} na locação {}", vendedorIdNovo, locacaoId);
            } catch (Exception e) {
                log.warn("Falha ao criar comissão: {}", e.getMessage());
            }
        }
    }

    // ========== PAGAMENTO EM LOTE ==========

    /**
     * DTO para resultado do pagamento em lote
     */
    public record PagamentoLoteResult(
            UUID vendedorId,
            String nomeVendedor,
            int qtdComissoesPagas,
            BigDecimal valorTotalPago,
            Instant dataHoraPagamento,
            String referenciaPagamento
    ) {}

    /**
     * Paga todas as comissões aprovadas de um vendedor em lote.
     *
     * @param tenantId Tenant ID
     * @param vendedorId Vendedor ID
     * @param pagoPor UUID do usuário que está pagando
     * @param referenciaPagamento Referência do pagamento (ex: PIX-2024-001)
     * @return Resultado do pagamento em lote
     */
    @Transactional
    public PagamentoLoteResult pagarComissoesVendedor(
            UUID tenantId,
            UUID vendedorId,
            UUID pagoPor,
            String referenciaPagamento
    ) {
        log.info("Iniciando pagamento em lote para vendedor: {} (ref: {})", vendedorId, referenciaPagamento);

        // 1. Buscar todas comissões APROVADAS do vendedor
        List<Comissao> comissoesAprovadas = comissaoRepository.findAprovadasByVendedor(tenantId, vendedorId);

        if (comissoesAprovadas.isEmpty()) {
            throw new BusinessException("Nenhuma comissão aprovada encontrada para este vendedor");
        }

        // 2. Marcar todas como PAGA
        BigDecimal valorTotal = BigDecimal.ZERO;
        Instant agora = Instant.now();

        for (Comissao comissao : comissoesAprovadas) {
            comissao.setStatus(StatusComissao.PAGA);
            comissao.setPagoPor(pagoPor);
            comissao.setPagoEm(agora);
            comissao.setReferenciaPagamento(referenciaPagamento);
            valorTotal = valorTotal.add(comissao.getValorComissao());
        }

        // 3. Salvar todas
        comissaoRepository.saveAll(comissoesAprovadas);

        // 4. Buscar nome do vendedor (através da primeira comissão)
        String nomeVendedor = "Vendedor " + vendedorId.toString().substring(0, 8);

        log.info("Pagamento em lote concluído: {} comissões, R$ {} (ref: {})",
                comissoesAprovadas.size(), valorTotal, referenciaPagamento);

        return new PagamentoLoteResult(
                vendedorId,
                nomeVendedor,
                comissoesAprovadas.size(),
                valorTotal,
                agora,
                referenciaPagamento
        );
    }
}
