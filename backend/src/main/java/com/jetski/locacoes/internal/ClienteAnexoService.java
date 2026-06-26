package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.ClienteAnexo;
import com.jetski.locacoes.internal.repository.ClienteAnexoRepository;
import com.jetski.shared.security.TenantContext;
import com.jetski.shared.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Armazena/recupera os anexos do cliente (identidade, comprovante, selfie).
 * Recebe dataURL/base64 do balcão, grava no storage e mantém uma linha por tipo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClienteAnexoService {

    private final ClienteAnexoRepository repository;
    private final StorageService storageService;

    @Transactional
    public ClienteAnexo salvar(UUID clienteId, ClienteAnexo.Tipo tipo, String conteudoBase64) {
        UUID tenantId = TenantContext.getTenantId();
        Decoded d = decode(conteudoBase64);
        String key = String.format("%s/cliente/%s/anexo-%s.%s",
            tenantId, clienteId, tipo.name().toLowerCase(), ext(d.mime));
        storageService.putObject(key, d.bytes, d.mime);

        ClienteAnexo anexo = repository.findByClienteIdAndTipo(clienteId, tipo)
            .orElseGet(() -> ClienteAnexo.builder()
                .tenantId(tenantId).clienteId(clienteId).tipo(tipo).build());
        anexo.setS3Key(key);
        anexo.setContentType(d.mime);
        anexo.setUpdatedAt(Instant.now());
        return repository.save(anexo);
    }

    @Transactional(readOnly = true)
    public List<ClienteAnexo> listar(UUID clienteId) {
        return repository.findByClienteId(clienteId);
    }

    @Transactional(readOnly = true)
    public byte[] lerImagem(ClienteAnexo anexo) {
        return storageService.getObject(anexo.getS3Key());
    }

    private record Decoded(byte[] bytes, String mime) {}

    private Decoded decode(String conteudo) {
        String mime = "image/jpeg";
        String b64 = conteudo == null ? "" : conteudo.trim();
        if (b64.startsWith("data:")) {
            int comma = b64.indexOf(',');
            if (comma > 0) {
                String header = b64.substring(5, comma); // ex.: image/jpeg;base64
                mime = header.split(";")[0];
                b64 = b64.substring(comma + 1);
            }
        }
        byte[] bytes = Base64.getDecoder().decode(b64);
        return new Decoded(bytes, mime);
    }

    private static String ext(String mime) {
        return switch (mime) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "application/pdf" -> "pdf";
            default -> "jpg";
        };
    }
}
