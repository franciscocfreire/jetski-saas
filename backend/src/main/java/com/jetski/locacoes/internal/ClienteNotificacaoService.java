package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.ClienteNotificacao;
import com.jetski.locacoes.internal.repository.ClienteNotificacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Emissão de notificações in-app do cliente (best-effort: um problema aqui
 * NUNCA pode derrubar o fluxo de negócio que a originou).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClienteNotificacaoService {

    private final ClienteNotificacaoRepository repository;

    public void notificar(UUID tenantId, UUID clienteId, String tipo,
                          String titulo, String mensagem, String link) {
        if (tenantId == null || clienteId == null) {
            return; // reservas de balcão sem cliente vinculado não notificam
        }
        try {
            repository.save(ClienteNotificacao.builder()
                .tenantId(tenantId)
                .clienteId(clienteId)
                .tipo(tipo)
                .titulo(titulo)
                .mensagem(mensagem)
                .link(link)
                .build());
            log.debug("Notificação {} criada p/ cliente {}", tipo, clienteId);
        } catch (Exception e) {
            log.warn("Falha ao criar notificação {} p/ cliente {}: {}", tipo, clienteId, e.getMessage());
        }
    }
}
