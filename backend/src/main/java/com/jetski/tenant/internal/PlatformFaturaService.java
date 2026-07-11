package com.jetski.tenant.internal;

import com.jetski.shared.email.EmailService;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.pix.BrCodePix;
import com.jetski.shared.security.TenantContext;
import com.jetski.tenant.domain.Fatura;
import com.jetski.tenant.internal.repository.FaturaRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Billing manual assistido — lado da PLATAFORMA (super admin):
 *
 * <ul>
 *   <li>{@link #gerarFaturasDoMes()} (job, idempotente): fatura da competência
 *       corrente para cada tenant ATIVO com assinatura ativa de plano PAGO
 *       (Trial/preço 0 não fatura), com PIX da plataforma (mesma chave dos
 *       créditos) e e-mail best-effort ao admin da empresa;</li>
 *   <li>{@link #pendentesConferencia()}: fila global (iteração por tenant,
 *       padrão {@code PlatformCreditoService});</li>
 *   <li>{@link #confirmar}/{@link #cancelar}: decisão humana pós-extrato;</li>
 *   <li>{@link #suspenderInadimplentes()} (job): ABERTA vencida além da
 *       carência → suspensão automática (padrão do trial);</li>
 *   <li>{@link #mudarPlano}: encerra a assinatura ativa e cria a nova — o
 *       caminho de contratação/upgrade (decisão humana no painel).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformFaturaService {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MM/yyyy");

    private final FaturaRepository faturaRepository;
    private final PlatformTenantService platformTenantService;
    private final EmailService emailService;
    private final EntityManager entityManager;

    /** Mesma chave PIX da plataforma usada na venda de créditos. */
    @Value("${jetski.creditos.pix-chave:pix@meujet.com.br}")
    private String pixChave;
    @Value("${jetski.creditos.pix-nome:Meu Jet}")
    private String pixNome;
    @Value("${jetski.creditos.pix-cidade:Florianopolis}")
    private String pixCidade;

    /** Dias entre a emissão e o vencimento da fatura. */
    @Value("${jetski.faturamento.vencimento-dias:10}")
    private int vencimentoDias;
    /** Dias de carência após o vencimento antes da suspensão automática. */
    @Value("${jetski.faturamento.carencia-dias:7}")
    private int carenciaDias;

    public record FaturaPlataforma(Fatura fatura, String slug, String razaoSocial) {}

    /** Gera (idempotente) as faturas da competência corrente. @return quantas criou. */
    @Transactional
    public int gerarFaturasDoMes() {
        LocalDate competencia = LocalDate.now(ZONA).withDayOfMonth(1);
        int criadas = 0;
        for (Object[] t : tenantsFaturaveis()) {
            UUID tenantId = (UUID) t[0];
            String planoNome = (String) t[3];
            BigDecimal valor = (BigDecimal) t[4];
            setTenant(tenantId);
            if (faturaRepository.existsByTenantIdAndCompetencia(tenantId, competencia)) {
                continue;
            }
            LocalDate vencimento = LocalDate.now(ZONA).plusDays(vencimentoDias);
            Fatura fatura = faturaRepository.save(Fatura.builder()
                .tenantId(tenantId)
                .competencia(competencia)
                .planoNome(planoNome)
                .valor(valor)
                .vencimento(vencimento)
                .pixCopiaECola(BrCodePix.gerar(pixChave.trim(), valor, pixNome, pixCidade))
                .build());
            criadas++;
            notificarFaturaGerada(tenantId, (String) t[2], fatura);
            log.info("[PLATFORM] Fatura gerada: tenant={} ({}), competencia={}, valor={}",
                tenantId, t[1], MES.format(competencia), valor);
        }
        return criadas;
    }

    /** Fila global de conferência (EM_CONFERENCIA) + abertas vencidas, por tenant. */
    @Transactional
    public List<FaturaPlataforma> pendentesConferencia() {
        List<FaturaPlataforma> resultado = new ArrayList<>();
        for (Object[] t : todosOsTenants()) {
            UUID tenantId = (UUID) t[0];
            setTenant(tenantId);
            for (Fatura f : faturaRepository.findByTenantIdAndStatus(
                    tenantId, Fatura.Status.EM_CONFERENCIA)) {
                resultado.add(new FaturaPlataforma(f, (String) t[1], (String) t[2]));
            }
        }
        return resultado;
    }

    /** Confirma o pagamento (conferido no extrato) → PAGA. */
    @Transactional
    public Fatura confirmar(UUID tenantId, UUID faturaId) {
        setTenant(tenantId);
        Fatura fatura = carregar(tenantId, faturaId);
        if (fatura.getStatus() == Fatura.Status.PAGA) {
            return fatura;
        }
        if (fatura.getStatus() == Fatura.Status.CANCELADA) {
            throw new BusinessException("Fatura cancelada não pode ser confirmada");
        }
        fatura.setStatus(Fatura.Status.PAGA);
        fatura.setPagoEm(Instant.now());
        fatura.setDecididoPor(TenantContext.getUsuarioId());
        log.info("[PLATFORM] Fatura PAGA: tenant={}, fatura={}, competencia={}",
            tenantId, faturaId, fatura.getCompetencia());
        return faturaRepository.save(fatura);
    }

    /** Cancela a fatura (cortesia/erro) com observação obrigatória. */
    @Transactional
    public Fatura cancelar(UUID tenantId, UUID faturaId, String observacao) {
        if (observacao == null || observacao.trim().isEmpty()) {
            throw new BusinessException("Observação é obrigatória ao cancelar uma fatura");
        }
        setTenant(tenantId);
        Fatura fatura = carregar(tenantId, faturaId);
        if (fatura.getStatus() == Fatura.Status.PAGA) {
            throw new BusinessException("Fatura paga não pode ser cancelada");
        }
        fatura.setStatus(Fatura.Status.CANCELADA);
        fatura.setObservacao(observacao.trim());
        fatura.setDecididoPor(TenantContext.getUsuarioId());
        return faturaRepository.save(fatura);
    }

    /**
     * Suspende tenants com fatura ABERTA vencida além da carência.
     * EM_CONFERENCIA não suspende (pagamento aguardando conferência humana).
     */
    @Transactional
    public int suspenderInadimplentes() {
        LocalDate limite = LocalDate.now(ZONA).minusDays(carenciaDias);
        int suspensos = 0;
        for (Object[] t : todosOsTenants()) {
            UUID tenantId = (UUID) t[0];
            if (!"ATIVO".equals(t[3])) {
                continue; // já suspenso/trial/excluído — nada a fazer
            }
            setTenant(tenantId);
            boolean inadimplente = faturaRepository
                .findByTenantIdAndStatus(tenantId, Fatura.Status.ABERTA).stream()
                .anyMatch(f -> f.getVencimento().isBefore(limite));
            if (inadimplente) {
                try {
                    platformTenantService.suspend(tenantId,
                        "Fatura da assinatura vencida há mais de " + carenciaDias
                        + " dias sem pagamento. Regularize para reativar o acesso.");
                    suspensos++;
                    log.warn("[PLATFORM] Tenant suspenso por inadimplência: {} ({})", tenantId, t[1]);
                } catch (Exception e) {
                    log.error("[PLATFORM] Falha ao suspender inadimplente {}: {}",
                        tenantId, e.getMessage());
                }
            }
        }
        return suspensos;
    }

    /**
     * Troca o plano do tenant: expira a assinatura ativa e cria a nova (ciclo
     * mensal, sem dt_fim — plano pago não expira sozinho; a inadimplência é
     * quem suspende). Caminho de contratação pós-trial e de upgrade/downgrade.
     */
    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = "plano-modulos", allEntries = true)
    public void mudarPlano(UUID tenantId, Integer planoId) {
        Object nome;
        try {
            nome = entityManager.createNativeQuery(
                    "SELECT nome FROM plano WHERE id = :pid")
                .setParameter("pid", planoId)
                .getSingleResult();
        } catch (Exception e) {
            throw new NotFoundException("Plano não encontrado: " + planoId);
        }
        entityManager.createNativeQuery(
                "UPDATE assinatura SET status = 'expirada', dt_fim = CURRENT_DATE, "
                + "updated_at = now() WHERE tenant_id = :tid AND status = 'ativa'")
            .setParameter("tid", tenantId)
            .executeUpdate();
        entityManager.createNativeQuery(
                "INSERT INTO assinatura (tenant_id, plano_id, ciclo, dt_inicio, status) "
                + "VALUES (:tid, :pid, 'mensal', CURRENT_DATE, 'ativa')")
            .setParameter("tid", tenantId)
            .setParameter("pid", planoId)
            .executeUpdate();
        log.warn("[PLATFORM] Plano alterado: tenant={}, novoPlano={} ({})",
            tenantId, planoId, nome);
    }

    /** Planos ativos para o seletor do painel de plataforma. */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<java.util.Map<String, Object>> planosAtivos() {
        List<Object[]> rows = entityManager.createNativeQuery(
                "SELECT id, nome, preco_mensal, limites::text, modulos::text FROM plano ORDER BY preco_mensal")
            .getResultList();
        return rows.stream()
            .map(r -> {
                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("id", r[0].toString());
                m.put("nome", r[1]);
                m.put("precoMensal", r[2]);
                m.put("limites", r[3]);
                m.put("modulos", r[4]); // JSON text ou null (= todos)
                return m;
            })
            .toList();
    }

    /**
     * Define os módulos do plano (controle de oferta). Lista vazia é inválida
     * (plano sem nenhum módulo não faz sentido — use null/todos ou escolha).
     * Chaves validadas contra o catálogo {@link com.jetski.tenant.ModuloPlano}.
     */
    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = "plano-modulos", allEntries = true)
    public void salvarModulos(Integer planoId, List<String> modulos) {
        if (modulos == null || modulos.isEmpty()) {
            throw new BusinessException("Selecione ao menos um módulo (ou todos)");
        }
        for (String m : modulos) {
            try {
                com.jetski.tenant.ModuloPlano.valueOf(m);
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Módulo desconhecido: " + m);
            }
        }
        boolean todos = modulos.size() == com.jetski.tenant.ModuloPlano.values().length;
        String json = todos ? null
            : "[" + modulos.stream().map(m -> "\"" + m + "\"")
                .collect(java.util.stream.Collectors.joining(",")) + "]";
        int n = entityManager.createNativeQuery(
                "UPDATE plano SET modulos = CAST(:json AS jsonb) WHERE id = :pid")
            .setParameter("json", json)
            .setParameter("pid", planoId)
            .executeUpdate();
        if (n == 0) {
            throw new NotFoundException("Plano não encontrado: " + planoId);
        }
        log.warn("[PLATFORM] Módulos do plano {} atualizados: {}", planoId,
            todos ? "TODOS" : modulos);
    }

    // ------------------------------------------------------------------

    /** Tenants ATIVOS com assinatura ativa de plano pago (preco_mensal > 0). */
    @SuppressWarnings("unchecked")
    private List<Object[]> tenantsFaturaveis() {
        return entityManager.createNativeQuery("""
                SELECT t.id, t.slug, t.razao_social, p.nome, p.preco_mensal
                  FROM tenant t
                  JOIN assinatura a ON a.tenant_id = t.id AND a.status = 'ativa'
                  JOIN plano p ON p.id = a.plano_id
                 WHERE t.status = 'ATIVO' AND p.preco_mensal > 0
                 ORDER BY t.razao_social
                """).getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> todosOsTenants() {
        return entityManager.createNativeQuery(
                "SELECT id, slug, razao_social, status FROM tenant "
                + "WHERE status <> 'EXCLUIDO' ORDER BY razao_social")
            .getResultList();
    }

    private Fatura carregar(UUID tenantId, UUID faturaId) {
        return faturaRepository.findByIdAndTenantId(faturaId, tenantId)
            .orElseThrow(() -> new NotFoundException("Fatura não encontrada: " + faturaId));
    }

    /** RLS transaction-local (padrão PlatformCreditoService/TrialExpirationService). */
    private void setTenant(UUID tenantId) {
        entityManager.createNativeQuery("SELECT set_config('app.tenant_id', :tid, true)")
            .setParameter("tid", tenantId.toString())
            .getSingleResult();
    }

    /** E-mail best-effort ao admin da empresa com valor, vencimento e PIX. */
    private void notificarFaturaGerada(UUID tenantId, String razaoSocial, Fatura f) {
        try {
            List<?> emails = entityManager.createNativeQuery(
                    "SELECT u.email FROM usuario u JOIN membro m ON m.usuario_id = u.id "
                    + "WHERE m.tenant_id = :tid AND m.ativo = true "
                    + "AND 'ADMIN_TENANT' = ANY(m.papeis) ORDER BY m.created_at LIMIT 1")
                .setParameter("tid", tenantId)
                .getResultList();
            if (emails.isEmpty()) {
                return;
            }
            String valorBr = f.getValor().toPlainString().replace('.', ',');
            String html = "<p>Olá!</p>"
                + "<p>A fatura da sua assinatura Meu Jet foi gerada:</p><ul>"
                + "<li><b>Competência:</b> " + MES.format(f.getCompetencia()) + "</li>"
                + "<li><b>Plano:</b> " + f.getPlanoNome() + "</li>"
                + "<li><b>Valor:</b> R$ " + valorBr + "</li>"
                + "<li><b>Vencimento:</b> "
                + f.getVencimento().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + "</li></ul>"
                + "<p><b>PIX copia-e-cola:</b></p>"
                + "<p style=\"word-break:break-all;font-family:monospace\">"
                + f.getPixCopiaECola() + "</p>"
                + "<p>Depois de pagar, informe o número da transação em "
                + "<b>Plano e faturas</b> no painel — a confirmação é feita pela nossa equipe.</p>"
                + "<p>Meu Jet</p>";
            emailService.sendEmail((String) emails.get(0),
                "Fatura Meu Jet " + MES.format(f.getCompetencia()) + " — " + razaoSocial, html);
        } catch (Exception e) {
            log.warn("E-mail da fatura falhou (tenant={}): {}", tenantId, e.getMessage());
        }
    }
}
