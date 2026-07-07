package com.jetski.locacoes.internal;

import com.jetski.locacoes.api.ModeloService;
import com.jetski.locacoes.api.dto.AceiteResponse;
import com.jetski.locacoes.api.dto.DocumentoConsultaResponse;
import com.jetski.locacoes.api.dto.FolioExtratoResponse;
import com.jetski.locacoes.api.dto.HabilitacaoResponse;
import com.jetski.locacoes.api.dto.ReservaFichaResponse;
import com.jetski.locacoes.api.dto.ReservaFichaResponse.CicloGru;
import com.jetski.locacoes.api.dto.ReservaFichaResponse.ClienteResumo;
import com.jetski.locacoes.api.dto.ReservaFichaResponse.PasseioResumo;
import com.jetski.locacoes.api.dto.ReservaResponse;
import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.domain.DocumentoEmitido;
import com.jetski.locacoes.domain.Jetski;
import com.jetski.locacoes.domain.Modelo;
import com.jetski.locacoes.domain.Reserva;
import com.jetski.locacoes.domain.ReservaAceite;
import com.jetski.locacoes.domain.ReservaHabilitacao;
import com.jetski.locacoes.internal.repository.DocumentoEmitidoRepository;
import com.jetski.shared.security.TenantContext;
import com.jetski.shared.storage.StorageService;
import com.jetski.tenant.TenantQueryService;
import com.jetski.tenant.domain.Tenant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Ficha da reserva: agrega os dados da página de detalhe (JSON) e monta o
 * PDF white-label (logo/dados da loja via TenantQueryService — módulo tenant
 * expõe só a query API; branding nulo ⇒ identidade padrão Meu Jet).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservaFichaService {

    private final ReservaService reservaService;
    private final ClienteService clienteService;
    private final ModeloService modeloService;
    private final JetskiService jetskiService;
    private final HabilitacaoService habilitacaoService;
    private final AceiteService aceiteService;
    private final DocumentoConsultaService documentoConsultaService;
    private final DocumentoEmitidoRepository documentoRepository;
    private final TenantQueryService tenantQueryService;
    private final StorageService storageService;
    private final ReservaFichaPdfService pdfService;

    @Transactional(readOnly = true)
    public ReservaFichaResponse ficha(UUID reservaId) {
        Reserva reserva = reservaService.findById(reservaId);
        Cliente cliente = clienteService.findById(reserva.getClienteId());
        Modelo modelo = safeModelo(reserva.getModeloId());
        Jetski jetski = reserva.getJetskiId() != null ? safeJetski(reserva.getJetskiId()) : null;
        ReservaHabilitacao hab = habilitacaoService.getByReserva(reservaId).orElse(null);
        ReservaAceite aceite = aceiteService.getUltimo(reservaId).orElse(null);
        List<DocumentoConsultaResponse> documentos =
            documentoConsultaService.listarPorReserva(reservaId);
        DocumentoEmitido docRecente = documentoRepository
            .findByReservaIdOrderByEmitidoEmDesc(reservaId).stream().findFirst().orElse(null);

        return new ReservaFichaResponse(
            ReservaResponse.from(reserva),
            new ClienteResumo(cliente.getId(), cliente.getNome(),
                mascarar(cliente.getDocumento()), cliente.getEmail(),
                cliente.getTelefone(), cliente.getWhatsapp()),
            new PasseioResumo(
                modelo != null ? modelo.getId() : reserva.getModeloId(),
                modelo != null ? modelo.getNome() : null,
                jetski != null ? jetski.getId() : reserva.getJetskiId(),
                jetski != null ? jetski.getSerie() : null),
            FolioExtratoResponse.from(reservaService.extrato(reservaId)),
            hab != null ? toHabilitacaoResponse(hab) : null,
            aceite != null ? toAceiteResponse(aceite) : null,
            hab != null && hab.getVia() == ReservaHabilitacao.Via.EMA && hab.getGruNumero() != null
                ? new CicloGru(hab.getGruNumero(), hab.getGruValor(),
                    hab.getGruGeradaEm() != null ? hab.getGruGeradaEm() : hab.getCreatedAt(),
                    hab.getGruPago(), hab.getGruPagoEm(),
                    reserva.getDocumentoEmitidoEm(),
                    docRecente != null ? docRecente.getMarinhaEnviadoEm() : null,
                    hab.getMarinhaConfirmadaEm())
                : null,
            documentos);
    }

    /** Bytes do PDF da ficha (link temporário mintado pelo controller). */
    @Transactional(readOnly = true)
    public byte[] gerarPdf(UUID reservaId) {
        ReservaFichaResponse f = ficha(reservaId);
        Tenant tenant = tenantQueryService.findById(TenantContext.getTenantId());

        byte[] logoBytes = null;
        if (tenant != null && tenant.getBranding() != null && tenant.getBranding().temLogo()) {
            try {
                logoBytes = storageService.getObject(tenant.getBranding().logoKey());
            } catch (Exception e) {
                log.warn("Logo do tenant indisponível — ficha sai com identidade padrão: {}", e.getMessage());
            }
        }

        return pdfService.gerar(ReservaFichaPdfService.DadosFicha.builder()
            .lojaNome(tenant != null ? tenant.getRazaoSocial() : "Meu Jet")
            .lojaCnpj(tenant != null ? tenant.getCnpj() : null)
            .lojaCidade(tenant != null ? tenant.getCidade() : null)
            .logoBytes(logoBytes)
            .ficha(f)
            .geradoPor(TenantContext.getUsuarioId() != null
                ? TenantContext.getUsuarioId().toString() : null)
            .geradoEm(Instant.now())
            .build());
    }

    private Modelo safeModelo(UUID id) {
        try { return modeloService.findById(id); } catch (Exception e) { return null; }
    }

    private Jetski safeJetski(UUID id) {
        try { return jetskiService.findById(id); } catch (Exception e) { return null; }
    }

    private HabilitacaoResponse toHabilitacaoResponse(ReservaHabilitacao h) {
        return HabilitacaoResponse.builder()
            .id(h.getId())
            .reservaId(h.getReservaId())
            .via(h.getVia() != null ? h.getVia().name() : null)
            .chaCategoria(h.getChaCategoria())
            .chaNumero(h.getChaNumero())
            .chaValidade(h.getChaValidade())
            .gruNumero(h.getGruNumero())
            .gruValor(h.getGruValor())
            .gruPago(h.getGruPago())
            .gruPagoEm(h.getGruPagoEm())
            .marinhaConfirmadaEm(h.getMarinhaConfirmadaEm())
            .devolutivaDisponivel(h.getChaMtaeS3Key() != null)
            .resolvida(h.getResolvida())
            .build();
    }

    private AceiteResponse toAceiteResponse(ReservaAceite a) {
        return AceiteResponse.builder()
            .id(a.getId())
            .reservaId(a.getReservaId())
            .metodo(a.getMetodo() != null ? a.getMetodo().name() : null)
            .hashSha256(a.getHashSha256())
            .origem(a.getOrigem())
            .aceitoEm(a.getAceitoEm())
            .build();
    }

    /** ***.***. 789-00 — mesma máscara do fluxo do portal. */
    private static String mascarar(String cpf) {
        String d = cpf == null ? "" : cpf.replaceAll("\\D", "");
        if (d.length() != 11) return cpf != null && !cpf.isBlank() ? "***" : null;
        return "***.***." + d.substring(6, 9) + "-" + d.substring(9);
    }
}
