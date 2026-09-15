package com.jetski.locacoes.internal;

import com.jetski.locacoes.api.dto.ModeloMidiaRequest;
import com.jetski.locacoes.api.dto.ModeloMidiaResponse;
import com.jetski.locacoes.domain.ModeloMidia;
import com.jetski.locacoes.internal.repository.ModeloRepository;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.security.TenantContext;
import com.jetski.shared.storage.StorageService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service: ModeloMidiaService
 *
 * Manages media items (images/videos) for jetski models.
 * Supports upload, ordering, and principal image selection.
 *
 * Business Rules:
 * - Each model can have multiple images and videos
 * - Only one image can be marked as principal per model
 * - Media items are ordered by 'ordem' field
 * - Tenant isolation via RLS
 * - Imagem enviada por upload (V074) fica no storage da empresa e é servida
 *   publicamente por /v1/public/midias/{tenant}/{midia}; a url guarda esse caminho
 *
 * @author Jetski Team
 * @since 0.9.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ModeloMidiaService {

    /** Limite do arquivo recebido (o backoffice já comprime para o preset MODELO antes). */
    public static final int MAX_BYTES = 5 * 1024 * 1024;

    /** Lado maior aceito — folga para PNG enviado sem passar pela compressão. */
    static final int MAX_DIMENSAO = 6000;

    /** Mídias (imagens + vídeos) por modelo. */
    public static final int MAX_MIDIAS_POR_MODELO = 20;

    /** Caminho público da imagem enviada (context path /api), ver PublicModeloMidiaController. */
    static final String URL_PUBLICA = "/api/v1/public/midias/";

    private final ModeloMidiaRepository midiaRepository;
    private final ModeloRepository modeloRepository;
    private final StorageService storageService;
    private final EntityManager entityManager;

    /** Conteúdo de uma imagem enviada, pronto para servir. */
    public record ArquivoMidia(byte[] conteudo, String contentType) {}

    /** Formato reconhecido pela assinatura do arquivo (não pelo nome nem pelo header). */
    record ImagemValida(String contentType, String extensao) {}

    /**
     * List all media for a model
     */
    @Transactional(readOnly = true)
    public List<ModeloMidiaResponse> listByModelo(UUID modeloId) {
        return midiaRepository.findByModeloIdOrderByOrdemAsc(modeloId).stream()
            .map(ModeloMidiaResponse::from)
            .toList();
    }

    /**
     * Get a specific media item by ID
     */
    @Transactional(readOnly = true)
    public ModeloMidiaResponse getById(UUID midiaId) {
        return midiaRepository.findById(midiaId)
            .map(ModeloMidiaResponse::from)
            .orElseThrow(() -> new EntityNotFoundException("Mídia não encontrada: " + midiaId));
    }

    /**
     * Add a new media item to a model
     */
    @Transactional
    public ModeloMidiaResponse addMidia(UUID modeloId, ModeloMidiaRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        // Verify modelo exists and belongs to tenant
        modeloRepository.findById(modeloId)
            .orElseThrow(() -> new EntityNotFoundException("Modelo não encontrado: " + modeloId));

        // If this is marked as principal, clear other principals first
        if (Boolean.TRUE.equals(request.principal())) {
            midiaRepository.clearPrincipalByModeloId(modeloId);
        }

        // Get next ordem if not specified
        Integer ordem = request.ordem() != null ? request.ordem() : midiaRepository.getNextOrdem(modeloId);

        ModeloMidia midia = ModeloMidia.builder()
            .tenantId(tenantId)
            .modeloId(modeloId)
            .tipo(request.tipo())
            .url(request.url())
            .thumbnailUrl(request.thumbnailUrl())
            .ordem(ordem)
            .principal(request.principal())
            .titulo(request.titulo())
            .build();

        ModeloMidia saved = midiaRepository.save(midia);

        // If this is the first media and is an image, make it principal
        if (midia.getTipo() == ModeloMidia.TipoMidia.IMAGEM && midiaRepository.countByModeloId(modeloId) == 1) {
            saved.setPrincipal(true);
            midiaRepository.save(saved);
        }

        // Update modelo.fotoReferenciaUrl with principal image
        updateModeloFotoReferencia(modeloId);

        log.info("Mídia adicionada: modeloId={}, midiaId={}, tipo={}", modeloId, saved.getId(), saved.getTipo());
        return ModeloMidiaResponse.from(saved);
    }

    /**
     * Upload de imagem do modelo: valida o arquivo, grava no storage da empresa e cria
     * a mídia com a url pública. Se a transação não completar, o objeto é apagado.
     *
     * @param conteudo  bytes do arquivo (o backoffice já comprimiu para o preset MODELO)
     * @param titulo    texto alternativo (opcional)
     * @param principal definir como imagem principal (a primeira imagem vira principal sozinha)
     */
    @Transactional
    public ModeloMidiaResponse uploadImagem(UUID modeloId, byte[] conteudo, String titulo, boolean principal) {
        UUID tenantId = TenantContext.getTenantId();

        modeloRepository.findById(modeloId)
            .orElseThrow(() -> new EntityNotFoundException("Modelo não encontrado: " + modeloId));

        if (midiaRepository.countByModeloId(modeloId) >= MAX_MIDIAS_POR_MODELO) {
            throw new BusinessException("O modelo já tem " + MAX_MIDIAS_POR_MODELO
                + " mídias. Apague alguma antes de enviar outra.");
        }

        ImagemValida imagem = validarImagem(conteudo);

        if (principal) {
            midiaRepository.clearPrincipalByModeloId(modeloId);
        }

        // A chave usa o id da mídia: salva primeiro (url provisória), depois grava o arquivo.
        ModeloMidia midia = midiaRepository.save(ModeloMidia.builder()
            .tenantId(tenantId)
            .modeloId(modeloId)
            .tipo(ModeloMidia.TipoMidia.IMAGEM)
            .url(URL_PUBLICA)
            .ordem(midiaRepository.getNextOrdem(modeloId))
            .principal(principal)
            .titulo(titulo != null && !titulo.isBlank() ? titulo.trim() : null)
            .build());

        String key = String.format("%s/modelos/%s/midias/%s.%s",
            tenantId, modeloId, midia.getId(), imagem.extensao());
        storageService.putObject(key, conteudo, imagem.contentType());
        aoReverter(() -> apagarDoStorage(key));

        midia.setStorageKey(key);
        midia.setTamanhoBytes(conteudo.length);
        midia.setUrl(URL_PUBLICA + tenantId + "/" + midia.getId());
        ModeloMidia saved = midiaRepository.save(midia);

        if (midiaRepository.findByModeloIdAndPrincipalTrue(modeloId).isEmpty()) {
            saved.setPrincipal(true);
            saved = midiaRepository.save(saved);
        }

        updateModeloFotoReferencia(modeloId);

        log.info("Imagem enviada: modeloId={}, midiaId={}, bytes={}, tipo={}",
            modeloId, saved.getId(), conteudo.length, imagem.contentType());
        return ModeloMidiaResponse.from(saved);
    }

    /**
     * Update an existing media item
     */
    @Transactional
    public ModeloMidiaResponse updateMidia(UUID midiaId, ModeloMidiaRequest request) {
        ModeloMidia midia = midiaRepository.findById(midiaId)
            .orElseThrow(() -> new EntityNotFoundException("Mídia não encontrada: " + midiaId));

        // Imagem enviada por upload: a url aponta para o arquivo guardado — trocar deixaria
        // o arquivo órfão no storage. Para trocar a foto, envia-se outra e apaga-se esta.
        if (midia.getStorageKey() != null
                && (!Objects.equals(midia.getUrl(), request.url())
                    || request.tipo() != ModeloMidia.TipoMidia.IMAGEM)) {
            throw new BusinessException("Imagem enviada por arquivo não pode ter a URL ou o tipo alterados. "
                + "Envie outra imagem e apague esta.");
        }

        // If setting as principal, clear others first
        if (Boolean.TRUE.equals(request.principal()) && !Boolean.TRUE.equals(midia.getPrincipal())) {
            midiaRepository.clearPrincipalByModeloId(midia.getModeloId());
        }

        midia.setTipo(request.tipo());
        midia.setUrl(request.url());
        midia.setThumbnailUrl(request.thumbnailUrl());
        if (request.ordem() != null) {
            midia.setOrdem(request.ordem());
        }
        midia.setPrincipal(request.principal());
        midia.setTitulo(request.titulo());

        ModeloMidia saved = midiaRepository.save(midia);

        // Update modelo.fotoReferenciaUrl
        updateModeloFotoReferencia(midia.getModeloId());

        log.info("Mídia atualizada: midiaId={}", midiaId);
        return ModeloMidiaResponse.from(saved);
    }

    /**
     * Delete a media item
     */
    @Transactional
    public void deleteMidia(UUID midiaId) {
        ModeloMidia midia = midiaRepository.findById(midiaId)
            .orElseThrow(() -> new EntityNotFoundException("Mídia não encontrada: " + midiaId));

        UUID modeloId = midia.getModeloId();
        boolean wasPrincipal = Boolean.TRUE.equals(midia.getPrincipal());
        String storageKey = midia.getStorageKey();

        midiaRepository.delete(midia);

        // Arquivo enviado sai do storage só depois do commit (rollback não perde a foto).
        if (storageKey != null) {
            aposCommit(() -> apagarDoStorage(storageKey));
        }

        // If deleted was principal, set next image as principal
        if (wasPrincipal) {
            midiaRepository.findByModeloIdAndTipoOrderByOrdemAsc(modeloId, ModeloMidia.TipoMidia.IMAGEM)
                .stream()
                .findFirst()
                .ifPresent(next -> {
                    next.setPrincipal(true);
                    midiaRepository.save(next);
                });
        }

        // Update modelo.fotoReferenciaUrl
        updateModeloFotoReferencia(modeloId);

        log.info("Mídia removida: midiaId={}, modeloId={}", midiaId, modeloId);
    }

    /**
     * Set a media item as principal (main image)
     */
    @Transactional
    public ModeloMidiaResponse setPrincipal(UUID midiaId) {
        ModeloMidia midia = midiaRepository.findById(midiaId)
            .orElseThrow(() -> new EntityNotFoundException("Mídia não encontrada: " + midiaId));

        if (midia.getTipo() != ModeloMidia.TipoMidia.IMAGEM) {
            throw new IllegalArgumentException("Apenas imagens podem ser definidas como principal");
        }

        // Clear all other principals for this model
        midiaRepository.clearPrincipalByModeloId(midia.getModeloId());

        // Set this one as principal
        midia.setPrincipal(true);
        ModeloMidia saved = midiaRepository.save(midia);

        // Update modelo.fotoReferenciaUrl
        updateModeloFotoReferencia(midia.getModeloId());

        log.info("Mídia definida como principal: midiaId={}", midiaId);
        return ModeloMidiaResponse.from(saved);
    }

    /**
     * Reorder media items for a model
     */
    @Transactional
    public List<ModeloMidiaResponse> reorder(UUID modeloId, List<UUID> orderedIds) {
        List<ModeloMidia> midias = midiaRepository.findByModeloIdOrderByOrdemAsc(modeloId);

        for (int i = 0; i < orderedIds.size(); i++) {
            UUID midiaId = orderedIds.get(i);
            final int newOrdem = i;
            midias.stream()
                .filter(m -> m.getId().equals(midiaId))
                .findFirst()
                .ifPresent(m -> m.setOrdem(newOrdem));
        }

        midiaRepository.saveAll(midias);

        log.info("Mídias reordenadas: modeloId={}, count={}", modeloId, orderedIds.size());
        return listByModelo(modeloId);
    }

    /**
     * Arquivo de uma imagem enviada, para o endpoint público (sem login, sem tenant na
     * sessão). O tenant vem do caminho: a RLS de modelo_midia só deixa ler a linha com
     * {@code app.tenant_id} fixado — local à transação, não vaza para outra requisição.
     * Serve também modelos fora do marketplace (a foto aparece no backoffice); o id da
     * mídia é um UUID, e fotos de modelo não são dado pessoal.
     */
    @Transactional(readOnly = true)
    public Optional<ArquivoMidia> lerArquivoPublico(UUID tenantId, UUID midiaId) {
        entityManager.createNativeQuery("SELECT set_config('app.tenant_id', ?1, true)")
            .setParameter(1, tenantId.toString())
            .getSingleResult();
        return midiaRepository.findById(midiaId)
            .filter(m -> tenantId.equals(m.getTenantId()) && m.getStorageKey() != null)
            .map(m -> new ArquivoMidia(storageService.getObject(m.getStorageKey()),
                contentTypeDaChave(m.getStorageKey())));
    }

    /**
     * Valida o arquivo pela assinatura (JPG, PNG, WebP), tamanho e dimensões. Lê só o
     * cabeçalho para as dimensões — decodificar a imagem inteira custaria centenas de MB.
     */
    static ImagemValida validarImagem(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException("Arquivo vazio.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new BusinessException("Imagem maior que 5 MB. Reduza a imagem e envie de novo.");
        }
        ImagemValida tipo = detectarTipo(bytes);
        if (tipo == null) {
            throw new BusinessException("Formato não suportado. Envie uma imagem JPG, PNG ou WebP.");
        }
        if (!"image/webp".equals(tipo.contentType())) {
            int[] dim = dimensoes(bytes);
            if (dim == null) {
                throw new BusinessException("Imagem corrompida ou ilegível.");
            }
            if (Math.max(dim[0], dim[1]) > MAX_DIMENSAO) {
                throw new BusinessException("Imagem com mais de " + MAX_DIMENSAO + " px no lado maior.");
            }
        }
        return tipo;
    }

    private static ImagemValida detectarTipo(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return new ImagemValida("image/jpeg", "jpg");
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return new ImagemValida("image/png", "png");
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return new ImagemValida("image/webp", "webp");
        }
        return null;
    }

    /** Largura e altura lidas do cabeçalho; null se o arquivo não for legível. */
    private static int[] dimensoes(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                return new int[] {reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static String contentTypeDaChave(String key) {
        if (key.endsWith(".png")) return "image/png";
        if (key.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private void apagarDoStorage(String key) {
        try {
            storageService.deleteFile(key);
        } catch (Exception e) {
            log.warn("Falha ao apagar imagem do storage (fica órfã): key={}, erro={}", key, e.getMessage());
        }
    }

    /** Executa depois do commit; sem transação ativa (ex.: teste unitário), na hora. */
    private static void aposCommit(Runnable acao) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    acao.run();
                }
            });
        } else {
            acao.run();
        }
    }

    /** Executa se a transação for revertida (desfaz o arquivo gravado antes da falha). */
    private static void aoReverter(Runnable acao) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        acao.run();
                    }
                }
            });
        }
    }

    /**
     * Update modelo.fotoReferenciaUrl with the principal image URL
     * This keeps backward compatibility with existing code
     */
    private void updateModeloFotoReferencia(UUID modeloId) {
        modeloRepository.findById(modeloId).ifPresent(modelo -> {
            String principalUrl = midiaRepository.findByModeloIdAndPrincipalTrue(modeloId)
                .map(ModeloMidia::getUrl)
                .orElse(null);

            modelo.setFotoReferenciaUrl(principalUrl);
            modeloRepository.save(modelo);
        });
    }
}
