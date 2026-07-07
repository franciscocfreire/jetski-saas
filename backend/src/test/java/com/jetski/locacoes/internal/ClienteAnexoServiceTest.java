package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.ClienteAnexo;
import com.jetski.locacoes.internal.repository.ClienteAnexoRepository;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.security.TenantContext;
import com.jetski.shared.storage.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Higiene LGPD do storage de anexos: substituição não acumula cópias
 * (key antiga é removida quando a extensão muda) e o limite de tamanho vale
 * para todos os fluxos (balcão e portal).
 */
@DisplayName("ClienteAnexoService — storage e limites")
class ClienteAnexoServiceTest {

    private final ClienteAnexoRepository repo = mock(ClienteAnexoRepository.class);
    private final StorageService storage = mock(StorageService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    private final ClienteAnexoService service = new ClienteAnexoService(repo, storage, events);

    private final UUID tenant = UUID.randomUUID();
    private final UUID clienteId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(tenant);
        when(repo.save(any(ClienteAnexo.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static String dataUrl(String mime, byte[] bytes) {
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    @DisplayName("re-upload com extensão diferente remove a key antiga do storage")
    void reUploadTrocaExtensaoDeletaKeyAntiga() {
        String keyAntiga = tenant + "/cliente/" + clienteId + "/anexo-selfie.jpg";
        when(repo.findByClienteIdAndTipo(clienteId, ClienteAnexo.Tipo.SELFIE))
            .thenReturn(Optional.of(ClienteAnexo.builder()
                .tenantId(tenant).clienteId(clienteId)
                .tipo(ClienteAnexo.Tipo.SELFIE).s3Key(keyAntiga).build()));

        service.salvar(clienteId, ClienteAnexo.Tipo.SELFIE, dataUrl("image/png", new byte[]{1, 2}));

        verify(storage).putObject(anyString(), any(), anyString());
        verify(storage).deleteFile(keyAntiga);
    }

    @Test
    @DisplayName("re-upload com a MESMA extensão não deleta (sobrescreve a própria key)")
    void reUploadMesmaExtensaoNaoDeleta() {
        String key = tenant + "/cliente/" + clienteId + "/anexo-selfie.jpg";
        when(repo.findByClienteIdAndTipo(clienteId, ClienteAnexo.Tipo.SELFIE))
            .thenReturn(Optional.of(ClienteAnexo.builder()
                .tenantId(tenant).clienteId(clienteId)
                .tipo(ClienteAnexo.Tipo.SELFIE).s3Key(key).build()));

        service.salvar(clienteId, ClienteAnexo.Tipo.SELFIE, dataUrl("image/jpeg", new byte[]{1, 2}));

        verify(storage, never()).deleteFile(anyString());
    }

    @Test
    @DisplayName("falha ao deletar a key antiga não interrompe a substituição")
    void falhaNoDeleteNaoPropaga() {
        String keyAntiga = tenant + "/cliente/" + clienteId + "/anexo-selfie.jpg";
        when(repo.findByClienteIdAndTipo(clienteId, ClienteAnexo.Tipo.SELFIE))
            .thenReturn(Optional.of(ClienteAnexo.builder()
                .tenantId(tenant).clienteId(clienteId)
                .tipo(ClienteAnexo.Tipo.SELFIE).s3Key(keyAntiga).build()));
        doThrow(new RuntimeException("minio fora")).when(storage).deleteFile(keyAntiga);

        assertThatCode(() ->
            service.salvar(clienteId, ClienteAnexo.Tipo.SELFIE, dataUrl("image/png", new byte[]{1})))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("upload acima de 8 MB é rejeitado")
    void uploadGrandeRejeitado() {
        when(repo.findByClienteIdAndTipo(clienteId, ClienteAnexo.Tipo.IDENTIDADE))
            .thenReturn(Optional.empty());
        byte[] grande = new byte[8 * 1024 * 1024 + 1];

        assertThatThrownBy(() ->
            service.salvar(clienteId, ClienteAnexo.Tipo.IDENTIDADE, dataUrl("image/jpeg", grande)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("8 MB");
        verify(storage, never()).putObject(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("parseTipoPortal aceita os 3 tipos do portal e recusa CHA")
    void parseTipoPortal() {
        assertThat(ClienteAnexoService.parseTipoPortal("selfie"))
            .isEqualTo(ClienteAnexo.Tipo.SELFIE);
        assertThatThrownBy(() -> ClienteAnexoService.parseTipoPortal("CHA"))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ClienteAnexoService.parseTipoPortal("XPTO"))
            .isInstanceOf(BusinessException.class);
    }
}
