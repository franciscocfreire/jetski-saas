package com.jetski.tenant.internal;

import com.jetski.shared.exception.ConflictException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.tenant.api.dto.PendingTenantDTO;
import com.jetski.tenant.api.dto.TenantStatusResult;
import com.jetski.tenant.domain.Tenant;
import com.jetski.tenant.domain.TenantStatus;
import com.jetski.tenant.domain.event.TenantStatusChangedEvent;
import com.jetski.tenant.internal.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Testes unitários do PlatformTenantService (ciclo de aprovação/bloqueio de tenants).
 *
 * @author Jetski Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlatformTenantService")
class PlatformTenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PlatformTenantService service;

    @BeforeEach
    void setUp() {
        // @InjectMocks usa o construtor (TenantRepository) e não cobre o campo
        // @PersistenceContext — injetamos o EntityManager manualmente.
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
    }

    private Tenant tenant(TenantStatus status) {
        return Tenant.builder()
            .id(UUID.randomUUID())
            .slug("acme")
            .razaoSocial("ACME Ltda")
            .cnpj("12345678000199")
            .status(status)
            .build();
    }

    @Test
    @DisplayName("approve: PENDENTE_APROVACAO → ATIVO + cria assinatura Trial")
    void approvePendingTenant() {
        Tenant t = tenant(TenantStatus.PENDENTE_APROVACAO);
        when(tenantRepository.findById(t.getId())).thenReturn(Optional.of(t));
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(1);

        TenantStatusResult result = service.approve(t.getId());

        assertThat(t.getStatus()).isEqualTo(TenantStatus.ATIVO);
        assertThat(result.status()).isEqualTo("ATIVO");
        verify(tenantRepository).save(t);
        verify(query).executeUpdate();  // assinatura Trial criada

        // evento de auditoria/notificação publicado — carrega razão social e slug
        // para os listeners de e-mail (que não leem o repositório do módulo tenant)
        ArgumentCaptor<TenantStatusChangedEvent> ev = ArgumentCaptor.forClass(TenantStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(ev.capture());
        assertThat(ev.getValue().acao()).isEqualTo("TENANT_APPROVED");
        assertThat(ev.getValue().toStatus()).isEqualTo("ATIVO");
        assertThat(ev.getValue().razaoSocial()).isEqualTo("ACME Ltda");
        assertThat(ev.getValue().slug()).isEqualTo("acme");
    }

    @Test
    @DisplayName("approve: tenant já ATIVO → ConflictException")
    void approveNonPendingTenant() {
        Tenant t = tenant(TenantStatus.ATIVO);
        when(tenantRepository.findById(t.getId())).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.approve(t.getId()))
            .isInstanceOf(ConflictException.class);
        verify(tenantRepository, never()).save(any());
        verifyNoInteractions(entityManager);
    }

    @Test
    @DisplayName("approve: tenant inexistente → NotFoundException")
    void approveMissingTenant() {
        UUID id = UUID.randomUUID();
        when(tenantRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(id))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("suspend: ATIVO → SUSPENSO")
    void suspendActiveTenant() {
        Tenant t = tenant(TenantStatus.ATIVO);
        when(tenantRepository.findById(t.getId())).thenReturn(Optional.of(t));

        TenantStatusResult result = service.suspend(t.getId(), "inadimplência");

        assertThat(t.getStatus()).isEqualTo(TenantStatus.SUSPENSO);
        assertThat(result.status()).isEqualTo("SUSPENSO");
        verify(tenantRepository).save(t);
    }

    @Test
    @DisplayName("suspend: tenant PENDENTE → ConflictException")
    void suspendPendingTenant() {
        Tenant t = tenant(TenantStatus.PENDENTE_APROVACAO);
        when(tenantRepository.findById(t.getId())).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.suspend(t.getId(), null))
            .isInstanceOf(ConflictException.class);
        verify(tenantRepository, never()).save(any());
    }

    @Test
    @DisplayName("reactivate: SUSPENSO → ATIVO")
    void reactivateSuspendedTenant() {
        Tenant t = tenant(TenantStatus.SUSPENSO);
        when(tenantRepository.findById(t.getId())).thenReturn(Optional.of(t));

        TenantStatusResult result = service.reactivate(t.getId());

        assertThat(t.getStatus()).isEqualTo(TenantStatus.ATIVO);
        assertThat(result.status()).isEqualTo("ATIVO");
        verify(tenantRepository).save(t);
    }

    @Test
    @DisplayName("reactivate: tenant ATIVO → ConflictException")
    void reactivateActiveTenant() {
        Tenant t = tenant(TenantStatus.ATIVO);
        when(tenantRepository.findById(t.getId())).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.reactivate(t.getId()))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("listPending: mapeia tenants PENDENTE_APROVACAO para DTO")
    void listPending() {
        Tenant t = tenant(TenantStatus.PENDENTE_APROVACAO);
        when(tenantRepository.findByStatusOrderByCreatedAtAsc(TenantStatus.PENDENTE_APROVACAO))
            .thenReturn(List.of(t));

        List<PendingTenantDTO> result = service.listPending();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).tenantId()).isEqualTo(t.getId());
        assertThat(result.get(0).slug()).isEqualTo("acme");
        assertThat(result.get(0).razaoSocial()).isEqualTo("ACME Ltda");
    }
}
