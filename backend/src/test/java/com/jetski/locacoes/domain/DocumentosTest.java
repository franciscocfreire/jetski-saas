package com.jetski.locacoes.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A regra que a V066 introduziu: o documento é guardado canônico e formatado só
 * na exibição. Sem estes testes o defeito volta calado — ele não quebra nada,
 * só cria uma segunda ficha da mesma pessoa.
 */
class DocumentosTest {

    @Nested
    @DisplayName("normalizar")
    class Normalizar {

        @Test
        @DisplayName("as duas grafias do mesmo CPF colapsam no mesmo valor")
        void cpfFormatadoEcruConvergem() {
            assertThat(Documentos.normalizar(DocumentoTipo.CPF, "847.215.903-50"))
                .isEqualTo(Documentos.normalizar(DocumentoTipo.CPF, "84721590350"))
                .isEqualTo("84721590350");
        }

        @Test
        @DisplayName("passaporte vira alfanumérico maiúsculo")
        void passaporte() {
            assertThat(Documentos.normalizar(DocumentoTipo.PASSAPORTE, " ab 123-456 "))
                .isEqualTo("AB123456");
        }

        @Test
        @DisplayName("sem tipo, infere pelo formato")
        void semTipoInfere() {
            assertThat(Documentos.normalizar("847.215.903-50")).isEqualTo("84721590350");
            assertThat(Documentos.normalizar("AB-123456")).isEqualTo("AB123456");
        }

        @Test
        @DisplayName("vazio e lixo puro viram null")
        void vazio() {
            assertThat(Documentos.normalizar(null)).isNull();
            assertThat(Documentos.normalizar("   ")).isNull();
            assertThat(Documentos.normalizar(DocumentoTipo.CPF, "...---")).isNull();
        }
    }

    @Nested
    @DisplayName("inferirTipo")
    class Inferir {

        @Test
        @DisplayName("letra em qualquer posição só pode ser passaporte")
        void letraEhPassaporte() {
            assertThat(Documentos.inferirTipo("AB123456")).isEqualTo(DocumentoTipo.PASSAPORTE);
            assertThat(Documentos.inferirTipo("1234567890X")).isEqualTo(DocumentoTipo.PASSAPORTE);
        }

        @Test
        @DisplayName("11 dígitos é CPF, 14 é CNPJ")
        void porTamanho() {
            assertThat(Documentos.inferirTipo("847.215.903-50")).isEqualTo(DocumentoTipo.CPF);
            assertThat(Documentos.inferirTipo("41.586.720/0001-68")).isEqualTo(DocumentoTipo.CNPJ);
        }

        @Test
        @DisplayName("só dígitos fora de 11/14 é CPF malformado — null, nunca passaporte")
        void malformadoNaoViraPassaporte() {
            // O caso real que a V066 pegou: um CPF de 10 dígitos virava
            // PASSAPORTE, ligava `estrangeiro` e o ofício à Capitania sairia com
            // os anexos 5-B em inglês e o rótulo trocado.
            assertThat(Documentos.inferirTipo("1150452892")).isNull();
            assertThat(Documentos.inferirTipo("123")).isNull();
        }
    }

    @Nested
    @DisplayName("formatar")
    class Formatar {

        @Test
        @DisplayName("devolve a pontuação para o ofício, a partir do canônico")
        void pontua() {
            assertThat(Documentos.formatar(DocumentoTipo.CPF, "84721590350"))
                .isEqualTo("847.215.903-50");
            assertThat(Documentos.formatar(DocumentoTipo.CNPJ, "41586720000168"))
                .isEqualTo("41.586.720/0001-68");
        }

        @Test
        @DisplayName("passaporte e malformado saem como estão")
        void semFormatoConhecido() {
            assertThat(Documentos.formatar(DocumentoTipo.PASSAPORTE, "AB123456")).isEqualTo("AB123456");
            assertThat(Documentos.formatar(null, "1150452892")).isEqualTo("1150452892");
        }

        @Test
        @DisplayName("formatar(normalizar(x)) é idempotente")
        void idempotente() {
            String canonico = Documentos.normalizar("847.215.903-50");
            String exibicao = Documentos.formatar(DocumentoTipo.CPF, canonico);
            assertThat(Documentos.normalizar(DocumentoTipo.CPF, exibicao)).isEqualTo(canonico);
        }
    }

    @Nested
    @DisplayName("Cliente: invariante de gravação")
    class Invariante {

        @Test
        @DisplayName("@PrePersist normaliza, tipifica e liga estrangeiro no passaporte")
        void prePersist() {
            Cliente c = Cliente.builder().nome("John Smith").documento("ab-123456").build();
            c.normalizarDocumento();

            assertThat(c.getDocumento()).isEqualTo("AB123456");
            assertThat(c.getDocumentoTipo()).isEqualTo(DocumentoTipo.PASSAPORTE);
            assertThat(c.getEstrangeiro()).isTrue();
        }

        @Test
        @DisplayName("estrangeiro com CPF continua estrangeiro — a implicação é de mão única")
        void estrangeiroResidenteNaoPerdeAflag() {
            Cliente c = Cliente.builder()
                .nome("Marie Dubois").documento("847.215.903-50").estrangeiro(true).build();
            c.normalizarDocumento();

            assertThat(c.getDocumentoTipo()).isEqualTo(DocumentoTipo.CPF);
            // Continua precisando dos anexos 5-B em inglês.
            assertThat(c.getEstrangeiro()).isTrue();
        }

        @Test
        @DisplayName("tipo informado manda sobre a inferência")
        void tipoExplicitoVence() {
            Cliente c = Cliente.builder()
                .nome("X").documento("12345678901").documentoTipo(DocumentoTipo.PASSAPORTE).build();
            c.normalizarDocumento();

            assertThat(c.getDocumentoTipo()).isEqualTo(DocumentoTipo.PASSAPORTE);
            assertThat(c.getDocumento()).isEqualTo("12345678901");
            assertThat(c.getEstrangeiro()).isTrue();
        }
    }
}
