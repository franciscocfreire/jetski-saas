package br.com.jetski.keycloak.emailcode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeChallengeTest {

    // ---- classificação e-mail × CPF ----

    @Test
    void cpfComPontuacaoViraSoDigitos() {
        var id = CodeChallenge.classify("123.456.789-09");
        assertEquals(CodeChallenge.Kind.CPF, id.kind());
        assertEquals("12345678909", id.value());
    }

    @Test
    void cpfSoDigitos() {
        var id = CodeChallenge.classify(" 12345678909 ");
        assertEquals(CodeChallenge.Kind.CPF, id.kind());
        assertEquals("12345678909", id.value());
    }

    @Test
    void emailNormalizaTrimELowercase() {
        var id = CodeChallenge.classify("  Joao.Silva@Gmail.COM ");
        assertEquals(CodeChallenge.Kind.EMAIL, id.kind());
        assertEquals("joao.silva@gmail.com", id.value());
    }

    @Test
    void dezDigitosNaoEhCpf() {
        assertEquals(CodeChallenge.Kind.UNKNOWN, CodeChallenge.classify("1234567890").kind());
    }

    @Test
    void dozeDigitosNaoEhCpf() {
        assertEquals(CodeChallenge.Kind.UNKNOWN, CodeChallenge.classify("123456789012").kind());
    }

    @Test
    void vazioENuloSaoUnknownComValorVazio() {
        assertEquals("", CodeChallenge.classify(null).value());
        assertEquals("", CodeChallenge.classify("   ").value());
        assertEquals(CodeChallenge.Kind.UNKNOWN, CodeChallenge.classify(null).kind());
    }

    // ---- geração e verificação ----

    @Test
    void codigoTemSeisDigitos() {
        for (int i = 0; i < 200; i++) {
            String code = CodeChallenge.generateCode();
            assertTrue(code.matches("\\d{6}"), "gerou: " + code);
        }
    }

    @Test
    void verificaCodigoCorreto() {
        String code = "042137";
        assertTrue(CodeChallenge.verify(CodeChallenge.hash(code), "042137"));
    }

    @Test
    void verificaComTrimDoDigitado() {
        String hash = CodeChallenge.hash("123456");
        assertTrue(CodeChallenge.verify(hash, " 123456 "));
    }

    @Test
    void rejeitaCodigoErrado() {
        assertFalse(CodeChallenge.verify(CodeChallenge.hash("123456"), "654321"));
    }

    @Test
    void rejeitaQuandoNaoHaHash() {
        // conta inexistente: nada foi enviado, qualquer código falha
        assertFalse(CodeChallenge.verify(null, "123456"));
        assertFalse(CodeChallenge.verify("", "123456"));
    }

    @Test
    void rejeitaCodigoNulo() {
        assertFalse(CodeChallenge.verify(CodeChallenge.hash("123456"), null));
    }

    @Test
    void hashEhDeterministicoEDiferentePorCodigo() {
        assertEquals(CodeChallenge.hash("111111"), CodeChallenge.hash("111111"));
        assertNotEquals(CodeChallenge.hash("111111"), CodeChallenge.hash("111112"));
    }

    // ---- expiração ----

    @Test
    void expiraDepoisDoLimite() {
        long emitido = 1_000_000L;
        long expira = emitido + CodeChallenge.TTL_SECONDS;
        assertFalse(CodeChallenge.isExpired(expira, expira));       // no limite ainda vale
        assertTrue(CodeChallenge.isExpired(expira, expira + 1));    // 1s depois expira
    }
}
