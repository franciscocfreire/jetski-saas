package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.domain.CustomerHabilitacao;
import com.jetski.locacoes.domain.Reserva;
import com.jetski.locacoes.domain.ReservaHabilitacao;
import com.jetski.locacoes.internal.repository.ClienteRepository;
import com.jetski.locacoes.internal.repository.CustomerHabilitacaoRepository;
import com.jetski.locacoes.internal.repository.ReservaHabilitacaoRepository;
import com.jetski.locacoes.internal.repository.ReservaRepository;
import com.jetski.shared.storage.StorageService;
import com.jetski.tenant.TenantQueryService;
import com.jetski.tenant.domain.Tenant;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Espelha a habilitação temporária (CHA-MTA-E) para o registro GLOBAL do
 * cliente ({@code customer_habilitacao}) — o dado nasce do cliente, by design.
 *
 * <p>Chamado na MESMA transação nos pontos que materializam o direito:
 * emissão dos documentos, confirmação de pagamento da GRU e devolutiva da
 * Marinha. Upsert idempotente por nº de GRU: só grava quando o trio
 * (via EMA + GRU paga + documento emitido) está completo.
 *
 * <p>Best-effort deliberado: uma falha aqui NUNCA derruba a operação de
 * negócio — a cópia tenant-scoped continua existindo e a leitura do portal
 * mantém a varredura por vínculos como fallback. O erro é logado alto.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerHabilitacaoSyncService {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    private final CustomerHabilitacaoRepository customerHabilitacaoRepository;
    private final ReservaRepository reservaRepository;
    private final ReservaHabilitacaoRepository habilitacaoRepository;
    private final ClienteRepository clienteRepository;
    private final TenantQueryService tenantQueryService;
    private final StorageService storageService;
    private final EntityManager entityManager;

    /** Sincroniza (upsert) o registro global a partir do estado atual da reserva. */
    public void sync(UUID reservaId) {
        try {
            doSync(reservaId);
        } catch (Exception e) {
            log.error("Sync da habilitação global do cliente falhou (reserva={}) — "
                + "cópia tenant-scoped preservada; leitura tem fallback", reservaId, e);
        }
    }

    private void doSync(UUID reservaId) {
        ReservaHabilitacao hab = habilitacaoRepository.findByReservaId(reservaId).orElse(null);
        if (hab == null || hab.getVia() != ReservaHabilitacao.Via.EMA
                || hab.getGruNumero() == null || hab.getGruNumero().isBlank()
                || !Boolean.TRUE.equals(hab.getGruPago())) {
            return; // direito ainda não materializado
        }
        Reserva reserva = reservaRepository.findById(reservaId).orElse(null);
        if (reserva == null || reserva.getDocumentoEmitidoEm() == null) {
            return;
        }
        Cliente cliente = clienteRepository.findById(reserva.getClienteId()).orElse(null);
        if (cliente == null || cliente.getDocumento() == null) {
            return;
        }
        String cpf = cliente.getDocumento().replaceAll("\\D", "");
        if (cpf.isEmpty()) {
            return;
        }

        CustomerHabilitacao registro = customerHabilitacaoRepository
            .findByGruNumero(hab.getGruNumero())
            .orElseGet(() -> CustomerHabilitacao.builder()
                .gruNumero(hab.getGruNumero())
                .build());

        registro.setCpf(cpf);
        registro.setEmitidaEm(reserva.getDocumentoEmitidoEm());
        registro.setValidaAte(LocalDate.ofInstant(
            reserva.getDocumentoEmitidoEm()
                .plus(Duration.ofDays(HabilitacaoService.VALIDADE_TEMPORARIA_DIAS)),
            ZONA));
        registro.setMarinhaConfirmadaEm(hab.getMarinhaConfirmadaEm());
        registro.setTenantOrigem(reserva.getTenantId());
        registro.setReservaOrigem(reserva.getId());

        Tenant tenant = tenantQueryService.findById(reserva.getTenantId());
        if (tenant != null) {
            registro.setLojaOrigemNome(tenant.getRazaoSocial());
        }

        // Vínculo do portal, se existir (cliente de balcão pode não ter conta)
        vinculoDoCliente(cliente.getId()).ifPresent(v -> {
            registro.setProvider(v.provider());
            registro.setProviderUserId(v.providerUserId());
        });

        // Devolutiva confirmada: copia o PDF para o prefixo da PLATAFORMA —
        // o prefixo do tenant é apagado num eventual expurgo da loja.
        if (hab.getChaMtaeS3Key() != null && registro.getPdfS3Key() == null) {
            String key = String.format("_platform/customers/%s/cha-mtae-%s.pdf",
                cpf, hab.getGruNumero().replaceAll("[^A-Za-z0-9._-]", "_"));
            byte[] pdf = storageService.getObject(hab.getChaMtaeS3Key());
            storageService.putObject(key, pdf, "application/pdf");
            registro.setPdfS3Key(key);
        }

        customerHabilitacaoRepository.save(registro);
        log.info("Habilitação global do cliente sincronizada: gru={}, cpf=***, confirmada={}",
            hab.getGruNumero(), registro.getMarinhaConfirmadaEm() != null);
    }

    private record Vinculo(String provider, String providerUserId) {}

    /** Vínculo IdP do cliente (query nativa: tabela tem policy self-read + tenant). */
    @SuppressWarnings("unchecked")
    private java.util.Optional<Vinculo> vinculoDoCliente(UUID clienteId) {
        List<Object[]> rows = entityManager.createNativeQuery(
                "SELECT provider, provider_user_id FROM cliente_identity_provider "
                + "WHERE cliente_id = :cid LIMIT 1")
            .setParameter("cid", clienteId)
            .getResultList();
        return rows.stream().findFirst()
            .map(r -> new Vinculo((String) r[0], (String) r[1]));
    }
}
