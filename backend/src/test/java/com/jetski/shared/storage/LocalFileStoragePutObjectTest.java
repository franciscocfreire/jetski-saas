package com.jetski.shared.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spike F0.2 — prova o {@code putObject} server-side (salvar bytes gerados, ex.: PDF).
 */
@DisplayName("LocalFileStorageService.putObject (F0.2)")
class LocalFileStoragePutObjectTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("salva os bytes no caminho da key e o arquivo passa a existir")
    void salvaBytes() throws Exception {
        LocalFileStorageService storage = new LocalFileStorageService();
        ReflectionTestUtils.setField(storage, "basePath", tempDir.toString());

        byte[] pdf = "%PDF-1.4 conteudo de teste".getBytes(StandardCharsets.US_ASCII);
        String key = "tenant-1/reserva-9/documento.pdf";

        storage.putObject(key, pdf, "application/pdf");

        assertThat(storage.fileExists(key)).isTrue();
        Path saved = Paths.get(tempDir.toString(), key);
        assertThat(Files.readAllBytes(saved)).isEqualTo(pdf);
    }
}
