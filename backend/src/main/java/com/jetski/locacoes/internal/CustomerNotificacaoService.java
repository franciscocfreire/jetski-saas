package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.ClienteNotificacao;
import com.jetski.locacoes.internal.repository.ClienteNotificacaoRepository;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.security.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Leitura/gestão das notificações pelo CLIENTE (portal): agrega os vínculos
 * do sub — mesmo padrão de minhasReservas/minhasLocacoes.
 */
@Service
@RequiredArgsConstructor
public class CustomerNotificacaoService {

    private final EntityManager entityManager;
    private final CustomerAccountService customerAccountService;
    private final ClienteNotificacaoRepository repository;

    public record NotificacaoCliente(
        UUID id, String lojaSlug, String tipo, String titulo,
        String mensagem, String link, boolean lida, Instant criadaEm) {}

    public record Caixa(long naoLidas, List<NotificacaoCliente> itens) {}

    @Transactional(readOnly = true)
    public Caixa caixa(String sub) {
        List<NotificacaoCliente> itens = new ArrayList<>();
        long naoLidas = 0;
        for (CustomerAccountService.VinculoLoja v : customerAccountService.vinculos(sub)) {
            fixarTenant(v.getTenantId());
            naoLidas += repository.countByTenantIdAndClienteIdAndLidaFalse(v.getTenantId(), v.getClienteId());
            for (ClienteNotificacao n : repository
                    .findTop50ByTenantIdAndClienteIdOrderByCreatedAtDesc(v.getTenantId(), v.getClienteId())) {
                itens.add(new NotificacaoCliente(n.getId(), v.getSlug(), n.getTipo(), n.getTitulo(),
                    n.getMensagem(), n.getLink(), Boolean.TRUE.equals(n.getLida()), n.getCreatedAt()));
            }
        }
        itens.sort((a, b) -> b.criadaEm().compareTo(a.criadaEm()));
        return new Caixa(naoLidas, itens.size() > 50 ? itens.subList(0, 50) : itens);
    }

    @Transactional
    public void marcarLida(String sub, UUID notificacaoId) {
        for (CustomerAccountService.VinculoLoja v : customerAccountService.vinculos(sub)) {
            fixarTenant(v.getTenantId());
            var n = repository.findByIdAndTenantIdAndClienteId(notificacaoId, v.getTenantId(), v.getClienteId());
            if (n.isPresent()) {
                n.get().setLida(true);
                repository.save(n.get());
                return;
            }
        }
        throw new NotFoundException("Notificação não encontrada: " + notificacaoId);
    }

    @Transactional
    public void marcarTodasLidas(String sub) {
        for (CustomerAccountService.VinculoLoja v : customerAccountService.vinculos(sub)) {
            fixarTenant(v.getTenantId());
            for (ClienteNotificacao n : repository
                    .findByTenantIdAndClienteIdAndLidaFalse(v.getTenantId(), v.getClienteId())) {
                n.setLida(true);
                repository.save(n);
            }
        }
    }

    private void fixarTenant(UUID tenantId) {
        entityManager.createNativeQuery("SELECT set_config('app.tenant_id', :tid, true)")
            .setParameter("tid", tenantId.toString())
            .getSingleResult();
        TenantContext.setTenantId(tenantId);
    }
}
