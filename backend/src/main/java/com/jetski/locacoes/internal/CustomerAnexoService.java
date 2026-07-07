package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.ClienteAnexo;
import com.jetski.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Documentos do cliente pela página de PERFIL do portal — por LOJA vinculada
 * (anexos são tenant-scoped; o perfil itera os vínculos, como o contato).
 * Posse: vínculo do sub com a loja ({@link CustomerAccountService#exigirVinculo}).
 *
 * <p>O wizard EMA (por reserva) continua com o próprio caminho em
 * {@link CustomerEmaService} — ambos convergem no {@link ClienteAnexoService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerAnexoService {

    private final CustomerAccountService customerAccountService;
    private final ClienteAnexoService clienteAnexoService;

    @Transactional(readOnly = true)
    public List<String> listar(String sub, UUID tenantId) {
        var v = customerAccountService.exigirVinculo(sub, tenantId);
        return clienteAnexoService.listar(v.getClienteId()).stream()
            .map(a -> a.getTipo().name())
            .toList();
    }

    @Transactional
    public List<String> upload(String sub, UUID tenantId, String tipo, String conteudoBase64) {
        ClienteAnexo.Tipo t = ClienteAnexoService.parseTipoPortal(tipo);
        var v = customerAccountService.exigirVinculo(sub, tenantId);
        clienteAnexoService.salvar(v.getClienteId(), t, conteudoBase64, "PORTAL", sub);
        log.info("Anexo {} atualizado pelo cliente no perfil: cliente={}, tenant={}",
            t, v.getClienteId(), tenantId);
        return clienteAnexoService.listar(v.getClienteId()).stream()
            .map(a -> a.getTipo().name())
            .toList();
    }

    @Transactional(readOnly = true)
    public ClienteAnexoService.AnexoImagem ler(String sub, UUID tenantId, String tipo) {
        ClienteAnexo.Tipo t = ClienteAnexoService.parseTipoPortal(tipo);
        var v = customerAccountService.exigirVinculo(sub, tenantId);
        ClienteAnexo anexo = clienteAnexoService.buscar(v.getClienteId(), t)
            .orElseThrow(() -> new NotFoundException("Documento ainda não anexado"));
        return new ClienteAnexoService.AnexoImagem(
            clienteAnexoService.lerImagem(anexo), anexo.getContentType());
    }
}
