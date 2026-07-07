package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.Reserva;
import com.jetski.locacoes.domain.ReservaHabilitacao;
import com.jetski.locacoes.domain.ReservaHabilitacao.Via;
import com.jetski.locacoes.internal.repository.ReservaHabilitacaoRepository;
import com.jetski.locacoes.internal.repository.ReservaRepository;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Serviço de habilitação do condutor (CHA existente ou emissão CHA-MTA-E + GRU).
 * 1:1 com reserva (upsert). {@code resolvida} = CHA coletada OU GRU paga.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HabilitacaoService {

    /**
     * Validade da CHA-MTA-E temporária, contada da EMISSÃO do documento
     * (reserva.documento_emitido_em) — mesma regra impressa no PDF (5-B-2).
     * Vigência comparada em Instant; exibição em America/Sao_Paulo.
     */
    public static final int VALIDADE_TEMPORARIA_DIAS = 30;

    /**
     * Marcador em {@code chaCategoria} de habilitação derivada do REUSO de uma
     * temporária vigente (chaNumero = nº da GRU de origem). A temporária É uma
     * CHA — o reuso passa pela via CHA (sem GRU nova, sem envio à Marinha,
     * sem débito de créditos).
     */
    public static final String CHA_CATEGORIA_TEMPORARIA = "MTA-E TEMPORÁRIA";

    private final ReservaHabilitacaoRepository repository;
    private final ReservaRepository reservaRepository;
    private final com.jetski.shared.storage.StorageService storageService;
    private final DocumentoPdfService documentoPdfService;
    private final ClienteNotificacaoService clienteNotificacaoService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Optional<ReservaHabilitacao> getByReserva(UUID reservaId) {
        return repository.findByReservaId(reservaId);
    }

    /**
     * Anexa a devolutiva da Marinha (CHA-MTA-E confirmada) — a resposta chega
     * MANUALMENTE por e-mail à loja; este registro é o que torna a temporária
     * elegível para reuso em novas reservas.
     *
     * <p>Re-upload substitui o PDF, mas preserva o instante/autor da
     * confirmação original; a notificação ao cliente sai só na primeira.
     */
    @Transactional
    public ReservaHabilitacao registrarDevolutivaMarinha(
            UUID reservaId, byte[] conteudo, boolean ehImagem, UUID usuarioId) {
        ReservaHabilitacao hab = repository.findByReservaId(reservaId)
            .orElseThrow(() -> new NotFoundException("Habilitação não encontrada: " + reservaId));
        Reserva reserva = reservaRepository.findById(reservaId)
            .orElseThrow(() -> new NotFoundException("Reserva não encontrada: " + reservaId));

        if (conteudo == null || conteudo.length == 0) {
            throw new BusinessException("Devolutiva vazia");
        }
        if (hab.getVia() != ReservaHabilitacao.Via.EMA) {
            throw new BusinessException(
                "A devolutiva da Marinha só se aplica a habilitações emitidas via EMA.");
        }
        if (!Boolean.TRUE.equals(hab.getGruPago()) || reserva.getDocumentoEmitidoEm() == null) {
            throw new BusinessException(
                "A devolutiva pressupõe a emissão concluída (GRU paga e documentação enviada à Marinha).");
        }

        byte[] pdf = ehImagem
            ? documentoPdfService.imagemParaPdf(conteudo, "CHA-MTA-E CONFIRMADA PELA MARINHA").conteudo()
            : conteudo;
        String key = String.format("%s/reserva/%s/cha-mtae-confirmada.pdf", hab.getTenantId(), reservaId);
        storageService.putObject(key, pdf, "application/pdf");

        boolean substituicao = hab.getMarinhaConfirmadaEm() != null;
        hab.setChaMtaeS3Key(key);
        if (!substituicao) {
            hab.setMarinhaConfirmadaEm(Instant.now());
            hab.setMarinhaConfirmadaPor(usuarioId);
            clienteNotificacaoService.notificar(hab.getTenantId(), reserva.getClienteId(),
                com.jetski.locacoes.domain.ClienteNotificacao.CHA_CONFIRMADA,
                "Sua CHA-MTA-E foi confirmada pela Marinha ✅",
                "A confirmação oficial já está disponível para download em Minhas habilitações.",
                "/conta/perfil");
        }
        ReservaHabilitacao salvo = repository.save(hab);
        eventPublisher.publishEvent(com.jetski.locacoes.event.ChaMtaeConfirmadaEvent.of(
            hab.getTenantId(), reservaId, usuarioId, substituicao));
        log.info("Devolutiva da Marinha registrada: reserva={}, substituicao={}", reservaId, substituicao);
        return salvo;
    }

    /** Bytes do PDF da devolutiva já anexada (stream autenticado). */
    @Transactional(readOnly = true)
    public byte[] baixarDevolutivaPdf(UUID reservaId) {
        ReservaHabilitacao hab = repository.findByReservaId(reservaId)
            .orElseThrow(() -> new NotFoundException("Habilitação não encontrada: " + reservaId));
        if (hab.getChaMtaeS3Key() == null) {
            throw new NotFoundException("Devolutiva da Marinha ainda não anexada para a reserva " + reservaId);
        }
        return storageService.getObject(hab.getChaMtaeS3Key());
    }

    @Transactional
    public ReservaHabilitacao registrar(UUID reservaId, ReservaHabilitacao dados) {
        Reserva reserva = reservaRepository.findById(reservaId)
            .orElseThrow(() -> new NotFoundException("Reserva não encontrada: " + reservaId));

        ReservaHabilitacao h = repository.findByReservaId(reservaId)
            .orElseGet(ReservaHabilitacao::new);

        h.setTenantId(reserva.getTenantId());
        h.setReservaId(reservaId);
        h.setVia(dados.getVia());
        h.setChaCategoria(dados.getChaCategoria());
        h.setChaNumero(dados.getChaNumero());
        h.setChaValidade(dados.getChaValidade());
        if (dados.getVideoaulaEm() != null) {
            h.setVideoaulaEm(dados.getVideoaulaEm());
        }
        // Preserva quando o passo não envia o campo (fluxo dividido: GRU x pré-requisitos).
        if (dados.getAnexoSaude() != null) h.setAnexoSaude(dados.getAnexoSaude());
        if (dados.getAnexoRegras() != null) h.setAnexoRegras(dados.getAnexoRegras());
        if (dados.getAnexoResidencia() != null) h.setAnexoResidencia(dados.getAnexoResidencia());
        if (dados.getUsaLentes() != null) h.setUsaLentes(dados.getUsaLentes());
        if (dados.getUsaAparelho() != null) h.setUsaAparelho(dados.getUsaAparelho());
        if (dados.getInstrutorId() != null) h.setInstrutorId(dados.getInstrutorId());
        // Preserva número/valor já gerados se o form não os reenviar.
        if (dados.getGruNumero() != null) {
            h.setGruNumero(dados.getGruNumero());
        }
        if (dados.getGruValor() != null) {
            h.setGruValor(dados.getGruValor());
        }

        // NÃO rebaixar uma GRU já paga (ex.: confirmada via "Verificar pagamento").
        boolean gruPago = Boolean.TRUE.equals(dados.getGruPago()) || Boolean.TRUE.equals(h.getGruPago());
        h.setGruPago(gruPago);
        if (gruPago && h.getGruPagoEm() == null) {
            h.setGruPagoEm(Instant.now());
        }

        h.setResolvida(computeResolvida(h));

        ReservaHabilitacao saved = repository.save(h);
        log.info("Habilitação registrada: reservaId={}, via={}, resolvida={}",
                 reservaId, saved.getVia(), saved.getResolvida());
        return saved;
    }

    private boolean computeResolvida(ReservaHabilitacao h) {
        if (h.getVia() == Via.CHA) {
            return h.getChaNumero() != null && !h.getChaNumero().isBlank();
        }
        // EMA resolve quando a GRU está paga
        return Boolean.TRUE.equals(h.getGruPago());
    }
}
