package com.jetski.locacoes.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes unitários para converters de enums do domínio.
 *
 * Foco em cobertura de branches nos métodos de conversão:
 * - FotoTipoConverter
 * - JetskiStatusConverter
 * - LocacaoStatusConverter
 * - VendedorTipoConverter
 *
 * Cada converter possui branches para null checks e conversões válidas/inválidas.
 */
@DisplayName("Enum Converters Unit Tests")
class EnumConvertersTest {

    // ==================== FotoTipoConverter Tests ====================

    private final FotoTipoConverter fotoTipoConverter = new FotoTipoConverter();

    @Test
    @DisplayName("FotoTipoConverter: convertToDatabaseColumn should return enum name")
    void testFotoTipoConverter_ToDatabase_Valid() {
        // When
        String result = fotoTipoConverter.convertToDatabaseColumn(FotoTipo.CHECKIN_FRENTE);

        // Then
        assertThat(result).isEqualTo("CHECKIN_FRENTE");
    }

    @Test
    @DisplayName("FotoTipoConverter: convertToDatabaseColumn should return null when input is null")
    void testFotoTipoConverter_ToDatabase_Null() {
        // When
        String result = fotoTipoConverter.convertToDatabaseColumn(null);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("FotoTipoConverter: convertToEntityAttribute should return enum from lowercase")
    void testFotoTipoConverter_ToEntity_Valid() {
        // When
        FotoTipo result = fotoTipoConverter.convertToEntityAttribute("checkin_lateral_esq");

        // Then
        assertThat(result).isEqualTo(FotoTipo.CHECKIN_LATERAL_ESQ);
    }

    @Test
    @DisplayName("FotoTipoConverter: convertToEntityAttribute should return null when input is null")
    void testFotoTipoConverter_ToEntity_Null() {
        // When
        FotoTipo result = fotoTipoConverter.convertToEntityAttribute(null);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("FotoTipoConverter: convertToEntityAttribute should return null when input is empty")
    void testFotoTipoConverter_ToEntity_Empty() {
        // When
        FotoTipo result = fotoTipoConverter.convertToEntityAttribute("");

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("FotoTipoConverter: convertToEntityAttribute should throw exception for invalid value")
    void testFotoTipoConverter_ToEntity_Invalid() {
        // When/Then
        assertThatThrownBy(() -> fotoTipoConverter.convertToEntityAttribute("INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== JetskiStatusConverter Tests ====================

    private final JetskiStatusConverter jetskiStatusConverter = new JetskiStatusConverter();

    @Test
    @DisplayName("JetskiStatusConverter: convertToDatabaseColumn should return lowercase string")
    void testJetskiStatusConverter_ToDatabase_Valid() {
        // When
        String result = jetskiStatusConverter.convertToDatabaseColumn(JetskiStatus.DISPONIVEL);

        // Then
        assertThat(result).isEqualTo("disponivel");
    }

    @Test
    @DisplayName("JetskiStatusConverter: convertToDatabaseColumn should return null when input is null")
    void testJetskiStatusConverter_ToDatabase_Null() {
        // When
        String result = jetskiStatusConverter.convertToDatabaseColumn(null);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("JetskiStatusConverter: convertToEntityAttribute should return enum from lowercase")
    void testJetskiStatusConverter_ToEntity_Valid() {
        // When
        JetskiStatus result = jetskiStatusConverter.convertToEntityAttribute("locado");

        // Then
        assertThat(result).isEqualTo(JetskiStatus.LOCADO);
    }

    @Test
    @DisplayName("JetskiStatusConverter: convertToEntityAttribute should return null when input is null")
    void testJetskiStatusConverter_ToEntity_Null() {
        // When
        JetskiStatus result = jetskiStatusConverter.convertToEntityAttribute(null);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("JetskiStatusConverter: convertToEntityAttribute should return null when input is empty")
    void testJetskiStatusConverter_ToEntity_Empty() {
        // When
        JetskiStatus result = jetskiStatusConverter.convertToEntityAttribute("");

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("JetskiStatusConverter: convertToEntityAttribute should throw exception for invalid value")
    void testJetskiStatusConverter_ToEntity_Invalid() {
        // When/Then
        assertThatThrownBy(() -> jetskiStatusConverter.convertToEntityAttribute("INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== LocacaoStatusConverter Tests ====================

    private final LocacaoStatusConverter locacaoStatusConverter = new LocacaoStatusConverter();

    @Test
    @DisplayName("LocacaoStatusConverter: convertToDatabaseColumn should return enum name")
    void testLocacaoStatusConverter_ToDatabase_Valid() {
        // When
        String result = locacaoStatusConverter.convertToDatabaseColumn(LocacaoStatus.EM_CURSO);

        // Then
        assertThat(result).isEqualTo("EM_CURSO");
    }

    @Test
    @DisplayName("LocacaoStatusConverter: convertToDatabaseColumn should return null when input is null")
    void testLocacaoStatusConverter_ToDatabase_Null() {
        // When
        String result = locacaoStatusConverter.convertToDatabaseColumn(null);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("LocacaoStatusConverter: convertToEntityAttribute should return enum from lowercase")
    void testLocacaoStatusConverter_ToEntity_Valid() {
        // When
        LocacaoStatus result = locacaoStatusConverter.convertToEntityAttribute("finalizada");

        // Then
        assertThat(result).isEqualTo(LocacaoStatus.FINALIZADA);
    }

    @Test
    @DisplayName("LocacaoStatusConverter: convertToEntityAttribute should return null when input is null")
    void testLocacaoStatusConverter_ToEntity_Null() {
        // When
        LocacaoStatus result = locacaoStatusConverter.convertToEntityAttribute(null);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("LocacaoStatusConverter: convertToEntityAttribute should return null when input is empty")
    void testLocacaoStatusConverter_ToEntity_Empty() {
        // When
        LocacaoStatus result = locacaoStatusConverter.convertToEntityAttribute("");

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("LocacaoStatusConverter: convertToEntityAttribute should throw exception for invalid value")
    void testLocacaoStatusConverter_ToEntity_Invalid() {
        // When/Then
        assertThatThrownBy(() -> locacaoStatusConverter.convertToEntityAttribute("INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== VendedorTipoConverter Tests ====================

    private final VendedorTipoConverter vendedorTipoConverter = new VendedorTipoConverter();

    @Test
    @DisplayName("VendedorTipoConverter: convertToDatabaseColumn should return lowercase string")
    void testVendedorTipoConverter_ToDatabase_Valid() {
        // When
        String result = vendedorTipoConverter.convertToDatabaseColumn(VendedorTipo.INTERNO);

        // Then
        assertThat(result).isEqualTo("interno");
    }

    @Test
    @DisplayName("VendedorTipoConverter: convertToDatabaseColumn should return null when input is null")
    void testVendedorTipoConverter_ToDatabase_Null() {
        // When
        String result = vendedorTipoConverter.convertToDatabaseColumn(null);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("VendedorTipoConverter: convertToEntityAttribute should return enum from lowercase")
    void testVendedorTipoConverter_ToEntity_Valid() {
        // When
        VendedorTipo result = vendedorTipoConverter.convertToEntityAttribute("parceiro");

        // Then
        assertThat(result).isEqualTo(VendedorTipo.PARCEIRO);
    }

    @Test
    @DisplayName("VendedorTipoConverter: convertToEntityAttribute should return null when input is null")
    void testVendedorTipoConverter_ToEntity_Null() {
        // When
        VendedorTipo result = vendedorTipoConverter.convertToEntityAttribute(null);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("VendedorTipoConverter: convertToEntityAttribute should return null when input is empty")
    void testVendedorTipoConverter_ToEntity_Empty() {
        // When
        VendedorTipo result = vendedorTipoConverter.convertToEntityAttribute("");

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("VendedorTipoConverter: convertToEntityAttribute should throw exception for invalid value")
    void testVendedorTipoConverter_ToEntity_Invalid() {
        // When/Then
        assertThatThrownBy(() -> vendedorTipoConverter.convertToEntityAttribute("INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== Additional Coverage Tests ====================

    @Test
    @DisplayName("FotoTipoConverter: should handle all enum values")
    void testFotoTipoConverter_AllEnumValues() {
        // Test a few more enum values to ensure comprehensive coverage
        assertThat(fotoTipoConverter.convertToDatabaseColumn(FotoTipo.CHECKIN_LATERAL_DIR))
                .isEqualTo("CHECKIN_LATERAL_DIR");
        assertThat(fotoTipoConverter.convertToDatabaseColumn(FotoTipo.CHECKIN_HORIMETRO))
                .isEqualTo("CHECKIN_HORIMETRO");

        assertThat(fotoTipoConverter.convertToEntityAttribute("CHECKOUT_FRENTE"))
                .isEqualTo(FotoTipo.CHECKOUT_FRENTE);
    }

    @Test
    @DisplayName("JetskiStatusConverter: should handle all enum values")
    void testJetskiStatusConverter_AllEnumValues() {
        // Test all enum values
        assertThat(jetskiStatusConverter.convertToDatabaseColumn(JetskiStatus.MANUTENCAO))
                .isEqualTo("manutencao");

        assertThat(jetskiStatusConverter.convertToEntityAttribute("disponivel"))
                .isEqualTo(JetskiStatus.DISPONIVEL);
    }

    @Test
    @DisplayName("LocacaoStatusConverter: should handle all enum values")
    void testLocacaoStatusConverter_AllEnumValues() {
        // Test all enum values
        assertThat(locacaoStatusConverter.convertToDatabaseColumn(LocacaoStatus.CANCELADA))
                .isEqualTo("CANCELADA");

        assertThat(locacaoStatusConverter.convertToEntityAttribute("EM_CURSO"))
                .isEqualTo(LocacaoStatus.EM_CURSO);
    }

    @Test
    @DisplayName("VendedorTipoConverter: should handle all enum values")
    void testVendedorTipoConverter_AllEnumValues() {
        // Test all enum values
        assertThat(vendedorTipoConverter.convertToDatabaseColumn(VendedorTipo.PARCEIRO))
                .isEqualTo("parceiro");

        assertThat(vendedorTipoConverter.convertToEntityAttribute("interno"))
                .isEqualTo(VendedorTipo.INTERNO);
    }
}
