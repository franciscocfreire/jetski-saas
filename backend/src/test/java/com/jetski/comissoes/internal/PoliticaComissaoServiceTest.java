package com.jetski.comissoes.internal;

import com.jetski.comissoes.domain.NivelPolitica;
import com.jetski.comissoes.domain.PoliticaComissao;
import com.jetski.comissoes.domain.TipoComissao;
import com.jetski.comissoes.internal.repository.PoliticaComissaoRepository;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PoliticaComissaoService
 *
 * Tests commission policy management:
 * - CRUD operations (create, update, toggle, list)
 * - Validation by nivel (VENDEDOR, MODELO, CAMPANHA, DURACAO)
 * - Validation by tipo (PERCENTUAL, VALOR_FIXO, ESCALONADO)
 * - Business rules (duration ranges, required fields)
 *
 * @author Jetski Team
 * @since 0.7.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PoliticaComissaoService - Commission Policy Management")
class PoliticaComissaoServiceTest {

    @Mock
    private PoliticaComissaoRepository politicaRepository;

    @InjectMocks
    private PoliticaComissaoService politicaService;

    private UUID tenantId;
    private UUID vendedorId;
    private UUID modeloId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        vendedorId = UUID.randomUUID();
        modeloId = UUID.randomUUID();
    }

    // ====================
    // CRUD Operations
    // ====================

    @Test
    @DisplayName("Should create valid VENDEDOR policy successfully")
    void shouldCreateVendedorPolicySuccessfully() {
        // Given: Valid VENDEDOR policy
        PoliticaComissao politica = createVendedorPolicy();
        when(politicaRepository.save(any(PoliticaComissao.class))).thenAnswer(i -> {
            PoliticaComissao saved = i.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        // When
        PoliticaComissao criada = politicaService.criar(tenantId, politica);

        // Then
        assertThat(criada).isNotNull();
        assertThat(criada.getTenantId()).isEqualTo(tenantId);
        assertThat(criada.getNivel()).isEqualTo(NivelPolitica.VENDEDOR);
        verify(politicaRepository).save(politica);
    }

    @Test
    @DisplayName("Should create valid MODELO policy successfully")
    void shouldCreateModeloPolicySuccessfully() {
        // Given: Valid MODELO policy
        PoliticaComissao politica = createModeloPolicy();
        when(politicaRepository.save(any(PoliticaComissao.class))).thenAnswer(i -> i.getArgument(0));

        // When
        PoliticaComissao criada = politicaService.criar(tenantId, politica);

        // Then
        assertThat(criada).isNotNull();
        assertThat(criada.getNivel()).isEqualTo(NivelPolitica.MODELO);
        assertThat(criada.getModeloId()).isEqualTo(modeloId);
        verify(politicaRepository).save(politica);
    }

    @Test
    @DisplayName("Should create valid CAMPANHA policy successfully")
    void shouldCreateCampanhaPolicySuccessfully() {
        // Given: Valid CAMPANHA policy
        PoliticaComissao politica = createCampanhaPolicy();
        when(politicaRepository.save(any(PoliticaComissao.class))).thenAnswer(i -> i.getArgument(0));

        // When
        PoliticaComissao criada = politicaService.criar(tenantId, politica);

        // Then
        assertThat(criada).isNotNull();
        assertThat(criada.getNivel()).isEqualTo(NivelPolitica.CAMPANHA);
        assertThat(criada.getCodigoCampanha()).isEqualTo("VERAO2025");
        verify(politicaRepository).save(politica);
    }

    @Test
    @DisplayName("Should create valid DURACAO policy successfully")
    void shouldCreateDuracaoPolicySuccessfully() {
        // Given: Valid DURACAO policy
        PoliticaComissao politica = createDuracaoPolicy();
        when(politicaRepository.save(any(PoliticaComissao.class))).thenAnswer(i -> i.getArgument(0));

        // When
        PoliticaComissao criada = politicaService.criar(tenantId, politica);

        // Then
        assertThat(criada).isNotNull();
        assertThat(criada.getNivel()).isEqualTo(NivelPolitica.DURACAO);
        assertThat(criada.getDuracaoMinMinutos()).isEqualTo(120);
        verify(politicaRepository).save(politica);
    }

    @Test
    @DisplayName("Should update existing policy successfully")
    void shouldUpdateExistingPolicy() {
        // Given: Existing policy
        UUID politicaId = UUID.randomUUID();
        PoliticaComissao politicaExistente = createVendedorPolicy();
        politicaExistente.setId(politicaId);
        politicaExistente.setNome("Antiga Política");
        politicaExistente.setPercentualComissao(new BigDecimal("10.00"));

        PoliticaComissao politicaAtualizada = PoliticaComissao.builder()
                .nome("Nova Política")
                .tipo(TipoComissao.PERCENTUAL)
                .percentualComissao(new BigDecimal("15.00"))
                .ativa(true)
                .build();

        when(politicaRepository.findById(politicaId)).thenReturn(Optional.of(politicaExistente));
        when(politicaRepository.save(any(PoliticaComissao.class))).thenAnswer(i -> i.getArgument(0));

        // When
        PoliticaComissao resultado = politicaService.atualizar(tenantId, politicaId, politicaAtualizada);

        // Then
        assertThat(resultado.getNome()).isEqualTo("Nova Política");
        assertThat(resultado.getPercentualComissao()).isEqualByComparingTo("15.00");
        verify(politicaRepository).save(politicaExistente);
    }

    @Test
    @DisplayName("Should toggle policy active status")
    void shouldTogglePolicyActiveStatus() {
        // Given: Active policy
        UUID politicaId = UUID.randomUUID();
        PoliticaComissao politica = createVendedorPolicy();
        politica.setId(politicaId);
        politica.setAtiva(true);

        when(politicaRepository.findById(politicaId)).thenReturn(Optional.of(politica));
        when(politicaRepository.save(any(PoliticaComissao.class))).thenAnswer(i -> i.getArgument(0));

        // When
        PoliticaComissao resultado = politicaService.toggleAtiva(tenantId, politicaId);

        // Then
        assertThat(resultado.getAtiva()).isFalse();
        verify(politicaRepository).save(politica);
    }

    @Test
    @DisplayName("Should find policy by ID successfully")
    void shouldFindPolicyById() {
        // Given
        UUID politicaId = UUID.randomUUID();
        PoliticaComissao politica = createVendedorPolicy();
        politica.setId(politicaId);
        politica.setTenantId(tenantId);

        when(politicaRepository.findById(politicaId)).thenReturn(Optional.of(politica));

        // When
        PoliticaComissao encontrada = politicaService.buscarPorId(tenantId, politicaId);

        // Then
        assertThat(encontrada).isNotNull();
        assertThat(encontrada.getId()).isEqualTo(politicaId);
        verify(politicaRepository).findById(politicaId);
    }

    @Test
    @DisplayName("Should throw NotFoundException when policy not found")
    void shouldThrowNotFoundExceptionWhenPolicyNotFound() {
        // Given
        UUID politicaId = UUID.randomUUID();
        when(politicaRepository.findById(politicaId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> politicaService.buscarPorId(tenantId, politicaId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Política de comissão não encontrada");
    }

    @Test
    @DisplayName("Should throw NotFoundException when policy belongs to different tenant")
    void shouldThrowNotFoundExceptionWhenDifferentTenant() {
        // Given
        UUID politicaId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        PoliticaComissao politica = createVendedorPolicy();
        politica.setId(politicaId);
        politica.setTenantId(otherTenantId);

        when(politicaRepository.findById(politicaId)).thenReturn(Optional.of(politica));

        // When/Then
        assertThatThrownBy(() -> politicaService.buscarPorId(tenantId, politicaId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("Should list active policies only")
    void shouldListActivePoliciesOnly() {
        // Given
        PoliticaComissao politica1 = createVendedorPolicy();
        politica1.setAtiva(true);
        PoliticaComissao politica2 = createModeloPolicy();
        politica2.setAtiva(true);

        when(politicaRepository.findByTenantIdAndAtivaOrderByNivelAsc(tenantId, true))
                .thenReturn(Arrays.asList(politica1, politica2));

        // When
        var ativas = politicaService.listarAtivas(tenantId);

        // Then
        assertThat(ativas).hasSize(2);
        assertThat(ativas).allMatch(PoliticaComissao::getAtiva);
        verify(politicaRepository).findByTenantIdAndAtivaOrderByNivelAsc(tenantId, true);
    }

    @Test
    @DisplayName("Should list all policies (active and inactive)")
    void shouldListAllPolicies() {
        // Given
        PoliticaComissao politica1 = createVendedorPolicy();
        politica1.setAtiva(true);
        PoliticaComissao politica2 = createModeloPolicy();
        politica2.setAtiva(false);

        when(politicaRepository.findByTenantIdAndAtivaOrderByNivelAsc(tenantId, null))
                .thenReturn(Arrays.asList(politica1, politica2));

        // When
        var todas = politicaService.listarTodas(tenantId);

        // Then
        assertThat(todas).hasSize(2);
        verify(politicaRepository).findByTenantIdAndAtivaOrderByNivelAsc(tenantId, null);
    }

    // ====================
    // Validation: Nivel-specific required fields
    // ====================

    @Test
    @DisplayName("Should fail when VENDEDOR policy missing vendedorId")
    void shouldFailWhenVendedorPolicyMissingVendedorId() {
        // Given: VENDEDOR policy without vendedorId
        PoliticaComissao politica = createVendedorPolicy();
        politica.setVendedorId(null);

        // When/Then
        assertThatThrownBy(() -> politicaService.criar(tenantId, politica))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("vendedorId é obrigatório para política de nível VENDEDOR");

        verify(politicaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should fail when MODELO policy missing modeloId")
    void shouldFailWhenModeloPolicyMissingModeloId() {
        // Given: MODELO policy without modeloId
        PoliticaComissao politica = createModeloPolicy();
        politica.setModeloId(null);

        // When/Then
        assertThatThrownBy(() -> politicaService.criar(tenantId, politica))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("modeloId é obrigatório para política de nível MODELO");

        verify(politicaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should fail when CAMPANHA policy missing codigoCampanha")
    void shouldFailWhenCampanhaPolicyMissingCodigo() {
        // Given: CAMPANHA policy without codigoCampanha
        PoliticaComissao politica = createCampanhaPolicy();
        politica.setCodigoCampanha(null);

        // When/Then
        assertThatThrownBy(() -> politicaService.criar(tenantId, politica))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("codigoCampanha é obrigatório para política de nível CAMPANHA");

        verify(politicaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should fail when CAMPANHA policy has blank codigoCampanha")
    void shouldFailWhenCampanhaPolicyHasBlankCodigo() {
        // Given: CAMPANHA policy with blank codigoCampanha
        PoliticaComissao politica = createCampanhaPolicy();
        politica.setCodigoCampanha("   ");

        // When/Then
        assertThatThrownBy(() -> politicaService.criar(tenantId, politica))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("codigoCampanha é obrigatório para política de nível CAMPANHA");

        verify(politicaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should fail when DURACAO policy missing duracaoMinMinutos")
    void shouldFailWhenDuracaoPolicyMissingDuracaoMin() {
        // Given: DURACAO policy without duracaoMinMinutos
        PoliticaComissao politica = createDuracaoPolicy();
        politica.setDuracaoMinMinutos(null);

        // When/Then
        assertThatThrownBy(() -> politicaService.criar(tenantId, politica))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("duracaoMinMinutos é obrigatório para política de nível DURACAO");

        verify(politicaRepository, never()).save(any());
    }

    // ====================
    // Validation: Tipo-specific required fields
    // ====================

    @Test
    @DisplayName("Should fail when PERCENTUAL policy missing percentualComissao")
    void shouldFailWhenPercentualPolicyMissingPercentual() {
        // Given: PERCENTUAL policy without percentualComissao
        PoliticaComissao politica = createVendedorPolicy();
        politica.setTipo(TipoComissao.PERCENTUAL);
        politica.setPercentualComissao(null);

        // When/Then
        assertThatThrownBy(() -> politicaService.criar(tenantId, politica))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("percentualComissao é obrigatório para tipo PERCENTUAL");

        verify(politicaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should fail when VALOR_FIXO policy missing valorFixo")
    void shouldFailWhenValorFixoPolicyMissingValor() {
        // Given: VALOR_FIXO policy without valorFixo
        PoliticaComissao politica = createVendedorPolicy();
        politica.setTipo(TipoComissao.VALOR_FIXO);
        politica.setValorFixo(null);

        // When/Then
        assertThatThrownBy(() -> politicaService.criar(tenantId, politica))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("valorFixo é obrigatório para tipo VALOR_FIXO");

        verify(politicaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should fail when ESCALONADO policy missing percentualComissao")
    void shouldFailWhenEscalonadoPolicyMissingPercentualComissao() {
        // Given: ESCALONADO policy without percentualComissao
        PoliticaComissao politica = createVendedorPolicy();
        politica.setTipo(TipoComissao.ESCALONADO);
        politica.setPercentualComissao(null);
        politica.setPercentualExtra(new BigDecimal("15.00"));
        politica.setDuracaoMinMinutos(120);

        // When/Then
        assertThatThrownBy(() -> politicaService.criar(tenantId, politica))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("percentualComissao, percentualExtra e duracaoMinMinutos são obrigatórios para tipo ESCALONADO");

        verify(politicaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should fail when ESCALONADO policy missing percentualExtra")
    void shouldFailWhenEscalonadoPolicyMissingPercentualExtra() {
        // Given: ESCALONADO policy without percentualExtra
        PoliticaComissao politica = createVendedorPolicy();
        politica.setTipo(TipoComissao.ESCALONADO);
        politica.setPercentualComissao(new BigDecimal("10.00"));
        politica.setPercentualExtra(null);
        politica.setDuracaoMinMinutos(120);

        // When/Then
        assertThatThrownBy(() -> politicaService.criar(tenantId, politica))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("percentualComissao, percentualExtra e duracaoMinMinutos são obrigatórios para tipo ESCALONADO");

        verify(politicaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should fail when ESCALONADO policy missing duracaoMinMinutos")
    void shouldFailWhenEscalonadoPolicyMissingDuracaoMin() {
        // Given: ESCALONADO policy without duracaoMinMinutos
        PoliticaComissao politica = createVendedorPolicy();
        politica.setTipo(TipoComissao.ESCALONADO);
        politica.setPercentualComissao(new BigDecimal("10.00"));
        politica.setPercentualExtra(new BigDecimal("15.00"));
        politica.setDuracaoMinMinutos(null);

        // When/Then
        assertThatThrownBy(() -> politicaService.criar(tenantId, politica))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("percentualComissao, percentualExtra e duracaoMinMinutos são obrigatórios para tipo ESCALONADO");

        verify(politicaRepository, never()).save(any());
    }

    // ====================
    // Validation: Duration range
    // ====================

    @Test
    @DisplayName("Should fail when duracaoMaxMinutos < duracaoMinMinutos")
    void shouldFailWhenDuracaoMaxLessThanMin() {
        // Given: Invalid duration range
        PoliticaComissao politica = createDuracaoPolicy();
        politica.setDuracaoMinMinutos(180);
        politica.setDuracaoMaxMinutos(120); // max < min

        // When/Then
        assertThatThrownBy(() -> politicaService.criar(tenantId, politica))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("duracaoMaxMinutos deve ser >= duracaoMinMinutos");

        verify(politicaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should accept when duracaoMaxMinutos == duracaoMinMinutos")
    void shouldAcceptWhenDuracaoMaxEqualsMin() {
        // Given: Valid duration range (equal)
        PoliticaComissao politica = createDuracaoPolicy();
        politica.setDuracaoMinMinutos(120);
        politica.setDuracaoMaxMinutos(120); // max == min (valid)

        when(politicaRepository.save(any(PoliticaComissao.class))).thenAnswer(i -> i.getArgument(0));

        // When/Then - Should not throw
        assertThatCode(() -> politicaService.criar(tenantId, politica))
                .doesNotThrowAnyException();

        verify(politicaRepository).save(politica);
    }

    @Test
    @DisplayName("Should accept when duracaoMaxMinutos > duracaoMinMinutos")
    void shouldAcceptWhenDuracaoMaxGreaterThanMin() {
        // Given: Valid duration range
        PoliticaComissao politica = createDuracaoPolicy();
        politica.setDuracaoMinMinutos(120);
        politica.setDuracaoMaxMinutos(240); // max > min (valid)

        when(politicaRepository.save(any(PoliticaComissao.class))).thenAnswer(i -> i.getArgument(0));

        // When/Then - Should not throw
        assertThatCode(() -> politicaService.criar(tenantId, politica))
                .doesNotThrowAnyException();

        verify(politicaRepository).save(politica);
    }

    // ====================
    // Helper Methods
    // ====================

    private PoliticaComissao createVendedorPolicy() {
        return PoliticaComissao.builder()
                .tenantId(tenantId)
                .nivel(NivelPolitica.VENDEDOR)
                .vendedorId(vendedorId)
                .tipo(TipoComissao.PERCENTUAL)
                .nome("Política Vendedor Padrão")
                .percentualComissao(new BigDecimal("10.00"))
                .ativa(true)
                .build();
    }

    private PoliticaComissao createModeloPolicy() {
        return PoliticaComissao.builder()
                .tenantId(tenantId)
                .nivel(NivelPolitica.MODELO)
                .modeloId(modeloId)
                .tipo(TipoComissao.PERCENTUAL)
                .nome("Política Modelo Premium")
                .percentualComissao(new BigDecimal("12.00"))
                .ativa(true)
                .build();
    }

    private PoliticaComissao createCampanhaPolicy() {
        return PoliticaComissao.builder()
                .tenantId(tenantId)
                .nivel(NivelPolitica.CAMPANHA)
                .codigoCampanha("VERAO2025")
                .tipo(TipoComissao.PERCENTUAL)
                .nome("Campanha Verão 2025")
                .percentualComissao(new BigDecimal("15.00"))
                .vigenciaInicio(Instant.parse("2025-01-01T00:00:00Z"))
                .vigenciaFim(Instant.parse("2025-03-31T23:59:59Z"))
                .ativa(true)
                .build();
    }

    private PoliticaComissao createDuracaoPolicy() {
        return PoliticaComissao.builder()
                .tenantId(tenantId)
                .nivel(NivelPolitica.DURACAO)
                .tipo(TipoComissao.PERCENTUAL)
                .nome("Política Locação 2h+")
                .percentualComissao(new BigDecimal("8.00"))
                .duracaoMinMinutos(120)
                .ativa(true)
                .build();
    }
}
