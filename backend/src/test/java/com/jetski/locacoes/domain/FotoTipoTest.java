package com.jetski.locacoes.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários para FotoTipo enum.
 *
 * Foco em cobertura de branches nos métodos de classificação:
 * - isCheckIn()
 * - isCheckOut()
 * - isIncidente()
 * - isManutencao()
 */
@DisplayName("FotoTipo Enum Unit Tests")
class FotoTipoTest {

    // ==================== isCheckIn() Tests ====================

    @Test
    @DisplayName("isCheckIn should return true for CHECKIN_FRENTE")
    void testIsCheckIn_True_Frente() {
        assertThat(FotoTipo.CHECKIN_FRENTE.isCheckIn()).isTrue();
    }

    @Test
    @DisplayName("isCheckIn should return true for CHECKIN_LATERAL_ESQ")
    void testIsCheckIn_True_LateralEsq() {
        assertThat(FotoTipo.CHECKIN_LATERAL_ESQ.isCheckIn()).isTrue();
    }

    @Test
    @DisplayName("isCheckIn should return true for CHECKIN_LATERAL_DIR")
    void testIsCheckIn_True_LateralDir() {
        assertThat(FotoTipo.CHECKIN_LATERAL_DIR.isCheckIn()).isTrue();
    }

    @Test
    @DisplayName("isCheckIn should return true for CHECKIN_HORIMETRO")
    void testIsCheckIn_True_Horimetro() {
        assertThat(FotoTipo.CHECKIN_HORIMETRO.isCheckIn()).isTrue();
    }

    @Test
    @DisplayName("isCheckIn should return false for CHECKOUT types")
    void testIsCheckIn_False_Checkout() {
        assertThat(FotoTipo.CHECKOUT_FRENTE.isCheckIn()).isFalse();
        assertThat(FotoTipo.CHECKOUT_LATERAL_ESQ.isCheckIn()).isFalse();
    }

    @Test
    @DisplayName("isCheckIn should return false for INCIDENTE")
    void testIsCheckIn_False_Incidente() {
        assertThat(FotoTipo.INCIDENTE.isCheckIn()).isFalse();
    }

    @Test
    @DisplayName("isCheckIn should return false for MANUTENCAO")
    void testIsCheckIn_False_Manutencao() {
        assertThat(FotoTipo.MANUTENCAO.isCheckIn()).isFalse();
    }

    // ==================== isCheckOut() Tests ====================

    @Test
    @DisplayName("isCheckOut should return true for CHECKOUT_FRENTE")
    void testIsCheckOut_True_Frente() {
        assertThat(FotoTipo.CHECKOUT_FRENTE.isCheckOut()).isTrue();
    }

    @Test
    @DisplayName("isCheckOut should return true for CHECKOUT_LATERAL_ESQ")
    void testIsCheckOut_True_LateralEsq() {
        assertThat(FotoTipo.CHECKOUT_LATERAL_ESQ.isCheckOut()).isTrue();
    }

    @Test
    @DisplayName("isCheckOut should return true for CHECKOUT_LATERAL_DIR")
    void testIsCheckOut_True_LateralDir() {
        assertThat(FotoTipo.CHECKOUT_LATERAL_DIR.isCheckOut()).isTrue();
    }

    @Test
    @DisplayName("isCheckOut should return true for CHECKOUT_HORIMETRO")
    void testIsCheckOut_True_Horimetro() {
        assertThat(FotoTipo.CHECKOUT_HORIMETRO.isCheckOut()).isTrue();
    }

    @Test
    @DisplayName("isCheckOut should return false for CHECKIN types")
    void testIsCheckOut_False_Checkin() {
        assertThat(FotoTipo.CHECKIN_FRENTE.isCheckOut()).isFalse();
        assertThat(FotoTipo.CHECKIN_LATERAL_ESQ.isCheckOut()).isFalse();
    }

    @Test
    @DisplayName("isCheckOut should return false for INCIDENTE")
    void testIsCheckOut_False_Incidente() {
        assertThat(FotoTipo.INCIDENTE.isCheckOut()).isFalse();
    }

    @Test
    @DisplayName("isCheckOut should return false for MANUTENCAO")
    void testIsCheckOut_False_Manutencao() {
        assertThat(FotoTipo.MANUTENCAO.isCheckOut()).isFalse();
    }

    // ==================== isIncidente() Tests ====================

    @Test
    @DisplayName("isIncidente should return true for INCIDENTE")
    void testIsIncidente_True() {
        assertThat(FotoTipo.INCIDENTE.isIncidente()).isTrue();
    }

    @Test
    @DisplayName("isIncidente should return false for CHECKIN types")
    void testIsIncidente_False_Checkin() {
        assertThat(FotoTipo.CHECKIN_FRENTE.isIncidente()).isFalse();
        assertThat(FotoTipo.CHECKIN_LATERAL_ESQ.isIncidente()).isFalse();
    }

    @Test
    @DisplayName("isIncidente should return false for CHECKOUT types")
    void testIsIncidente_False_Checkout() {
        assertThat(FotoTipo.CHECKOUT_FRENTE.isIncidente()).isFalse();
        assertThat(FotoTipo.CHECKOUT_LATERAL_ESQ.isIncidente()).isFalse();
    }

    @Test
    @DisplayName("isIncidente should return false for MANUTENCAO")
    void testIsIncidente_False_Manutencao() {
        assertThat(FotoTipo.MANUTENCAO.isIncidente()).isFalse();
    }

    // ==================== isManutencao() Tests ====================

    @Test
    @DisplayName("isManutencao should return true for MANUTENCAO")
    void testIsManutencao_True() {
        assertThat(FotoTipo.MANUTENCAO.isManutencao()).isTrue();
    }

    @Test
    @DisplayName("isManutencao should return false for CHECKIN types")
    void testIsManutencao_False_Checkin() {
        assertThat(FotoTipo.CHECKIN_FRENTE.isManutencao()).isFalse();
        assertThat(FotoTipo.CHECKIN_LATERAL_ESQ.isManutencao()).isFalse();
    }

    @Test
    @DisplayName("isManutencao should return false for CHECKOUT types")
    void testIsManutencao_False_Checkout() {
        assertThat(FotoTipo.CHECKOUT_FRENTE.isManutencao()).isFalse();
        assertThat(FotoTipo.CHECKOUT_LATERAL_ESQ.isManutencao()).isFalse();
    }

    @Test
    @DisplayName("isManutencao should return false for INCIDENTE")
    void testIsManutencao_False_Incidente() {
        assertThat(FotoTipo.INCIDENTE.isManutencao()).isFalse();
    }
}
