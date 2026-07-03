package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.domain.ClienteIdentityProvider;
import com.jetski.locacoes.api.ModeloService;
import com.jetski.locacoes.domain.Modelo;
import com.jetski.locacoes.domain.Reserva;
import com.jetski.locacoes.domain.ReservaComprovante;
import com.jetski.locacoes.internal.repository.ClienteIdentityProviderRepository;
import com.jetski.locacoes.internal.repository.ClienteRepository;
import com.jetski.locacoes.internal.repository.ReservaAceiteRepository;
import com.jetski.locacoes.internal.repository.ReservaComprovanteRepository;
import com.jetski.locacoes.internal.repository.ReservaHabilitacaoRepository;
import com.jetski.locacoes.internal.repository.ReservaRepository;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.pix.BrCodePix;
import com.jetski.shared.security.TenantContext;
import com.jetski.shared.storage.StorageService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Reservas do CLIENTE FINAL (portal, P1).
 *
 * O token do cliente não carrega tenant: cada operação resolve a loja (slug ou
 * vínculo) e fixa app.tenant_id transaction-local (mesmo padrão do ClaimService)
 * — a RLS estrita continua valendo. O Cliente tenant-scoped nasce aqui, na
 * primeira reserva com a loja, sempre com vínculo explícito em
 * cliente_identity_provider (nunca merge cego por e-mail/CPF).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerReservaService {

    private static final String PROVIDER = "keycloak";
    private static final Set<String> COMPROVANTE_TIPOS =
        Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");
    private static final long COMPROVANTE_MAX_BYTES = 5L * 1024 * 1024;

    private final EntityManager entityManager;
    private final ReservaService reservaService;
    private final ModeloService modeloService;
    private final ClienteRepository clienteRepository;
    private final ClienteIdentityProviderRepository identityRepository;
    private final ReservaRepository reservaRepository;
    private final ReservaHabilitacaoRepository habilitacaoRepository;
    private final ReservaAceiteRepository aceiteRepository;
    private final ReservaComprovanteRepository comprovanteRepository;
    private final StorageService storageService;
    private final CustomerAccountService customerAccountService;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    private final AceiteService aceiteService;
    private final AceiteOtpService aceiteOtpService;
    private final HabilitacaoService habilitacaoService;
    private final ClienteAnexoService clienteAnexoService;

    /** Percentual do sinal sobre o valor estimado da reserva (spec §5.2: ex. 30%). */
    @Value("${jetski.portal.sinal-percentual:30}")
    private int sinalPercentual;

    // ============================ Criação ============================

    public record CriarCmd(
        String lojaSlug, UUID modeloId,
        LocalDateTime dataInicio, LocalDateTime dataFimPrevista,
        String observacoes, Reserva.PagamentoTipo pagamentoTipo,
        String cpf, String telefone) {}

    @Transactional
    public ReservaCliente criar(String sub, String email, String nome, CriarCmd cmd) {
        Loja loja = lojaBySlug(cmd.lojaSlug())
            .orElseThrow(() -> new NotFoundException("Loja não encontrada: " + cmd.lojaSlug()));
        fixarTenant(loja.tenantId());

        Cliente cliente = resolverOuCriarCliente(loja.tenantId(), sub, email, nome, cmd.cpf(), cmd.telefone());

        Reserva reserva = Reserva.builder()
            .tenantId(loja.tenantId())
            .modeloId(cmd.modeloId())
            .clienteId(cliente.getId())
            .dataInicio(cmd.dataInicio())
            .dataFimPrevista(cmd.dataFimPrevista())
            .observacoes(cmd.observacoes())
            .status(Reserva.ReservaStatus.PENDENTE)
            .canal(Reserva.Canal.PORTAL)
            .sinalPago(false)
            .ativo(true)
            .build();
        if (cmd.pagamentoTipo() != null) {
            reserva.setPagamentoTipo(cmd.pagamentoTipo());
        }

        Reserva criada = reservaService.createReserva(reserva);
        log.info("Reserva de portal criada: reserva={}, tenant={}, cliente={}",
            criada.getId(), loja.tenantId(), cliente.getId());
        return toDto(criada, loja);
    }

    /**
     * Resolve o Cliente do sub nesta loja; cria (origem=PORTAL, conta ATIVA) se
     * for a primeira interação. Dedupe por CPF nunca vincula silenciosamente:
     * CPF já cadastrado na loja ⇒ orienta o fluxo de claim (anti-takeover).
     */
    private Cliente resolverOuCriarCliente(UUID tenantId, String sub, String email,
                                           String nome, String cpf, String telefone) {
        Optional<ClienteIdentityProvider> vinculo =
            identityRepository.findByProviderAndProviderUserId(PROVIDER, sub);
        if (vinculo.isPresent()) {
            return clienteRepository.findById(vinculo.get().getClienteId())
                .orElseThrow(() -> new NotFoundException("Cliente do vínculo não encontrado"));
        }

        if (cpf != null && !cpf.isBlank()) {
            Optional<Cliente> mesmoCpf = clienteRepository.findByDocumento(cpf.trim());
            if (mesmoCpf.isPresent()) {
                throw new BusinessException(
                    "Este CPF já tem cadastro nesta loja. Peça à loja o link de ativação " +
                    "da sua conta (verificação de identidade) antes de reservar pelo portal.");
            }
        }

        Cliente cliente = clienteRepository.save(Cliente.builder()
            .tenantId(tenantId)
            .nome(nome)
            .email(email)
            .documento(cpf != null && !cpf.isBlank() ? cpf.trim() : null)
            .telefone(telefone)
            .origem(Cliente.Origem.PORTAL)
            .statusConta(Cliente.StatusConta.ATIVA)
            .ativo(true)
            .build());

        identityRepository.save(ClienteIdentityProvider.builder()
            .tenantId(tenantId)
            .clienteId(cliente.getId())
            .provider(PROVIDER)
            .providerUserId(sub)
            .build());

        log.info("Cliente criado no primeiro contato via portal: cliente={}, tenant={}",
            cliente.getId(), tenantId);
        return cliente;
    }

    // ============================ Consultas ============================

    @Transactional(readOnly = true)
    public List<ReservaCliente> minhasReservas(String sub) {
        List<ReservaCliente> resultado = new ArrayList<>();
        for (CustomerAccountService.VinculoLoja v : customerAccountService.vinculos(sub)) {
            fixarTenant(v.getTenantId());
            Loja loja = lojaByTenantId(v.getTenantId());
            for (Reserva r : reservaRepository.findByClienteId(v.getClienteId())) {
                resultado.add(toDto(r, loja));
            }
        }
        resultado.sort((a, b) -> b.dataInicio().compareTo(a.dataInicio()));
        return resultado;
    }

    @Transactional(readOnly = true)
    public ReservaCliente detalhe(String sub, UUID reservaId) {
        Localizada l = localizar(sub, reservaId);
        return toDto(l.reserva(), l.loja());
    }

    @Transactional(readOnly = true)
    public Checklist checklist(String sub, UUID reservaId, boolean emailVerified) {
        Localizada l = localizar(sub, reservaId);
        Reserva r = l.reserva();

        boolean pagamentoOk = r.getPagamentoStatus() == Reserva.PagamentoStatus.CONFIRMADO;
        boolean habilitacaoOk = habilitacaoRepository.findByReservaId(r.getId())
            .map(h -> Boolean.TRUE.equals(h.getResolvida()))
            .orElse(false);
        boolean termosOk = aceiteRepository
            .findFirstByReservaIdOrderByAceitoEmDesc(r.getId())
            .isPresent();
        boolean garantida = pagamentoOk && emailVerified;

        return Checklist.builder()
            .emailVerified(emailVerified)
            .pagamentoStatus(r.getPagamentoStatus() != null ? r.getPagamentoStatus().name() : "AGUARDANDO")
            .pagamentoTipo(r.getPagamentoTipo() != null ? r.getPagamentoTipo().name() : null)
            .pagamentoOk(pagamentoOk)
            .habilitacaoOk(habilitacaoOk)
            .termosOk(termosOk)
            .garantida(garantida)
            .prontaParaCheckin(garantida && habilitacaoOk && termosOk)
            .build();
    }

    // ============================ Comprovante ============================

    public record AnexarComprovanteCmd(
        Reserva.PagamentoTipo tipo, BigDecimal valorInformado,
        String contentType, String dataBase64) {}

    @Transactional
    public ReservaCliente anexarComprovante(String sub, UUID reservaId, AnexarComprovanteCmd cmd) {
        if (cmd.tipo() == null) {
            throw new BusinessException("Informe o tipo do pagamento (SINAL ou TOTAL)");
        }
        if (cmd.valorInformado() == null || cmd.valorInformado().signum() <= 0) {
            throw new BusinessException("Informe o valor transferido");
        }
        if (cmd.contentType() == null || !COMPROVANTE_TIPOS.contains(cmd.contentType())) {
            throw new BusinessException("Comprovante deve ser imagem (JPEG/PNG/WebP) ou PDF");
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(cmd.dataBase64());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Arquivo do comprovante inválido");
        }
        if (bytes.length == 0 || bytes.length > COMPROVANTE_MAX_BYTES) {
            throw new BusinessException("Comprovante deve ter até 5 MB");
        }

        Localizada l = localizar(sub, reservaId);
        Reserva r = l.reserva();
        if (r.getPagamentoStatus() == Reserva.PagamentoStatus.CONFIRMADO) {
            throw new BusinessException("O pagamento desta reserva já foi confirmado");
        }

        String ext = switch (cmd.contentType()) {
            case "application/pdf" -> "pdf";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
        String key = "%s/reserva/%s/comprovante-%d.%s"
            .formatted(l.loja().tenantId(), r.getId(), System.currentTimeMillis(), ext);
        storageService.putObject(key, bytes, cmd.contentType());

        comprovanteRepository.save(ReservaComprovante.builder()
            .tenantId(l.loja().tenantId())
            .reservaId(r.getId())
            .s3Key(key)
            .hashSha256(sha256(bytes))
            .tipo("PIX")
            .build());

        r.setPagamentoTipo(cmd.tipo());
        r.setPagamentoValorInformado(cmd.valorInformado());
        r.setPagamentoStatus(Reserva.PagamentoStatus.EM_ANALISE);
        reservaRepository.save(r);

        log.info("Comprovante anexado pelo cliente: reserva={}, tipo={}, valor={}",
            r.getId(), cmd.tipo(), cmd.valorInformado());
        return toDto(r, l.loja());
    }

    // ============================ Expiração (job) ============================

    /**
     * Expira pré-reservas do PORTAL sem pagamento em análise/confirmado após o
     * prazo. Itera todos os tenants (set_config por tenant — sem bypass de RLS).
     * Chamado pelo ReservaExpiracaoJob. Cada tenant roda em transação própria
     * (TransactionTemplate — auto-invocação não passa pelo proxy @Transactional).
     */
    public int expirarPreReservasPortal(int horas) {
        List<UUID> tenants = transactionTemplate.execute(tx -> {
            @SuppressWarnings("unchecked")
            List<UUID> ids = entityManager
                .createNativeQuery("SELECT id FROM tenant WHERE status = 'ATIVO'")
                .getResultList();
            return ids;
        });

        int total = 0;
        for (UUID tenantId : tenants) {
            Integer n = transactionTemplate.execute(tx ->
                expirarPreReservasPortalDoTenant(tenantId, horas));
            total += n != null ? n : 0;
        }
        return total;
    }

    private int expirarPreReservasPortalDoTenant(UUID tenantId, int horas) {
        fixarTenant(tenantId);
        int n = entityManager.createNativeQuery("""
                UPDATE reserva
                   SET status = 'EXPIRADA', updated_at = now()
                 WHERE tenant_id = :tenant
                   AND canal = 'PORTAL'
                   AND ativo = true
                   AND status = 'PENDENTE'
                   AND sinal_pago = false
                   AND pagamento_status = 'AGUARDANDO'
                   AND created_at < now() - make_interval(hours => :horas)
                """)
            .setParameter("tenant", tenantId)
            .setParameter("horas", horas)
            .executeUpdate();
        if (n > 0) {
            log.info("Pré-reservas de portal expiradas: tenant={}, quantidade={}", tenantId, n);
        }
        return n;
    }

    // ============================ Termos / aceite (P2) ============================

    @Transactional(readOnly = true)
    public AceiteOtpService.OtpStatus otpStatus(String sub, UUID reservaId) {
        Localizada l = localizar(sub, reservaId);
        return aceiteOtpService.status(l.reserva().getId());
    }

    @Transactional
    public AceiteOtpService.EnvioResultado otpEnviar(String sub, UUID reservaId) {
        Localizada l = localizar(sub, reservaId);
        return aceiteOtpService.enviar(l.reserva().getId());
    }

    @Transactional
    public boolean otpVerificar(String sub, UUID reservaId, String codigo) {
        Localizada l = localizar(sub, reservaId);
        return aceiteOtpService.verificar(l.reserva().getId(), codigo);
    }

    /** Cliente assina o termo remotamente (SignaturePad no celular; OTP se o tenant exigir). */
    @Transactional
    public void assinarTermo(String sub, UUID reservaId, String assinaturaBase64,
                             String ip, String userAgent) {
        Localizada l = localizar(sub, reservaId);
        byte[] assinatura = decodeBase64Imagem(assinaturaBase64);
        aceiteService.registrar(l.reserva().getId(),
            com.jetski.locacoes.domain.ReservaAceite.Metodo.SIGNATURE_PAD,
            assinatura, ip, userAgent, "PORTAL");
        log.info("Termo assinado pelo cliente no portal: reserva={}", reservaId);
    }

    // ============================ Habilitação — caminho A (P2) ============================

    public record HabilitacaoCliente(
        String via, String chaCategoria, String chaNumero,
        java.time.LocalDate chaValidade, boolean resolvida, boolean temFotoCha) {}

    @Transactional(readOnly = true)
    public HabilitacaoCliente habilitacao(String sub, UUID reservaId) {
        Localizada l = localizar(sub, reservaId);
        boolean temFoto = clienteAnexoService
            .buscar(l.reserva().getClienteId(), com.jetski.locacoes.domain.ClienteAnexo.Tipo.CHA)
            .isPresent();
        return habilitacaoService.getByReserva(l.reserva().getId())
            .map(h -> new HabilitacaoCliente(
                h.getVia() != null ? h.getVia().name() : null,
                h.getChaCategoria(), h.getChaNumero(), h.getChaValidade(),
                Boolean.TRUE.equals(h.getResolvida()), temFoto))
            .orElse(new HabilitacaoCliente(null, null, null, null, false, temFoto));
    }

    public record EnviarChaCmd(
        String categoria, String numero, java.time.LocalDate validade, String fotoBase64) {}

    /** Cliente informa a própria CHA (caminho A) + foto do documento. */
    @Transactional
    public HabilitacaoCliente enviarCha(String sub, UUID reservaId, EnviarChaCmd cmd) {
        if (cmd.numero() == null || cmd.numero().isBlank()) {
            throw new BusinessException("Informe o número da habilitação (CHA)");
        }
        if (cmd.validade() != null && cmd.validade().isBefore(java.time.LocalDate.now())) {
            throw new BusinessException("Sua habilitação está vencida — use o caminho da CHA-MTA-E");
        }
        Localizada l = localizar(sub, reservaId);

        if (cmd.fotoBase64() != null && !cmd.fotoBase64().isBlank()) {
            clienteAnexoService.salvar(l.reserva().getClienteId(),
                com.jetski.locacoes.domain.ClienteAnexo.Tipo.CHA, cmd.fotoBase64());
        }

        com.jetski.locacoes.domain.ReservaHabilitacao dados =
            com.jetski.locacoes.domain.ReservaHabilitacao.builder()
                .via(com.jetski.locacoes.domain.ReservaHabilitacao.Via.CHA)
                .chaCategoria(cmd.categoria())
                .chaNumero(cmd.numero().trim())
                .chaValidade(cmd.validade())
                .build();
        habilitacaoService.registrar(l.reserva().getId(), dados);
        log.info("CHA enviada pelo cliente no portal: reserva={}", reservaId);
        return habilitacao(sub, reservaId);
    }

    /** Decodifica dataURL ou base64 puro de imagem. */
    private static byte[] decodeBase64Imagem(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new BusinessException("Assinatura é obrigatória");
        }
        String dados = base64.contains(",") ? base64.substring(base64.indexOf(',') + 1) : base64;
        try {
            return Base64.getDecoder().decode(dados);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Assinatura inválida");
        }
    }

    // ============================ Internos ============================

    private record Localizada(Reserva reserva, Loja loja) {}

    /** Localiza a reserva entre os vínculos do cliente (e valida a posse). */
    private Localizada localizar(String sub, UUID reservaId) {
        for (CustomerAccountService.VinculoLoja v : customerAccountService.vinculos(sub)) {
            fixarTenant(v.getTenantId());
            Optional<Reserva> r = reservaRepository.findById(reservaId);
            if (r.isPresent() && r.get().getClienteId().equals(v.getClienteId())) {
                return new Localizada(r.get(), lojaByTenantId(v.getTenantId()));
            }
        }
        throw new NotFoundException("Reserva não encontrada: " + reservaId);
    }

    public record Loja(UUID tenantId, String slug, String nome, String cidade, String pixChave, String cnpj) {}

    /** Lookup público de loja por slug (usado pela disponibilidade pública). */
    public Optional<Loja> lojaPublica(String slug) {
        return lojaBySlug(slug);
    }

    private Optional<Loja> lojaBySlug(String slug) {
        return lojaQuery("SELECT id, slug, razao_social, cidade, pix_chave, cnpj FROM tenant " +
            "WHERE slug = :param AND status = 'ATIVO'", slug);
    }

    private Loja lojaByTenantId(UUID tenantId) {
        return lojaQuery("SELECT id, slug, razao_social, cidade, pix_chave, cnpj FROM tenant " +
            "WHERE id = :param", tenantId)
            .orElseThrow(() -> new NotFoundException("Loja não encontrada: " + tenantId));
    }

    private Optional<Loja> lojaQuery(String sql, Object param) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(sql)
            .setParameter("param", param)
            .getResultList();
        return rows.stream().findFirst().map(r -> new Loja(
            (UUID) r[0], (String) r[1], (String) r[2], (String) r[3], (String) r[4], (String) r[5]));
    }

    /** Fixa app.tenant_id (transaction-local) — RLS estrita continua valendo. */
    private void fixarTenant(UUID tenantId) {
        entityManager.createNativeQuery("SELECT set_config('app.tenant_id', :tid, true)")
            .setParameter("tid", tenantId.toString())
            .getSingleResult();
        TenantContext.setTenantId(tenantId);
    }

    // ============================ DTOs ============================

    @lombok.Value
    @lombok.Builder
    public static class Checklist {
        boolean emailVerified;
        String pagamentoStatus;
        String pagamentoTipo;
        boolean pagamentoOk;
        boolean habilitacaoOk;
        boolean termosOk;
        boolean garantida;
        boolean prontaParaCheckin;
    }

    public record Pagamento(
        String tipo, String status, String motivoRecusa,
        BigDecimal valorTotal, BigDecimal valorSinal, BigDecimal valorInformado,
        String pixChave, String pixCopiaEColaSinal, String pixCopiaEColaTotal) {}

    public record ReservaCliente(
        UUID id, String lojaSlug, String lojaNome, String lojaCnpj,
        UUID modeloId, String modeloNome,
        LocalDateTime dataInicio, LocalDateTime dataFimPrevista,
        String status, String observacoes, Pagamento pagamento) {}

    private ReservaCliente toDto(Reserva r, Loja loja) {
        Modelo modelo = modeloService.findById(r.getModeloId());

        BigDecimal valorTotal = valorEstimado(modelo, r.getDataInicio(), r.getDataFimPrevista());
        BigDecimal valorSinal = valorTotal
            .multiply(BigDecimal.valueOf(sinalPercentual))
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        String pixSinal = null;
        String pixTotal = null;
        if (loja.pixChave() != null && !loja.pixChave().isBlank() && valorTotal.signum() > 0) {
            String cidade = loja.cidade() != null ? loja.cidade() : "Brasil";
            pixSinal = BrCodePix.gerar(loja.pixChave().trim(), valorSinal, loja.nome(), cidade);
            pixTotal = BrCodePix.gerar(loja.pixChave().trim(), valorTotal, loja.nome(), cidade);
        }

        Pagamento pagamento = new Pagamento(
            r.getPagamentoTipo() != null ? r.getPagamentoTipo().name() : null,
            r.getPagamentoStatus() != null ? r.getPagamentoStatus().name() : "AGUARDANDO",
            r.getPagamentoMotivoRecusa(),
            valorTotal, valorSinal, r.getPagamentoValorInformado(),
            loja.pixChave(), pixSinal, pixTotal);

        return new ReservaCliente(
            r.getId(), loja.slug(), loja.nome(), loja.cnpj(),
            modelo.getId(), modelo.getNome(),
            r.getDataInicio(), r.getDataFimPrevista(),
            r.getStatus() != null ? r.getStatus().name() : null,
            r.getObservacoes(), pagamento);
    }

    /** Valor estimado = preço base/hora × duração (frações de hora proporcionais). */
    private BigDecimal valorEstimado(Modelo modelo, LocalDateTime inicio, LocalDateTime fim) {
        if (modelo.getPrecoBaseHora() == null || inicio == null || fim == null || !fim.isAfter(inicio)) {
            return BigDecimal.ZERO;
        }
        long minutos = Duration.between(inicio, fim).toMinutes();
        return modelo.getPrecoBaseHora()
            .multiply(BigDecimal.valueOf(minutos))
            .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            return null;
        }
    }
}
