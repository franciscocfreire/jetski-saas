package com.jetski.locacoes.internal;

import com.jetski.locacoes.api.dto.FotoResponse;
import com.jetski.locacoes.domain.Avaliacao;
import com.jetski.locacoes.domain.Jetski;
import com.jetski.locacoes.domain.Locacao;
import com.jetski.locacoes.domain.LocacaoStatus;
import com.jetski.locacoes.domain.Modelo;
import com.jetski.locacoes.internal.repository.AvaliacaoRepository;
import com.jetski.locacoes.internal.repository.JetskiRepository;
import com.jetski.locacoes.internal.repository.LocacaoRepository;
import com.jetski.locacoes.internal.repository.ModeloRepository;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.jetski.shared.security.TenantContext;

/**
 * Histórico de locações do CLIENTE FINAL + avaliações (P4 do portal).
 * Mesmo padrão do CustomerReservaService: posse via vínculos e RLS por
 * set_config transaction-local — sem X-Tenant-Id.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerLocacaoService {

    private final EntityManager entityManager;
    private final CustomerAccountService customerAccountService;
    private final LocacaoRepository locacaoRepository;
    private final JetskiRepository jetskiRepository;
    private final ModeloRepository modeloRepository;
    private final AvaliacaoRepository avaliacaoRepository;
    private final FotoService fotoService;

    // ============================ DTOs ============================

    public record LocacaoCliente(
        UUID id, String lojaSlug, String lojaNome,
        String modeloNome, String jetskiSerie,
        LocalDateTime dataCheckIn, LocalDateTime dataCheckOut,
        Integer minutosUsados, Integer minutosFaturaveis,
        BigDecimal valorBase, BigDecimal valorTotal,
        String status, Integer avaliacaoNota, String avaliacaoComentario) {}

    public record LocacaoClienteDetalhe(
        LocacaoCliente locacao, List<FotoResponse> fotos) {}

    // ============================ Consultas ============================

    @Transactional(readOnly = true)
    public List<LocacaoCliente> minhasLocacoes(String sub) {
        List<LocacaoCliente> resultado = new ArrayList<>();
        for (CustomerAccountService.VinculoLoja v : customerAccountService.vinculos(sub)) {
            fixarTenant(v.getTenantId());
            CustomerReservaService.Loja loja = lojaDoTenant(v.getTenantId());
            for (Locacao l : locacaoRepository
                    .findByTenantIdAndClienteIdOrderByDataCheckInDesc(v.getTenantId(), v.getClienteId())) {
                resultado.add(toDto(l, loja));
            }
        }
        resultado.sort((a, b) -> {
            LocalDateTime da = a.dataCheckIn() != null ? a.dataCheckIn() : LocalDateTime.MIN;
            LocalDateTime db = b.dataCheckIn() != null ? b.dataCheckIn() : LocalDateTime.MIN;
            return db.compareTo(da);
        });
        return resultado;
    }

    @Transactional(readOnly = true)
    public LocacaoClienteDetalhe detalhe(String sub, UUID locacaoId) {
        Localizada l = localizar(sub, locacaoId);
        List<FotoResponse> fotos = fotoService.listFotosByLocacao(l.tenantId(), locacaoId);
        return new LocacaoClienteDetalhe(toDto(l.locacao(), l.loja()), fotos);
    }

    // ============================ Avaliação ============================

    @Transactional
    public LocacaoCliente avaliar(String sub, UUID locacaoId, int nota, String comentario) {
        if (nota < 1 || nota > 5) {
            throw new BusinessException("A nota deve ser de 1 a 5");
        }
        Localizada l = localizar(sub, locacaoId);
        Locacao locacao = l.locacao();

        if (locacao.getStatus() != LocacaoStatus.FINALIZADA) {
            throw new BusinessException("Só é possível avaliar após a locação ser finalizada");
        }
        if (avaliacaoRepository.existsByLocacaoId(locacaoId)) {
            throw new BusinessException("Esta locação já foi avaliada — obrigado!");
        }

        UUID modeloId = jetskiRepository.findById(locacao.getJetskiId())
            .map(Jetski::getModeloId)
            .orElseThrow(() -> new NotFoundException("Jetski da locação não encontrado"));

        avaliacaoRepository.save(Avaliacao.builder()
            .tenantId(l.tenantId())
            .locacaoId(locacaoId)
            .clienteId(locacao.getClienteId())
            .modeloId(modeloId)
            .nota(nota)
            .comentario(comentario != null && !comentario.isBlank() ? comentario.trim() : null)
            .build());

        log.info("Avaliação registrada pelo cliente: locacao={}, nota={}", locacaoId, nota);
        return toDto(locacao, l.loja());
    }

    // ============================ Internos ============================

    private record Localizada(Locacao locacao, UUID tenantId, CustomerReservaService.Loja loja) {}

    private Localizada localizar(String sub, UUID locacaoId) {
        for (CustomerAccountService.VinculoLoja v : customerAccountService.vinculos(sub)) {
            fixarTenant(v.getTenantId());
            Optional<Locacao> l = locacaoRepository.findByIdAndTenantId(locacaoId, v.getTenantId());
            if (l.isPresent() && v.getClienteId().equals(l.get().getClienteId())) {
                return new Localizada(l.get(), v.getTenantId(), lojaDoTenant(v.getTenantId()));
            }
        }
        throw new NotFoundException("Locação não encontrada: " + locacaoId);
    }

    private CustomerReservaService.Loja lojaDoTenant(UUID tenantId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(
                "SELECT id, slug, razao_social, cidade, pix_chave, cnpj FROM tenant WHERE id = :id")
            .setParameter("id", tenantId)
            .getResultList();
        return rows.stream().findFirst()
            .map(r -> new CustomerReservaService.Loja(
                (UUID) r[0], (String) r[1], (String) r[2], (String) r[3], (String) r[4], (String) r[5]))
            .orElseThrow(() -> new NotFoundException("Loja não encontrada"));
    }

    private LocacaoCliente toDto(Locacao l, CustomerReservaService.Loja loja) {
        String modeloNome = null;
        String jetskiSerie = null;
        Optional<Jetski> jetski = jetskiRepository.findById(l.getJetskiId());
        if (jetski.isPresent()) {
            jetskiSerie = jetski.get().getSerie();
            modeloNome = modeloRepository.findById(jetski.get().getModeloId())
                .map(Modelo::getNome).orElse(null);
        }
        Optional<Avaliacao> av = avaliacaoRepository.findByLocacaoId(l.getId());

        return new LocacaoCliente(
            l.getId(), loja.slug(), loja.nome(),
            modeloNome, jetskiSerie,
            l.getDataCheckIn(), l.getDataCheckOut(),
            l.getMinutosUsados(), l.getMinutosFaturaveis(),
            l.getValorBase(), l.getValorTotal(),
            l.getStatus() != null ? l.getStatus().name() : null,
            av.map(Avaliacao::getNota).orElse(null),
            av.map(Avaliacao::getComentario).orElse(null));
    }

    /** Fixa app.tenant_id (transaction-local) — RLS estrita continua valendo. */
    private void fixarTenant(UUID tenantId) {
        entityManager.createNativeQuery("SELECT set_config('app.tenant_id', :tid, true)")
            .setParameter("tid", tenantId.toString())
            .getSingleResult();
        TenantContext.setTenantId(tenantId);
    }
}
