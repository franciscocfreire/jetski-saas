package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.ClienteAnexo;
import com.jetski.locacoes.event.ClienteAnexoAtualizadoEvent;
import com.jetski.locacoes.internal.repository.ClienteAnexoRepository;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.security.TenantContext;
import com.jetski.shared.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Armazena/recupera os anexos do cliente (identidade, comprovante, selfie).
 * Recebe dataURL/base64 do balcão ou do portal, grava no storage e mantém
 * uma linha por tipo. Toda gravação publica {@link ClienteAnexoAtualizadoEvent}
 * (trilha LGPD — sem conteúdo, só o fato).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClienteAnexoService {

    /** Limite de upload (LGPD/minimização + proteção do storage). */
    private static final int MAX_BYTES = 8 * 1024 * 1024;

    /** Tipos que o CLIENTE pode gerenciar pelo portal (CHA é do balcão/GRU). */
    public static final Set<ClienteAnexo.Tipo> TIPOS_PORTAL = Set.of(
        ClienteAnexo.Tipo.IDENTIDADE,
        ClienteAnexo.Tipo.SELFIE,
        ClienteAnexo.Tipo.COMPROVANTE_RESIDENCIA);

    /** Imagem de um anexo (bytes + content type) para streaming autenticado. */
    public record AnexoImagem(byte[] bytes, String contentType) {}

    private final ClienteAnexoRepository repository;
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;

    /** Parse restrito aos tipos permitidos no portal. */
    public static ClienteAnexo.Tipo parseTipoPortal(String tipo) {
        ClienteAnexo.Tipo t;
        try {
            t = ClienteAnexo.Tipo.valueOf(tipo.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Tipo de documento inválido");
        }
        if (!TIPOS_PORTAL.contains(t)) {
            throw new BusinessException("Tipo de documento não permitido pelo portal");
        }
        return t;
    }

    @Transactional
    public ClienteAnexo salvar(UUID clienteId, ClienteAnexo.Tipo tipo, String conteudoBase64) {
        return salvar(clienteId, tipo, conteudoBase64, "BALCAO", null);
    }

    @Transactional
    public ClienteAnexo salvar(UUID clienteId, ClienteAnexo.Tipo tipo, String conteudoBase64,
                               String origem, String registradoPor) {
        UUID tenantId = TenantContext.getTenantId();
        Decoded d = decode(conteudoBase64);
        String key = String.format("%s/cliente/%s/anexo-%s.%s",
            tenantId, clienteId, tipo.name().toLowerCase(), ext(d.mime));

        ClienteAnexo anexo = repository.findByClienteIdAndTipo(clienteId, tipo)
            .orElseGet(() -> ClienteAnexo.builder()
                .tenantId(tenantId).clienteId(clienteId).tipo(tipo).build());
        String keyAntiga = anexo.getS3Key();

        storageService.putObject(key, d.bytes, d.mime);

        // Substituição com extensão diferente muda a key — remover o objeto
        // antigo (best-effort) para não acumular cópias de documento no storage.
        if (keyAntiga != null && !keyAntiga.equals(key)) {
            try {
                storageService.deleteFile(keyAntiga);
            } catch (Exception e) {
                log.warn("Anexo {} do cliente {}: falha ao remover key antiga {} ({})",
                    tipo, clienteId, keyAntiga, e.getMessage());
            }
        }

        anexo.setS3Key(key);
        anexo.setContentType(d.mime);
        anexo.setUpdatedAt(Instant.now());
        ClienteAnexo salvo = repository.save(anexo);

        eventPublisher.publishEvent(ClienteAnexoAtualizadoEvent.of(
            tenantId, clienteId, tipo.name(), origem, registradoPor));

        return salvo;
    }

    @Transactional(readOnly = true)
    public List<ClienteAnexo> listar(UUID clienteId) {
        return repository.findByClienteId(clienteId);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<ClienteAnexo> buscar(UUID clienteId, ClienteAnexo.Tipo tipo) {
        return repository.findByClienteIdAndTipo(clienteId, tipo);
    }

    /** Remove o anexo (registro + objeto no storage). No-op se não existir. */
    @Transactional
    public void deletar(UUID clienteId, ClienteAnexo.Tipo tipo) {
        repository.findByClienteIdAndTipo(clienteId, tipo).ifPresent(anexo -> {
            try {
                storageService.deleteFile(anexo.getS3Key());
            } catch (Exception e) {
                log.warn("Anexo {} do cliente {}: falha ao remover do storage ({}): {}",
                    tipo, clienteId, anexo.getS3Key(), e.getMessage());
            }
            repository.delete(anexo);
            log.info("Anexo {} do cliente {} removido", tipo, clienteId);
        });
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
        if (bytes.length > MAX_BYTES) {
            throw new BusinessException("Arquivo muito grande (máximo 8 MB)");
        }
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
