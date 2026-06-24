package com.jetski.locacoes.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.domain.Reserva;
import com.jetski.locacoes.domain.ReservaHabilitacao;
import com.jetski.locacoes.internal.gru.GruClient;
import com.jetski.locacoes.internal.gru.GruContribuinte;
import com.jetski.locacoes.internal.gru.GruException;
import com.jetski.locacoes.internal.gru.GruResultado;
import com.jetski.locacoes.internal.repository.ClienteRepository;
import com.jetski.locacoes.internal.repository.ReservaHabilitacaoRepository;
import com.jetski.locacoes.internal.repository.ReservaRepository;
import com.jetski.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Geração da GRU (taxa da Marinha) + PIX para a habilitação EMA de uma reserva.
 *
 * <p>Best-effort: nunca lança {@link GruException} para fora — em falha devolve
 * {@link GruGeracao} com {@code sucesso=false} + código, para o backoffice cair
 * no <b>fluxo manual</b> (operador digita número/valor). Cada GRU é uma obrigação
 * de pagamento, então uma GRU válida e não vencida é <b>reaproveitada</b> (não
 * gera duplicada).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GruService {

    private final ReservaHabilitacaoRepository habilitacaoRepository;
    private final ReservaRepository reservaRepository;
    private final ClienteRepository clienteRepository;
    private final GruClient gruClient;
    private final ObjectMapper objectMapper;

    /** Resultado da tentativa de geração (sempre devolvido, mesmo em falha). */
    public record GruGeracao(
        boolean sucesso,
        ReservaHabilitacao habilitacao,
        String qrPngBase64,      // só no sucesso "fresco"; null se reaproveitada/falha
        boolean reaproveitada,
        String erroCodigo,
        String erroMensagem
    ) {}

    @Transactional
    public GruGeracao gerarGru(UUID reservaId) {
        Reserva reserva = reservaRepository.findById(reservaId)
            .orElseThrow(() -> new NotFoundException("Reserva não encontrada: " + reservaId));
        Cliente cliente = clienteRepository.findById(reserva.getClienteId())
            .orElseThrow(() -> new NotFoundException("Cliente não encontrado: " + reserva.getClienteId()));

        ReservaHabilitacao hab = habilitacaoRepository.findByReservaId(reservaId)
            .orElseGet(() -> ReservaHabilitacao.builder()
                .tenantId(reserva.getTenantId())
                .reservaId(reservaId)
                .via(ReservaHabilitacao.Via.EMA)
                .build());

        // Idempotência: GRU já paga ou PIX ainda válido → reaproveita (não duplica)
        if (gruValidaReaproveitavel(hab)) {
            log.info("GRU reaproveitada para reserva {} (numero={})", reservaId, hab.getGruNumero());
            return new GruGeracao(true, hab, null, true, null, null);
        }

        GruContribuinte contrib = montarContribuinte(cliente);
        try {
            GruResultado r = gruClient.gerar(contrib);
            hab.setGruNumero(r.gruNumero());
            hab.setGruValor(r.gruValor());
            hab.setGruPixCopiaECola(r.pixCopiaECola());
            hab.setGruPixExpiracao(r.pixExpiracao());
            hab.setGruIdMarinha(r.idGru());
            hab.setGruGeradaEm(Instant.now());
            ReservaHabilitacao salvo = habilitacaoRepository.save(hab);
            return new GruGeracao(true, salvo, r.pixQrPngBase64(), false, null, null);
        } catch (GruException e) {
            log.warn("Falha ao gerar GRU para reserva {}: {} - {}",
                reservaId, e.getCodigo(), e.getMessage());
            return new GruGeracao(false, hab, null, false,
                e.getCodigo().name(), e.getMessage());
        }
    }

    private boolean gruValidaReaproveitavel(ReservaHabilitacao hab) {
        if (Boolean.TRUE.equals(hab.getGruPago())) {
            return true;
        }
        boolean temPix = hab.getGruNumero() != null && hab.getGruPixCopiaECola() != null;
        boolean naoVencida = hab.getGruPixExpiracao() == null
            || hab.getGruPixExpiracao().isAfter(Instant.now());
        return temPix && naoVencida;
    }

    private GruContribuinte montarContribuinte(Cliente c) {
        JsonNode end = parseEndereco(c.getEnderecoJson());
        return new GruContribuinte(
            soDigitos(c.getDocumento()),
            c.getNome(),
            c.getTelefone() != null ? c.getTelefone() : c.getWhatsapp(),
            c.getEmail(),
            mapSexo(c.getGenero()),
            text(end, "cep"),
            text(end, "logradouro"),
            text(end, "numero"),
            text(end, "complemento"),
            text(end, "bairro"),
            text(end, "cidade", "municipio"),
            text(end, "uf", "estado"));
    }

    private JsonNode parseEndereco(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("enderecoJson do cliente inválido ao montar GRU");
            return objectMapper.createObjectNode();
        }
    }

    private static String text(JsonNode n, String... chaves) {
        for (String k : chaves) {
            JsonNode v = n.get(k);
            if (v != null && !v.isNull() && !v.asText().isBlank()) {
                return v.asText();
            }
        }
        return "";
    }

    private static String mapSexo(String genero) {
        if (genero == null || genero.isBlank()) {
            return "";
        }
        char c = Character.toUpperCase(genero.trim().charAt(0));
        return c == 'M' ? "M" : c == 'F' ? "F" : "";
    }

    private static String soDigitos(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }
}
