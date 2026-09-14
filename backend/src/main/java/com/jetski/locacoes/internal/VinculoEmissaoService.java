package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.Instrutor;
import com.jetski.locacoes.domain.VinculoEmissao;
import com.jetski.locacoes.domain.VinculoInstrutorOperadora;
import com.jetski.locacoes.internal.repository.VinculoEmissaoRepository;
import com.jetski.locacoes.internal.repository.VinculoInstrutorOperadoraRepository;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.ConflictException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.security.TenantContext;
import com.jetski.tenant.ModuloPlano;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Parceria de emissão delegada (EMISSAO_DELEGADA_SPEC §4): convite/aceite
 * bilateral com termo, kill switch da EAMA, estorno anti-fraude do bônus da
 * operadora e resolução do contexto do emissor na emissão.
 *
 * <p><b>Papel exclusivo (§8.M)</b>: uma empresa é EAMA emissora OU delegada.
 * A parceria em vigor como operadora define o modo de emissão (não o plano);
 * aceitar ser operadora derruba a habilitação de emissora, e ninguém é
 * operadora numa parceria e emissora em outra.
 *
 * <p><b>Instrutores da operadora (§8.N, V070)</b>: pela NORMAM-212 o instrutor
 * é cadastrado na EAMA, que responde por ele. A operadora pode cadastrar
 * instrutores próprios, mas eles só assinam emissões delegadas depois de
 * aprovados pela EAMA da parceria, que também pode removê-los.
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

    /** Parceria aceita e não revogada: é ela que torna a empresa delegada (§8.M). */
    private static final EnumSet<VinculoEmissao.Status> EM_VIGOR =
        EnumSet.of(VinculoEmissao.Status.ATIVO, VinculoEmissao.Status.BLOQUEADO);

    /** Papel do CONVIDANTE no vínculo proposto. */
    public enum PapelConvite { OPERADORA, EMISSORA }

    /** Como a empresa emite à Marinha hoje. */
    public enum ModoEmissao { PROPRIA, DELEGADA, SEM_EMISSAO }

    /** Decisão da EAMA sobre um instrutor submetido pela operadora (V070). */
    public enum DecisaoInstrutor { APROVAR, REJEITAR, REMOVER }

    private final VinculoEmissaoRepository repository;
    private final com.jetski.locacoes.internal.repository.VinculoEmissaoInstrutorRepository designacaoRepository;
    private final VinculoInstrutorOperadoraRepository aprovacaoRepository;
    private final com.jetski.locacoes.internal.repository.InstrutorRepository instrutorRepository;
    private final com.jetski.creditos.CreditoService creditoService;
    private final com.jetski.tenant.PlanoLimiteService planoLimiteService;
    private final com.jetski.shared.email.EmailService emailService;
    private final com.jetski.shared.storage.StorageService storageService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    /** Dados mínimos do parceiro lidos na janela unrestricted (sem segredos). */
    public record ParceiroInfo(UUID id, String slug, String razaoSocial,
                               UUID capitaniaId, boolean emissoraHabilitada,
                               String contatoEmail) {}

    /** Contexto do emissor resolvido para uma emissão delegada (snapshot vivo). */
    public record DelegacaoContext(
        UUID vinculoId, UUID emissorTenantId,
        String razaoSocial, String cnpj, String cidade, String uf,
        String capitaniaCodigo, String marinhaEmail, String contatoEmail,
        UUID instrutorId, String instrutorNome, String instrutorRg,
        String instrutorOrgaoEmissor, String instrutorCpf, String instrutorCha,
        java.time.LocalDate instrutorDataEmissao, String instrutorAssinaturaS3Key,
        // Ofício à Capitania (V064): a EAMA emissora assina o e-mail (NORMAM-212 5.4.2)
        String eamaRegistro, String responsavelNome, String telefone, String emailOficial) {}

    /**
     * Modo de emissão da empresa (§8.M). {@code vinculoId}/{@code vinculoStatus}
     * descrevem a parceria viva como operadora, quando houver; os flags de plano
     * são o portão comercial, informativo para a UI.
     */
    public record ModoEmissaoInfo(ModoEmissao modo, UUID vinculoId, String vinculoStatus,
                                  String emissoraNome, boolean planoPermitePropria,
                                  boolean planoPermiteDelegada) {}

    /** Instrutor da operadora submetido à EAMA, com os dados que a EAMA avalia (V070). */
    public record InstrutorOperadoraInfo(UUID instrutorId, String nome, String rg, String orgaoEmissor,
                                         String cpf, String cha, java.time.LocalDate dataEmissao,
                                         boolean temAssinatura, String assinaturaUrl, boolean ativo,
                                         String status, Instant solicitadoEm, Instant decididoEm,
                                         String motivo) {}

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

        ParceiroInfo self = lookupParceiroPorId(tenantId);
        ParceiroInfo operador = operadorId.equals(tenantId) ? self : parceiro;
        ParceiroInfo emissor = emissorId.equals(tenantId) ? self : parceiro;
        validarMesmaCapitania(operador, emissor);
        validarEmissorHabilitado(emissor);
        validarPapelExclusivo(operador, emissor);
        validarPlanoDaOperadora(operador);

        // Janela da operadora: a sessão pode ser a da EAMA, que não enxerga as
        // parcerias da operadora com terceiros.
        if (comTenant(operadorId, () -> repository.existsByTenantOperadorIdAndStatusIn(operadorId, VIVOS))) {
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
            notificarTransicao(v, "CONVIDADO", tenantId,
                "Convite de parceria de emissão delegada",
                "Sua empresa foi convidada para uma parceria de emissão delegada no Meu Jet. "
                + "Acesse Emissão delegada no backoffice para ler o termo e aceitar (ou recusar).");
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
        validarPapelExclusivo(operador, emissor);
        validarPlanoDaOperadora(operador);

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
            // Papel exclusivo (§8.M): aceitou ser delegada, deixa de ser emissora.
            // Voltar a emitir por conta própria exige nova validação do superadmin,
            // e só depois de revogar a parceria.
            int derrubada = entityManager.createNativeQuery(
                    "UPDATE tenant SET emissora_habilitada = false "
                    + "WHERE id = ?1 AND emissora_habilitada = true")
                .setParameter(1, v.getTenantOperadorId())
                .executeUpdate();
            if (derrubada > 0) {
                log.info("Habilitação de emissora da operadora {} removida na ativação do vínculo {} "
                    + "(papel exclusivo: emissora OU delegada)", v.getTenantOperadorId(), v.getId());
            }
            return null;
        });

        log.info("Vínculo de emissão ATIVO: {} (aceito pelo tenant {})", v.getId(), tenantId);
        notificarTransicao(v, "ATIVADO", tenantId,
            "Parceria de emissão delegada ativada",
            "O convite de parceria de emissão delegada foi aceito (termo de responsabilidade "
            + "assinado). A parceria está ATIVA.");
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
        notificarTransicao(v, "BLOQUEADO", tenantId,
            "Emissão delegada BLOQUEADA pela EAMA",
            "A EAMA parceira bloqueou novas emissões em nome dela (efeito imediato). "
            + "Fale com a emissora para regularizar e liberar.");
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
        notificarTransicao(v, "LIBERADO", tenantId,
            "Emissão delegada liberada",
            "A EAMA parceira liberou a parceria — novas emissões em nome dela voltaram a funcionar.");
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
        notificarTransicao(v, "REVOGADO", tenantId,
            "Parceria de emissão delegada revogada",
            "A parceria de emissão delegada foi revogada (ação definitiva).");
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

    // ==================== modo de emissão (§8.M) ====================

    /**
     * True quando a empresa é operadora de uma parceria aceita e não revogada
     * (ATIVA ou BLOQUEADA). É isso — e não o plano — que torna a emissão delegada.
     */
    @Transactional(readOnly = true)
    public boolean emissaoDelegadaEmVigor(UUID operadoraTenantId) {
        return comTenant(operadoraTenantId,
            () -> repository.existsByTenantOperadorIdAndStatusIn(operadoraTenantId, EM_VIGOR));
    }

    /**
     * Modo de emissão da empresa: parceria em vigor como operadora → DELEGADA;
     * senão o plano decide (emissão própria → PROPRIA; só a delegada → DELEGADA,
     * ainda sem parceria); sem nenhum dos dois módulos → SEM_EMISSAO.
     */
    @Transactional(readOnly = true)
    public ModoEmissaoInfo modoEmissao(UUID tenantId) {
        boolean propria = planoLimiteService.moduloHabilitado(tenantId, ModuloPlano.EMISSAO_PROPRIA);
        boolean delegada = planoLimiteService.moduloHabilitado(tenantId, ModuloPlano.EMISSAO_DELEGADA);
        VinculoEmissao v = comTenant(tenantId, () -> vinculoVivoDaOperadora(tenantId));
        UUID vinculoId = v != null ? v.getId() : null;
        String vinculoStatus = v != null ? v.getStatus().name() : null;
        String emissoraNome = v != null ? nomeDoTenant(v.getTenantEmissorId()) : null;

        ModoEmissao modo;
        if (v != null && EM_VIGOR.contains(v.getStatus())) {
            modo = ModoEmissao.DELEGADA;
        } else if (propria) {
            modo = ModoEmissao.PROPRIA;
        } else if (delegada) {
            modo = ModoEmissao.DELEGADA;
        } else {
            modo = ModoEmissao.SEM_EMISSAO;
        }
        return new ModoEmissaoInfo(modo, vinculoId, vinculoStatus, emissoraNome, propria, delegada);
    }

    // ==================== emissão delegada ====================

    /**
     * Designação obrigatória (V049, revista em 13/set/2026 — §8.L): a operadora só usa
     * instrutor da EAMA que foi designado para a parceria. Sem designação, nenhum
     * instrutor da EAMA; a operadora fica só com os próprios aprovados.
     */
    private static final String COND_DESIGNADO =
        " AND EXISTS (SELECT 1 FROM vinculo_emissao_instrutor d "
        + "WHERE d.vinculo_id = :vinculoId AND d.instrutor_id = i.id)";

    /**
     * Instrutores (id + nome + origem) disponíveis para a emissão delegada da
     * operadora: os da EAMA DESIGNADOS para a parceria e os da própria
     * operadora APROVADOS pela EAMA (V070). Exposição mínima — CPF/RG/CHA
     * entram no PDF pelo serviço, nunca pela UI da operadora (LGPD, §5.4).
     *
     * @return linhas {id, nome, origem}, origem = EAMA | OPERADORA
     */
    @Transactional(readOnly = true)
    public List<Object[]> instrutoresDoParceiro(UUID operadoraTenantId) {
        VinculoEmissao v = vinculoVivoDaOperadora(operadoraTenantId);
        if (v == null || v.getStatus() != VinculoEmissao.Status.ATIVO) {
            throw new BusinessException("Não há parceria de emissão ativa com uma EAMA");
        }
        List<Object[]> daEama = comTenant(v.getTenantEmissorId(), () -> {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = entityManager.createNativeQuery(
                    "SELECT i.id, i.nome, CAST('EAMA' AS varchar) FROM instrutor i "
                    + "WHERE i.tenant_id = :emissorId AND i.ativo = true"
                    + COND_DESIGNADO + " ORDER BY i.nome")
                .setParameter("emissorId", v.getTenantEmissorId())
                .setParameter("vinculoId", v.getId())
                .getResultList();
            return rows;
        });
        List<Object[]> aprovados = comTenant(operadoraTenantId, () -> {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = entityManager.createNativeQuery(
                    "SELECT i.id, i.nome, CAST('OPERADORA' AS varchar) FROM instrutor i "
                    + "JOIN vinculo_instrutor_operadora a ON a.instrutor_id = i.id "
                    + "WHERE a.vinculo_id = :vinculoId AND a.status = 'APROVADO' "
                    + "AND i.tenant_id = :operadoraId AND i.ativo = true ORDER BY i.nome")
                .setParameter("vinculoId", v.getId())
                .setParameter("operadoraId", operadoraTenantId)
                .getResultList();
            return rows;
        });
        List<Object[]> todos = new ArrayList<>(daEama);
        todos.addAll(aprovados);
        return todos;
    }

    /**
     * Define (substituindo o conjunto) quais instrutores da EAMA atendem esta
     * parceria. Só o EMISSOR designa; lista vazia = nenhum instrutor da EAMA (a
     * operadora fica só com os próprios aprovados). Todos os ids precisam ser
     * instrutores ATIVOS da própria EAMA.
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
            instrutorRepository.findById(id)
                .filter(i -> tenantId.equals(i.getTenantId()) && Boolean.TRUE.equals(i.getAtivo()))
                .orElseThrow(() -> new BusinessException(
                    "Instrutor inválido na designação (precisa ser instrutor ATIVO da sua EAMA): " + id));
        }
        // Diferença, não "apaga tudo e regrava": o Hibernate executa os INSERTs antes dos
        // DELETEs no flush, então regravar um par já designado violava
        // ux_vinculo_emissao_instrutor (salvar a mesma designação de novo dava 500).
        var atuais = designacaoRepository.findByVinculoId(vinculoId);
        var removidos = atuais.stream().filter(d -> !ids.contains(d.getInstrutorId())).toList();
        designacaoRepository.deleteAll(removidos);
        var jaDesignados = atuais.stream()
            .map(com.jetski.locacoes.domain.VinculoEmissaoInstrutor::getInstrutorId)
            .collect(java.util.stream.Collectors.toSet());
        for (UUID id : ids) {
            if (!jaDesignados.contains(id)) {
                designacaoRepository.save(com.jetski.locacoes.domain.VinculoEmissaoInstrutor.builder()
                    .vinculoId(vinculoId)
                    .instrutorId(id)
                    .build());
            }
        }
        designacaoRepository.flush();
        log.info("Designação de instrutores da parceria {} atualizada pela EAMA {}: {} instrutor(es) "
            + "(vazio = nenhum instrutor da EAMA)", vinculoId, tenantId, ids.size());
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
     * ainda habilitada. O instrutor informado precisa ser da EAMA e designado para
     * a parceria (V049) ou da própria operadora com aprovação da EAMA (V070).
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
        Object[] aprovadoDaOperadora = instrutorId == null ? null
            : comTenant(operadoraTenantId, () -> instrutorAprovadoDaOperadora(v.getId(), operadoraTenantId, instrutorId));
        return comTenant(emissorId, () -> {
            Object[] t;
            try {
                t = (Object[]) entityManager.createNativeQuery(
                        "SELECT t.razao_social, t.cnpj, t.cidade, t.uf, t.marinha_email, "
                        + "t.email_remetente, t.emissora_habilitada, c.codigo, "
                        + "t.eama_registro, t.responsavel_nome, t.telefone, t.email_oficial "
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
                Object[] i = aprovadoDaOperadora;
                if (i == null) {
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
                            + "nem foi aprovado por ela, ou não está designado para esta parceria (na "
                            + "emissão delegada o instrutor é da emissora ou um instrutor seu aprovado "
                            + "pela emissora)");
                    }
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
                insId, insNome, insRg, insOrgao, insCpf, insCha, insData, insAssinatura,
                (String) t[8], (String) t[9], (String) t[10], (String) t[11]);
        });
    }

    /** Instrutor ativo da operadora com aprovação da EAMA nesta parceria, ou null. */
    private Object[] instrutorAprovadoDaOperadora(UUID vinculoId, UUID operadoraTenantId, UUID instrutorId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(
                "SELECT i.id, i.nome, i.rg, i.orgao_emissor, i.cpf, i.cha, i.data_emissao, "
                + "i.assinatura_s3_key FROM instrutor i "
                + "JOIN vinculo_instrutor_operadora a ON a.instrutor_id = i.id "
                + "WHERE i.id = :instrutorId AND i.tenant_id = :operadoraId AND i.ativo = true "
                + "AND a.vinculo_id = :vinculoId AND a.status = 'APROVADO'")
            .setParameter("instrutorId", instrutorId)
            .setParameter("operadoraId", operadoraTenantId)
            .setParameter("vinculoId", vinculoId)
            .getResultList();
        return rows.isEmpty() ? null : rows.get(0);
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

    // ==================== instrutores da operadora (§8.N, V070) ====================

    /**
     * A operadora submete um instrutor PRÓPRIO à EAMA da parceria em vigor.
     * Idempotente para pedidos pendentes; rejeitado ou removido volta a PENDENTE.
     */
    @Transactional
    public VinculoInstrutorOperadora solicitarAprovacaoInstrutor(UUID operadoraTenantId, UUID instrutorId) {
        VinculoEmissao v = repository.findFirstByTenantOperadorIdAndStatusIn(operadoraTenantId, EM_VIGOR)
            .orElseThrow(() -> new BusinessException(
                "Sua empresa não é operadora de uma parceria de emissão em vigor — a aprovação de "
                + "instrutores é feita pela EAMA emissora da parceria"));
        Instrutor instrutor = instrutorRepository.findById(instrutorId)
            .filter(i -> operadoraTenantId.equals(i.getTenantId()))
            .orElseThrow(() -> new NotFoundException("Instrutor não encontrado: " + instrutorId));
        if (!Boolean.TRUE.equals(instrutor.getAtivo())) {
            throw new BusinessException("Reative o instrutor antes de pedir a aprovação da EAMA");
        }
        VinculoInstrutorOperadora a = aprovacaoRepository
            .findByVinculoIdAndInstrutorId(v.getId(), instrutorId).orElse(null);
        if (a != null && a.getStatus() == VinculoInstrutorOperadora.Status.PENDENTE) {
            return a;
        }
        if (a != null && a.getStatus() == VinculoInstrutorOperadora.Status.APROVADO) {
            throw new ConflictException("Instrutor já aprovado pela EAMA parceira");
        }
        if (a == null) {
            a = VinculoInstrutorOperadora.builder().vinculoId(v.getId()).instrutorId(instrutorId).build();
        }
        a = aprovacaoRepository.save(pendente(a));
        log.info("Instrutor {} da operadora {} submetido à EAMA {} (vínculo {})",
            instrutorId, operadoraTenantId, v.getTenantEmissorId(), v.getId());
        notificar(v, "INSTRUTOR_SOLICITADO", operadoraTenantId, instrutorId,
            "Instrutor aguardando sua aprovação",
            "cadastrou o instrutor <b>" + HtmlUtils.htmlEscape(instrutor.getNome()) + "</b> e pede a sua "
            + "aprovação para usá-lo nas emissões em seu nome. Acesse Emissão delegada no backoffice "
            + "para aprovar ou rejeitar.");
        return a;
    }

    /**
     * Dados de um instrutor da operadora mudaram: a aprovação anterior (ou a
     * recusa) deixa de valer e o pedido volta a PENDENTE para a EAMA revisar.
     * Sem parceria em vigor ou sem pedido, não faz nada.
     */
    @Transactional
    public void reenviarParaAprovacaoSeAlterado(UUID operadoraTenantId, UUID instrutorId, String nome) {
        VinculoEmissao v = repository.findFirstByTenantOperadorIdAndStatusIn(operadoraTenantId, EM_VIGOR)
            .orElse(null);
        if (v == null) {
            return;
        }
        VinculoInstrutorOperadora a = aprovacaoRepository
            .findByVinculoIdAndInstrutorId(v.getId(), instrutorId).orElse(null);
        if (a == null || a.getStatus() == VinculoInstrutorOperadora.Status.PENDENTE) {
            return;
        }
        VinculoInstrutorOperadora.Status anterior = a.getStatus();
        aprovacaoRepository.save(pendente(a));
        log.info("Instrutor {} da operadora {} alterado: pedido à EAMA voltou a PENDENTE (era {})",
            instrutorId, operadoraTenantId, anterior);
        notificar(v, "INSTRUTOR_SOLICITADO", operadoraTenantId, instrutorId,
            "Instrutor alterado aguardando sua aprovação",
            "alterou os dados do instrutor <b>" + HtmlUtils.htmlEscape(nome) + "</b>. Ele não assina "
            + "emissões em seu nome até você revisar e aprovar de novo em Emissão delegada.");
    }

    /** A EAMA aprova, rejeita ou remove um instrutor submetido pela operadora. */
    @Transactional
    public VinculoInstrutorOperadora decidirInstrutor(UUID tenantId, UUID vinculoId, UUID instrutorId,
                                                      DecisaoInstrutor decisao, String motivo) {
        VinculoEmissao v = requireParticipante(tenantId, vinculoId);
        if (!tenantId.equals(v.getTenantEmissorId())) {
            throw new BusinessException("Somente a EAMA emissora aprova, rejeita ou remove os instrutores "
                + "da operadora");
        }
        if (v.getStatus() == VinculoEmissao.Status.REVOGADO) {
            throw new ConflictException("Parceria revogada");
        }
        VinculoInstrutorOperadora a = aprovacaoRepository.findByVinculoIdAndInstrutorId(vinculoId, instrutorId)
            .orElseThrow(() -> new NotFoundException(
                "A operadora não submeteu este instrutor à parceria: " + instrutorId));
        VinculoInstrutorOperadora.Status status = a.getStatus();
        String transicao;
        String corpo;
        switch (decisao) {
            case APROVAR -> {
                exigirStatus(status, VinculoInstrutorOperadora.Status.PENDENTE, "aprovados");
                Boolean ativo = comTenant(v.getTenantOperadorId(), () -> {
                    List<?> rows = entityManager.createNativeQuery(
                            "SELECT ativo FROM instrutor WHERE id = ?1 AND tenant_id = ?2")
                        .setParameter(1, instrutorId)
                        .setParameter(2, v.getTenantOperadorId())
                        .getResultList();
                    return rows.isEmpty() ? null : (Boolean) rows.get(0);
                });
                if (!Boolean.TRUE.equals(ativo)) {
                    throw new BusinessException("O instrutor está inativo na operadora e não pode ser aprovado");
                }
                a.setStatus(VinculoInstrutorOperadora.Status.APROVADO);
                transicao = "INSTRUTOR_APROVADO";
                corpo = "aprovou o seu instrutor para as emissões em nome dela.";
            }
            case REJEITAR -> {
                exigirStatus(status, VinculoInstrutorOperadora.Status.PENDENTE, "rejeitados");
                a.setStatus(VinculoInstrutorOperadora.Status.REJEITADO);
                transicao = "INSTRUTOR_REJEITADO";
                corpo = "rejeitou o seu instrutor para as emissões em nome dela.";
            }
            case REMOVER -> {
                exigirStatus(status, VinculoInstrutorOperadora.Status.APROVADO, "removidos");
                a.setStatus(VinculoInstrutorOperadora.Status.REMOVIDO);
                transicao = "INSTRUTOR_REMOVIDO";
                corpo = "removeu o seu instrutor: ele não assina mais emissões em nome dela.";
            }
            default -> throw new BusinessException("Decisão inválida: " + decisao);
        }
        a.setDecididoEm(Instant.now());
        a.setDecididoPor(actorOrNull());
        a.setMotivo(motivoLimpo(motivo));
        a = aprovacaoRepository.save(a);
        log.info("EAMA {} decidiu {} sobre o instrutor {} da operadora {} (vínculo {})",
            tenantId, decisao, instrutorId, v.getTenantOperadorId(), vinculoId);
        notificar(v, transicao, tenantId, instrutorId,
            "Decisão sobre instrutor da parceria",
            corpo + (a.getMotivo() != null ? " Motivo: " + HtmlUtils.htmlEscape(a.getMotivo()) : ""));
        return a;
    }

    /**
     * Instrutores que a operadora submeteu à parceria, com os dados que a EAMA
     * avalia (identidade, CHA e assinatura). Visível aos dois lados.
     */
    @Transactional(readOnly = true)
    public List<InstrutorOperadoraInfo> listarInstrutoresOperadora(UUID tenantId, UUID vinculoId) {
        VinculoEmissao v = requireParticipante(tenantId, vinculoId);
        List<VinculoInstrutorOperadora> pedidos = aprovacaoRepository.findByVinculoIdOrderBySolicitadoEmDesc(vinculoId);
        if (pedidos.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = pedidos.stream().map(VinculoInstrutorOperadora::getInstrutorId).toList();
        Map<UUID, Object[]> dados = comTenant(v.getTenantOperadorId(), () -> {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = entityManager.createNativeQuery(
                    "SELECT id, nome, rg, orgao_emissor, cpf, cha, data_emissao, assinatura_s3_key, ativo "
                    + "FROM instrutor WHERE tenant_id = :operadoraId AND id IN (:ids)")
                .setParameter("operadoraId", v.getTenantOperadorId())
                .setParameter("ids", ids)
                .getResultList();
            Map<UUID, Object[]> porId = new HashMap<>();
            for (Object[] r : rows) {
                porId.put((UUID) r[0], r);
            }
            return porId;
        });
        List<InstrutorOperadoraInfo> resultado = new ArrayList<>();
        for (VinculoInstrutorOperadora a : pedidos) {
            Object[] r = dados.get(a.getInstrutorId());
            if (r == null) {
                continue;
            }
            String assinaturaKey = (String) r[7];
            resultado.add(new InstrutorOperadoraInfo(
                a.getInstrutorId(), (String) r[1], (String) r[2], (String) r[3], (String) r[4],
                (String) r[5], r[6] != null ? ((java.sql.Date) r[6]).toLocalDate() : null,
                assinaturaKey != null, urlAssinatura(assinaturaKey), Boolean.TRUE.equals(r[8]),
                a.getStatus().name(), a.getSolicitadoEm(), a.getDecididoEm(), a.getMotivo()));
        }
        return resultado;
    }

    private VinculoInstrutorOperadora pendente(VinculoInstrutorOperadora a) {
        a.setStatus(VinculoInstrutorOperadora.Status.PENDENTE);
        a.setSolicitadoEm(Instant.now());
        a.setSolicitadoPor(actorOrNull());
        a.setDecididoEm(null);
        a.setDecididoPor(null);
        a.setMotivo(null);
        return a;
    }

    private static void exigirStatus(VinculoInstrutorOperadora.Status atual,
                                     VinculoInstrutorOperadora.Status esperado, String verbo) {
        if (atual != esperado) {
            throw new ConflictException("Só instrutores com pedido " + esperado + " podem ser " + verbo
                + " (status atual: " + atual + ")");
        }
    }

    private static String motivoLimpo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            return null;
        }
        String m = motivo.trim();
        return m.length() > 500 ? m.substring(0, 500) : m;
    }

    private String urlAssinatura(String key) {
        if (key == null) {
            return null;
        }
        try {
            return storageService.generatePresignedDownloadUrl(key, 15).getUrl();
        } catch (Exception e) {
            log.warn("URL da assinatura do instrutor indisponível ({}): {}", key, e.getMessage());
            return null;
        }
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

    /**
     * Papel exclusivo (§8.M): a operadora não pode ser emissora de outra parceria
     * viva, e a emissora não pode ser operadora de outra. Cada checagem roda na
     * janela da empresa checada — a sessão não enxerga parcerias com terceiros.
     */
    private void validarPapelExclusivo(ParceiroInfo operador, ParceiroInfo emissor) {
        if (comTenant(operador.id(), () -> repository.existsByTenantEmissorIdAndStatusIn(operador.id(), VIVOS))) {
            throw new BusinessException("A empresa " + operador.razaoSocial() + " é EAMA emissora de outra "
                + "parceria em andamento. Uma empresa é emissora OU delegada: revogue as parcerias em "
                + "que ela é emissora antes de torná-la operadora.");
        }
        if (comTenant(emissor.id(), () -> repository.existsByTenantOperadorIdAndStatusIn(emissor.id(), VIVOS))) {
            throw new BusinessException("A empresa " + emissor.razaoSocial() + " é operadora (delegada) de "
                + "outra parceria em andamento. Uma empresa é emissora OU delegada: ela não pode emitir "
                + "para outra operadora.");
        }
    }

    /** Portão comercial: ser operadora exige o módulo de emissão delegada no plano. */
    private void validarPlanoDaOperadora(ParceiroInfo operador) {
        if (!planoLimiteService.moduloHabilitado(operador.id(), ModuloPlano.EMISSAO_DELEGADA)) {
            throw new BusinessException("O plano da empresa " + operador.razaoSocial() + " não inclui o "
                + "módulo \"" + ModuloPlano.EMISSAO_DELEGADA.rotulo() + "\". Faça upgrade antes da parceria.");
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
                    "SELECT id, slug, razao_social, capitania_id, emissora_habilitada, email_remetente "
                    + "FROM tenant WHERE " + where)
                .setParameter(1, param)
                .getResultList();
            if (rows.isEmpty()) {
                return null;
            }
            Object[] r = rows.get(0);
            return new ParceiroInfo((UUID) r[0], (String) r[1], (String) r[2],
                (UUID) r[3], Boolean.TRUE.equals(r[4]), (String) r[5]);
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

    /**
     * Trilha + aviso da transição: publica o evento de auditoria (gravado nos
     * DOIS tenants pelo AuditEventListener) e envia e-mail best-effort ao
     * OUTRO lado da parceria (email_remetente — identidade da empresa).
     */
    private void notificarTransicao(VinculoEmissao v, String transicao, UUID actorTenantId,
                                    String assunto, String corpo) {
        eventPublisher.publishEvent(com.jetski.locacoes.event.VinculoEmissaoTransicaoEvent.of(
            v.getId(), v.getTenantOperadorId(), v.getTenantEmissorId(), transicao, actorOrNull()));
        enviarAoOutroLado(v, transicao, actorTenantId, assunto, "<p><b>%s</b>: " + corpo + "</p>");
    }

    /** Como {@link #notificarTransicao}, com o instrutor na trilha (V070). */
    private void notificar(VinculoEmissao v, String transicao, UUID actorTenantId, UUID instrutorId,
                           String assunto, String corpo) {
        eventPublisher.publishEvent(com.jetski.locacoes.event.VinculoEmissaoTransicaoEvent.of(
            v.getId(), v.getTenantOperadorId(), v.getTenantEmissorId(), transicao, actorOrNull(),
            instrutorId));
        enviarAoOutroLado(v, transicao, actorTenantId, assunto, "<p><b>%s</b> " + corpo + "</p>");
    }

    private void enviarAoOutroLado(VinculoEmissao v, String transicao, UUID actorTenantId,
                                   String assunto, String modeloHtml) {
        try {
            UUID outroLado = actorTenantId.equals(v.getTenantOperadorId())
                ? v.getTenantEmissorId() : v.getTenantOperadorId();
            ParceiroInfo destino = lookupParceiroPorId(outroLado);
            if (destino != null && destino.contatoEmail() != null && !destino.contatoEmail().isBlank()) {
                ParceiroInfo remetente = lookupParceiroPorId(actorTenantId);
                String quem = remetente != null ? remetente.razaoSocial() : "A empresa parceira";
                emailService.sendEmail(destino.contatoEmail(),
                    assunto + " — " + quem,
                    modeloHtml.replace("%s", HtmlUtils.htmlEscape(quem)));
            }
        } catch (Exception e) {
            log.warn("E-mail da transição {} do vínculo {} não enviado (segue sem): {}",
                transicao, v.getId(), e.getMessage());
        }
    }

    private UUID actorOrNull() {
        try {
            return TenantContext.getUsuarioId();
        } catch (Exception e) {
            return null;
        }
    }
}
