package com.jetski.locacoes.internal;

import com.jetski.locacoes.event.EnvioDocumentosSolicitadoEvent;
import com.jetski.shared.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envia os e-mails da emissão FORA do request (modo assíncrono).
 *
 * <p>Motivação: os dois envios custavam ~24 s dos ~25 s da emissão, dentro da
 * transação — o operador do balcão esperava com o cliente na frente e a conexão do
 * pool ficava presa. Aqui o POST responde em ~1,5 s e a tela acompanha o status.
 *
 * <p>Bean separado de propósito: {@code @Async} é aplicado por proxy e não funciona
 * em auto-invocação. Mesmo padrão de {@code GruAutoEmissaoService}.
 *
 * <p>{@code AFTER_COMMIT}: nada é enviado por uma emissão que reverteu (sem saldo de
 * crédito, a emissão inteira volta atrás — e seria péssimo o cliente receber o PDF de
 * uma emissão que não existe). O contrapeso é que, se a emissão rodar sem transação,
 * o evento é descartado e o documento fica PENDENTE; não usar
 * {@code fallbackExecution=true} para "resolver" isso — enviaria ANTES do commit.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentoEnvioListener {

    private final DocumentoEnvioService documentoEnvioService;

    @Async("envioDocumentosExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEnvioSolicitado(EnvioDocumentosSolicitadoEvent e) {
        try {
            // A thread assíncrona nasce sem contexto e o TenantAwareDataSource lê o
            // ThreadLocal a cada getConnection(): sem isto, a RLS derruba as queries.
            TenantContext.setTenantId(e.tenantId());
            // setUsuarioId(null) lança: o usuário nem sempre está no contexto
            // (o log de produção mostra "Usuario ID requested but not set").
            if (e.usuarioId() != null) {
                TenantContext.setUsuarioId(e.usuarioId());
            }

            DocumentoEnvioService.EnvioContexto ctx = documentoEnvioService.carregar(e.documentoId(), false);
            DocumentoEnvioService.ResultadoEnvio r = documentoEnvioService.despachar(ctx);
            documentoEnvioService.persistirStatus(e.documentoId(), r);

            log.info("Envio assíncrono concluído: docId={}, reserva={}, marinha={}, cliente={}",
                e.documentoId(), e.reservaId(), r.marinha(), r.cliente());
        } catch (Exception ex) {
            log.warn("Envio assíncrono falhou (docId={}): {}", e.documentoId(), ex.toString());
            try {
                documentoEnvioService.marcarFalha(e.documentoId(), ex);
            } catch (Exception ignored) {
                log.warn("Não foi possível registrar a falha do envio (docId={})", e.documentoId());
            }
        } finally {
            // Thread de pool é reutilizada: sem isto o tenant vaza para o próximo
            // envio — com RLS, isso é vazamento de dados entre lojas.
            TenantContext.clear();
        }
    }
}
