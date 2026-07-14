package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.VinculoEmissao;
import com.jetski.locacoes.internal.repository.VinculoEmissaoRepository;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.ConflictException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.security.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Parceria de emissão delegada (EMISSAO_DELEGADA_SPEC §4): convite/aceite
 * bilateral com termo, kill switch da EAMA, estorno anti-fraude do bônus da
 * operadora e resolução do contexto do emissor na emissão.
 *
 * <p><b>RLS</b>: tenant e instrutor do PARCEIRO não são legíveis na sessão do
 * tenant corrente. As leituras/escritas do outro lado rodam em janelas
 * {@code set_config('app.tenant_id', ..., true)} restauradas em finally; o
 * lookup por slug (antes de conhecer o id do parceiro) usa uma janela
 * {@code app.unrestricted} com SELECT de colunas explicitamente limitadas
 * (nunca segredos como smtp_password).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VinculoEmissaoService {

    /** Versão corrente do termo — snapshot gravado no vínculo a cada aceite. */
    public static final String TERMO_RESPONSABILIDADE = """
        TERMO DE RESPONSABILIDADE — EMISSÃO DELEGADA (v1)
        A EAMA emissora declara que: (i) mantém registro válido na Capitania informada em seu \
        cadastro; (ii) seus instrutores credenciados realizarão e atestarão as demonstrações \
        práticas (Anexo 5-B-1) das emissões feitas em seu nome pela operadora parceira; \
        (iii) responde, perante a Marinha do Brasil, pelos documentos emitidos em seu nome por \
        meio desta parceria, podendo bloqueá-la a qualquer momento. A operadora declara que \
        utilizará a parceria exclusivamente para emissões reais, com demonstração prática \
        realizada, e que seus créditos de bônus de adesão serão estornados na ativação da \
        parceria (anti-fraude). O aceite fica registrado com autor, data/hora e trilha imutável.""";

    private static final EnumSet<VinculoEmissao.Status> VIVOS =
        EnumSet.of(VinculoEmissao.Status.CONVIDADO, VinculoEmissao.Status.ATIVO,
            VinculoEmissao.Status.BLOQUEADO);

    /** Papel do CONVIDANTE no vínculo proposto. */
    public enum PapelConvite { OPERADORA, EMISSORA }

    private final VinculoEmissaoRepository repository;
    private final com.jetski.locacoes.internal.repository.VinculoEmissaoInstrutorRepository designacaoRepository;
    private final com.jetski.locacoes.internal.repository.InstrutorRepository instrutorRepository;
    private final com.jetski.creditos.CreditoService creditoService;
    private final EntityManager entityManager;

    /** Dados mínimos do parceiro lidos na janela unrestricted (sem segredos). */
    public record ParceiroInfo(UUID id, String slug, String razaoSocial,
                               UUID capitaniaId, boolean emissoraHabilitada) {}

    /** Contexto do emissor resolvido para uma emissão delegada (snapshot vivo). */
    public record DelegacaoContext(
        UUID vinculoId, UUID emissorTenantId,
        String razaoSocial, String cnpj, String cidade, String uf,
        String capitaniaCodigo, String marinhaEmail, String contatoEmail,
        UUID instrutorId, String instrutorNome, String instrutorRg,
        String instrutorOrgaoEmissor, String instrutorCpf, String instrutorCha,
        java.time.LocalDate instrutorDataEmissao, String instrutorAssinaturaS3Key) {}

    // ==================== ciclo de vida do vínculo ====================

    @Transactional
    public VinculoEmissao convidar(UUID tenantId, String parceiroSlug, PapelConvite papel) {
        if (parceiroSlug == null || parceiroSlug.isBlank()) {
            throw new BusinessException("Informe o identificador (slug) da empresa parceira");
        }
        ParceiroInfo parceiro = lookupParceiroPorSlug(parceiroSlug.trim().toLowerCase());
        if (parceiro == null) {
            throw new NotFoundException("Empresa parceira não encontrada: " + parceiroSlug);
        }
        if (parceiro.id().equals(tenantId)) {
            throw new BusinessException("A parceria de emissão exige duas empresas diferentes");
        }
        UUID operadorId = papel == PapelConvite.OPERADORA ? tenantId : parceiro.id();
        UUID emissorId = papel == PapelConvite.OPERADORA ? parceiro.id() : tenantId;

        validarPar(tenantId, parceiro, operadorId, emissorId);

        if (repository.existsByTenantOperadorIdAndStatusIn(operadorId, VIVOS)) {
            throw new ConflictException("A operadora já tem uma parceria de emissão em andamento "
                + "(convide de novo após revogá-la)");
        }
        try {
            VinculoEmissao v = repository.saveAndFlush(VinculoEmissao.builder()
                .tenantOperadorId(operadorId)
                .tenantEmissorId(emissorId)
                .status(VinculoEmissao.Status.CONVIDADO)
                .convidadoPorTenant(tenantId)
                .convidadoPor(actorOrNull())
                .convidadoEm(Instant.now())
                .build());
            log.info("Vínculo de emissão convidado: {} (operadora={}, emissora={}, por tenant={})",
                v.getId(), operadorId, emissorId, tenantId);
            return v;
        } catch (DataIntegrityViolationException e) {
            // corrida com o unique parcial (1 vínculo vivo por operadora)
            throw new ConflictException("A operadora já tem uma parceria de emissão em andamento");
        }
    }

    @Transactional
    public VinculoEmissao aceitar(UUID tenantId, UUID vinculoId, boolean termoAceito) {
        VinculoEmissao v = requireParticipante(tenantId, vinculoId);
        if (v.getStatus() != VinculoEmissao.Status.CONVIDADO) {
            throw new ConflictException("Convite não está mais pendente (status: " + v.getStatus() + ")");
        }
        if (tenantId.equals(v.getConvidadoPorTenant())) {
            throw new BusinessException("Quem convidou não pode aceitar — o aceite é da empresa convidada");
        }
        if (!termoAceito) {
            throw new BusinessException("O aceite do termo de responsabilidade é obrigatório");
        }
        // Revalida no aceite (o cadastro pode ter mudado desde o convite)
        ParceiroInfo emissor = lookupParceiroPorId(v.getTenantEmissorId());
        ParceiroInfo operador = lookupParceiroPorId(v.getTenantOperadorId());
        if (emissor == null || operador == null) {
            throw new BusinessException("Empresa da parceria não está mais disponível");
        }
        validarMesmaCapitania(operador, emissor);
        validarEmissorHabilitado(emissor);

        v.setStatus(VinculoEmissao.Status.ATIVO);
        v.setAceitoPor(actorOrNull());
        v.setAceitoEm(Instant.now());
        v.setTermoAceiteEm(Instant.now());
        v.setTermoTexto(TERMO_RESPONSABILIDADE);
        repository.save(v);

        // Anti-fraude (§4.1.4/§8.H): zera o bônus de adesão da OPERADORA na
        // ativação — estorno append-only, idempotente por vínculo, créditos
        // comprados preservados. Roda na janela RLS da operadora (o aceite
        // pode estar vindo da sessão da emissora).
        comTenant(v.getTenantOperadorId(), () -> {
            int estornado = creditoService.estornarBonusDelegacao(
                v.getTenantOperadorId(), v.getId(), actorOrNull());
            if (estornado > 0) {
                log.info("Bônus da operadora {} estornado na ativação do vínculo {}: {} créditos",
                    v.getTenantOperadorId(), v.getId(), estornado);
            }
            return null;
        });

        log.info("Vínculo de emissão ATIVO: {} (aceito pelo tenant {})", v.getId(), tenantId);
        return v;
    }

    /** Kill switch da EAMA (§4.3): só o emissor bloqueia/libera; efeito imediato. */
    @Transactional
    public VinculoEmissao bloquear(UUID tenantId, UUID vinculoId) {
        VinculoEmissao v = requireParticipante(tenantId, vinculoId);
        if (!tenantId.equals(v.getTenantEmissorId())) {
            throw new BusinessException("Somente a EAMA emissora pode bloquear a emissão em seu nome");
        }
        if (v.getStatus() != VinculoEmissao.Status.ATIVO) {
            throw new ConflictException("Só parcerias ativas podem ser bloqueadas (status: " + v.getStatus() + ")");
        }
        v.setStatus(VinculoEmissao.Status.BLOQUEADO);
        v.setBloqueadoEm(Instant.now());
        repository.save(v);
        log.info("Vínculo de emissão BLOQUEADO pela emissora: {} (tenant {})", vinculoId, tenantId);
        return v;
    }

    @Transactional
    public VinculoEmissao liberar(UUID tenantId, UUID vinculoId) {
        VinculoEmissao v = requireParticipante(tenantId, vinculoId);
        if (!tenantId.equals(v.getTenantEmissorId())) {
            throw new BusinessException("Somente a EAMA emissora pode liberar a emissão em seu nome");
        }
        if (v.getStatus() != VinculoEmissao.Status.BLOQUEADO) {
            throw new ConflictException("Só parcerias bloqueadas podem ser liberadas (status: " + v.getStatus() + ")");
        }
        v.setStatus(VinculoEmissao.Status.ATIVO);
        v.setBloqueadoEm(null);
        repository.save(v);
        log.info("Vínculo de emissão LIBERADO pela emissora: {} (tenant {})", vinculoId, tenantId);
        return v;
    }

    /** Revogação unilateral (qualquer lado), terminal. Não devolve bônus estornado. */
    @Transactional
    public VinculoEmissao revogar(UUID tenantId, UUID vinculoId) {
        VinculoEmissao v = requireParticipante(tenantId, vinculoId);
        if (v.getStatus() == VinculoEmissao.Status.REVOGADO) {
            throw new ConflictException("Parceria já revogada");
        }
        v.setStatus(VinculoEmissao.Status.REVOGADO);
        v.setRevogadoPor(actorOrNull());
        v.setRevogadoEm(Instant.now());
        repository.save(v);
        log.info("Vínculo de emissão REVOGADO: {} (pelo tenant {})", vinculoId, tenantId);
        return v;
    }

    @Transactional(readOnly = true)
    public List<VinculoEmissao> listar(UUID tenantId) {
        return repository.findByTenantOperadorIdOrTenantEmissorIdOrderByCreatedAtDesc(tenantId, tenantId);
    }

    /** Nome de exibição do parceiro (razão social) — leitura limitada, sem segredos. */
    @Transactional(readOnly = true)
    public String nomeDoTenant(UUID tenantId) {
        ParceiroInfo p = lookupParceiroPorId(tenantId);
        return p != null ? p.razaoSocial() : null;
    }

    // ==================== emissão delegada ====================

    /** Condição de designação (V049): sem designação p/ o vínculo = todos; com = só os designados. */
    private static final String COND_DESIGNADO =
        " AND (NOT EXISTS (SELECT 1 FROM vinculo_emissao_instrutor d WHERE d.vinculo_id = :vinculoId)"
        + " OR EXISTS (SELECT 1 FROM vinculo_emissao_instrutor d "
        + "WHERE d.vinculo_id = :vinculoId AND d.instrutor_id = i.id))";

    /**
     * Instrutores (id + nome) da EAMA parceira para o fluxo de emissão da
     * operadora. Exposição mínima — CPF/RG/CHA entram no PDF pelo serviço,
     * nunca pela UI da operadora (LGPD, §5.4). Respeita a designação por
     * parceria (V049): a operadora NÃO vê todos os instrutores quando a EAMA
     * designou um subconjunto.
     */
    @Transactional(readOnly = true)
    public List<Object[]> instrutoresDoParceiro(UUID operadoraTenantId) {
        VinculoEmissao v = vinculoVivoDaOperadora(operadoraTenantId);
        if (v == null || v.getStatus() != VinculoEmissao.Status.ATIVO) {
            throw new BusinessException("Não há parceria de emissão ativa com uma EAMA");
        }
        return comTenant(v.getTenantEmissorId(), () -> {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = entityManager.createNativeQuery(
                    "SELECT i.id, i.nome FROM instrutor i "
                    + "WHERE i.tenant_id = :emissorId AND i.ativo = true"
                    + COND_DESIGNADO + " ORDER BY i.nome")
                .setParameter("emissorId", v.getTenantEmissorId())
                .setParameter("vinculoId", v.getId())
                .getResultList();
            return rows;
        });
    }

    /**
     * Define (substituindo o conjunto) quais instrutores da EAMA atendem esta
     * parceria. Só o EMISSOR designa; lista vazia volta ao padrão "todos os
     * ativos". Todos os ids precisam ser instrutores ATIVOS da própria EAMA.
     */
    @Transactional
    public List<Object[]> designarInstrutores(UUID tenantId, UUID vinculoId, List<UUID> instrutorIds) {
        VinculoEmissao v = requireParticipante(tenantId, vinculoId);
        if (!tenantId.equals(v.getTenantEmissorId())) {
            throw new BusinessException("Somente a EAMA emissora designa os instrutores da parceria");
        }
        if (v.getStatus() == VinculoEmissao.Status.REVOGADO) {
            throw new ConflictException("Parceria revogada não recebe designações");
        }
        List<UUID> ids = instrutorIds == null ? List.of() : instrutorIds.stream().distinct().toList();
        for (UUID id : ids) {
            var instrutor = instrutorRepository.findById(id)
                .filter(i -> tenantId.equals(i.getTenantId()) && Boolean.TRUE.equals(i.getAtivo()))
                .orElseThrow(() -> new BusinessException(
                    "Instrutor inválido na designação (precisa ser instrutor ATIVO da sua EAMA): " + id));
        }
        designacaoRepository.deleteByVinculoId(vinculoId);
        for (UUID id : ids) {
            designacaoRepository.save(com.jetski.locacoes.domain.VinculoEmissaoInstrutor.builder()
                .vinculoId(vinculoId)
                .instrutorId(id)
                .build());
        }
        log.info("Designação de instrutores da parceria {} atualizada pela EAMA {}: {} instrutor(es) "
            + "(vazio = todos os ativos)", vinculoId, tenantId, ids.size());
        return listarDesignados(tenantId, vinculoId);
    }

    /** Instrutores designados (id + nome) da parceria — visível aos dois lados. */
    @Transactional(readOnly = true)
    public List<Object[]> listarDesignados(UUID tenantId, UUID vinculoId) {
        VinculoEmissao v = requireParticipante(tenantId, vinculoId);
        return comTenant(v.getTenantEmissorId(), () -> {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = entityManager.createNativeQuery(
                    "SELECT i.id, i.nome FROM vinculo_emissao_instrutor d "
                    + "JOIN instrutor i ON i.id = d.instrutor_id "
                    + "WHERE d.vinculo_id = ?1 ORDER BY i.nome")
                .setParameter(1, vinculoId)
                .getResultList();
            return rows;
        });
    }

    /**
     * Resolve o contexto do emissor para uma emissão delegada da operadora:
     * exige vínculo ATIVO (BLOQUEADO = kill switch → 400 de negócio) e EAMA
     * ainda habilitada. Instrutor é validado como pertencente à EAMA quando
     * informado (a obrigatoriedade em si segue a parametrização do tenant).
     */
    @Transactional(readOnly = true)
    public DelegacaoContext resolverParaEmissao(UUID operadoraTenantId, UUID instrutorId) {
        VinculoEmissao v = vinculoVivoDaOperadora(operadoraTenantId);
        if (v == null || v.getStatus() == VinculoEmissao.Status.CONVIDADO) {
            throw new BusinessException("Seu plano emite via EAMA parceira, mas não há parceria "
                + "ativa. Convide uma EAMA em Emissão delegada.");
        }
        if (v.getStatus() == VinculoEmissao.Status.BLOQUEADO) {
            throw new BusinessException("Emissão via EAMA parceira suspensa pelo parceiro. "
                + "Fale com a emissora para liberar.");
        }
        UUID emissorId = v.getTenantEmissorId();
        return comTenant(emissorId, () -> {
            Object[] t;
            try {
                t = (Object[]) entityManager.createNativeQuery(
                        "SELECT t.razao_social, t.cnpj, t.cidade, t.uf, t.marinha_email, "
                        + "t.email_remetente, t.emissora_habilitada, c.codigo "
                        + "FROM tenant t LEFT JOIN capitania c ON c.id = t.capitania_id "
                        + "WHERE t.id = ?1")
                    .setParameter(1, emissorId)
                    .getSingleResult();
            } catch (jakarta.persistence.NoResultException e) {
                throw new BusinessException("EAMA parceira não está mais disponível");
            }
            if (!Boolean.TRUE.equals(t[6])) {
                throw new BusinessException("A EAMA parceira não está mais habilitada como emissora "
                    + "— peça a revalidação junto ao Meu Jet");
            }
            UUID insId = null;
            String insNome = null, insRg = null, insOrgao = null, insCpf = null,
                insCha = null, insAssinatura = null;
            java.time.LocalDate insData = null;
            if (instrutorId != null) {
                Object[] i;
                try {
                    i = (Object[]) entityManager.createNativeQuery(
                            "SELECT i.id, i.nome, i.rg, i.orgao_emissor, i.cpf, i.cha, i.data_emissao, "
                            + "i.assinatura_s3_key FROM instrutor i "
                            + "WHERE i.id = :instrutorId AND i.tenant_id = :emissorId AND i.ativo = true"
                            + COND_DESIGNADO)
                        .setParameter("instrutorId", instrutorId)
                        .setParameter("emissorId", emissorId)
                        .setParameter("vinculoId", v.getId())
                        .getSingleResult();
                } catch (jakarta.persistence.NoResultException e) {
                    throw new BusinessException("O instrutor informado não pertence à EAMA parceira "
                        + "ou não está designado para esta parceria (na emissão delegada, o "
                        + "instrutor é sempre da emissora)");
                }
                insId = (UUID) i[0];
                insNome = (String) i[1];
                insRg = (String) i[2];
                insOrgao = (String) i[3];
                insCpf = (String) i[4];
                insCha = (String) i[5];
                insData = i[6] != null ? ((java.sql.Date) i[6]).toLocalDate() : null;
                insAssinatura = (String) i[7];
            }
            return new DelegacaoContext(v.getId(), emissorId,
                (String) t[0], (String) t[1], (String) t[2], (String) t[3],
                (String) t[7], (String) t[4], (String) t[5],
                insId, insNome, insRg, insOrgao, insCpf, insCha, insData, insAssinatura);
        });
    }

    /**
     * Grava o espelho da emissão no tenant EMISSOR (§3.5). Flush explícito
     * ANTES da janela (as escritas pendentes da operadora precisam ir ao banco
     * sob o contexto RLS dela — gotcha flush×RLS multi-tenant); o INSERT do
     * espelho é nativo, executado dentro da janela do emissor.
     */
    @Transactional
    public void registrarEspelho(DelegacaoContext ctx, UUID documentoId, String documentoHash,
                                 String s3KeyMarinha, String operadoraNome, String condutorNome,
                                 String condutorCpf, String gruNumero, Instant emitidoEm) {
        entityManager.flush();
        comTenant(ctx.emissorTenantId(), () -> {
            entityManager.createNativeQuery(
                    "INSERT INTO emissao_delegada (tenant_id, vinculo_id, documento_id, "
                    + "documento_hash, s3_key, operadora_tenant_id, operadora_nome, "
                    + "condutor_nome, condutor_cpf, instrutor_id, instrutor_nome, gru_numero, "
                    + "emitido_em) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13) "
                    + "ON CONFLICT (documento_id) WHERE documento_id IS NOT NULL DO NOTHING")
                .setParameter(1, ctx.emissorTenantId())
                .setParameter(2, ctx.vinculoId())
                .setParameter(3, documentoId)
                .setParameter(4, documentoHash)
                .setParameter(5, s3KeyMarinha)
                .setParameter(6, TenantContext.getTenantId())
                .setParameter(7, operadoraNome)
                .setParameter(8, condutorNome)
                .setParameter(9, condutorCpf)
                .setParameter(10, ctx.instrutorId())
                .setParameter(11, ctx.instrutorNome())
                .setParameter(12, gruNumero)
                .setParameter(13, emitidoEm)
                .executeUpdate();
            return null;
        });
    }

    // ==================== helpers ====================

    private VinculoEmissao vinculoVivoDaOperadora(UUID operadoraTenantId) {
        return repository.findFirstByTenantOperadorIdAndStatusIn(operadoraTenantId, VIVOS).orElse(null);
    }

    private VinculoEmissao requireParticipante(UUID tenantId, UUID vinculoId) {
        VinculoEmissao v = repository.findById(vinculoId)
            .orElseThrow(() -> new NotFoundException("Parceria de emissão não encontrada: " + vinculoId));
        if (!tenantId.equals(v.getTenantOperadorId()) && !tenantId.equals(v.getTenantEmissorId())) {
            throw new NotFoundException("Parceria de emissão não encontrada: " + vinculoId);
        }
        return v;
    }

    private void validarPar(UUID tenantId, ParceiroInfo parceiro, UUID operadorId, UUID emissorId) {
        ParceiroInfo self = lookupParceiroPorId(tenantId);
        ParceiroInfo operador = operadorId.equals(tenantId) ? self : parceiro;
        ParceiroInfo emissor = emissorId.equals(tenantId) ? self : parceiro;
        validarMesmaCapitania(operador, emissor);
        validarEmissorHabilitado(emissor);
    }

    private void validarMesmaCapitania(ParceiroInfo operador, ParceiroInfo emissor) {
        if (operador.capitaniaId() == null || emissor.capitaniaId() == null) {
            throw new BusinessException("As duas empresas precisam declarar a capitania "
                + "(perfil de emissão) antes da parceria");
        }
        if (!operador.capitaniaId().equals(emissor.capitaniaId())) {
            throw new BusinessException("A parceria de emissão exige empresas da MESMA capitania");
        }
    }

    private void validarEmissorHabilitado(ParceiroInfo emissor) {
        if (!emissor.emissoraHabilitada()) {
            throw new BusinessException("A empresa " + emissor.razaoSocial()
                + " não está habilitada como EAMA emissora (validação do Meu Jet pendente)");
        }
    }

    private ParceiroInfo lookupParceiroPorSlug(String slug) {
        return lookupParceiro("slug = ?1 AND excluido_em IS NULL", slug);
    }

    private ParceiroInfo lookupParceiroPorId(UUID id) {
        return lookupParceiro("id = ?1 AND excluido_em IS NULL", id);
    }

    /**
     * Leitura mínima da tabela tenant fora do contexto RLS corrente, via janela
     * {@code app.unrestricted} (mesmo GUC do superadmin, V042). Colunas
     * EXPLICITAMENTE limitadas — nunca segredos (smtp_password etc.).
     */
    private ParceiroInfo lookupParceiro(String where, Object param) {
        String antes = (String) entityManager.createNativeQuery(
            "SELECT COALESCE(current_setting('app.unrestricted', true), 'false')").getSingleResult();
        entityManager.createNativeQuery("SELECT set_config('app.unrestricted', 'true', true)")
            .getSingleResult();
        try {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = entityManager.createNativeQuery(
                    "SELECT id, slug, razao_social, capitania_id, emissora_habilitada "
                    + "FROM tenant WHERE " + where)
                .setParameter(1, param)
                .getResultList();
            if (rows.isEmpty()) {
                return null;
            }
            Object[] r = rows.get(0);
            return new ParceiroInfo((UUID) r[0], (String) r[1], (String) r[2],
                (UUID) r[3], Boolean.TRUE.equals(r[4]));
        } finally {
            entityManager.createNativeQuery("SELECT set_config('app.unrestricted', ?1, true)")
                .setParameter(1, antes)
                .getSingleResult();
        }
    }

    /**
     * Executa {@code fn} com a RLS apontando para {@code alvo} e SEMPRE
     * restaura o contexto original (transaction-scoped via set_config local).
     */
    private <T> T comTenant(UUID alvo, Supplier<T> fn) {
        UUID original = TenantContext.getTenantId();
        setTenantLocal(alvo);
        try {
            return fn.get();
        } finally {
            setTenantLocal(original);
        }
    }

    private void setTenantLocal(UUID tenantId) {
        entityManager.createNativeQuery("SELECT set_config('app.tenant_id', ?1, true)")
            .setParameter(1, tenantId != null ? tenantId.toString() : "")
            .getSingleResult();
    }

    private UUID actorOrNull() {
        try {
            return TenantContext.getUsuarioId();
        } catch (Exception e) {
            return null;
        }
    }
}
