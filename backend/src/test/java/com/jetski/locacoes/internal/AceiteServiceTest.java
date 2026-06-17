package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.Reserva;
import com.jetski.locacoes.domain.ReservaAceite;
import com.jetski.locacoes.internal.repository.ReservaAceiteRepository;
import com.jetski.locacoes.internal.repository.ReservaRepository;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2.4 — aceite/assinatura: arquiva imagem (signature pad) + evidências; PAPEL sem imagem.
 */
@DisplayName("AceiteService (F2.4)")
class AceiteServiceTest {

    private final ReservaAceiteRepository repo = mock(ReservaAceiteRepository.class);
    private final ReservaRepository reservaRepo = mock(ReservaRepository.class);
    private final StorageService storage = mock(StorageService.class);
    private final AceiteService service = new AceiteService(repo, reservaRepo, storage);

    private final UUID tenant = UUID.randomUUID();
    private final UUID reservaId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(reservaRepo.findById(reservaId))
            .thenReturn(Optional.of(Reserva.builder().id(reservaId).tenantId(tenant).build()));
        when(repo.save(any(ReservaAceite.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("SIGNATURE_PAD com imagem → arquiva + hash + origem BALCAO")
    void signaturePadArquiva() {
        byte[] png = "imagem-png-fake".getBytes(StandardCharsets.UTF_8);

        ReservaAceite a = service.registrar(reservaId, ReservaAceite.Metodo.SIGNATURE_PAD, png, "1.2.3.4", "JUnit");

        verify(storage, times(1)).putObject(anyString(), eq(png), eq("image/png"));
        assertThat(a.getMetodo()).isEqualTo(ReservaAceite.Metodo.SIGNATURE_PAD);
        assertThat(a.getOrigem()).isEqualTo("BALCAO");
        assertThat(a.getAssinaturaS3Key()).isNotNull();
        assertThat(a.getHashSha256()).matches("^[0-9a-f]{64}$");
        assertThat(a.getIp()).isEqualTo("1.2.3.4");
        assertThat(a.getTenantId()).isEqualTo(tenant);
    }

    @Test
    @DisplayName("PAPEL sem imagem → não arquiva, s3Key nulo")
    void papelSemImagem() {
        ReservaAceite a = service.registrar(reservaId, ReservaAceite.Metodo.PAPEL, null, "1.2.3.4", "JUnit");

        verify(storage, never()).putObject(anyString(), any(), anyString());
        assertThat(a.getAssinaturaS3Key()).isNull();
        assertThat(a.getHashSha256()).isNull();
        assertThat(a.getMetodo()).isEqualTo(ReservaAceite.Metodo.PAPEL);
    }

    @Test
    @DisplayName("SIGNATURE_PAD sem imagem → erro")
    void signaturePadSemImagemFalha() {
        assertThatThrownBy(() ->
            service.registrar(reservaId, ReservaAceite.Metodo.SIGNATURE_PAD, null, "1.2.3.4", "JUnit"))
            .isInstanceOf(BusinessException.class);
    }
}
