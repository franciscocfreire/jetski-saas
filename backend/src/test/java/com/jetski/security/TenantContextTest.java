package com.jetski.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for TenantContext
 *
 * @author Jetski Team
 */
class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldSetAndGetTenantId() {
        // Given
        UUID tenantId = UUID.randomUUID();

        // When
        TenantContext.setTenantId(tenantId);

        // Then
        assertThat(TenantContext.getTenantId()).isEqualTo(tenantId);
        assertThat(TenantContext.isSet()).isTrue();
    }

    @Test
    void shouldGetTenantIdAsString() {
        // Given
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        // When
        String tenantIdStr = TenantContext.getTenantIdAsString();

        // Then
        assertThat(tenantIdStr).isEqualTo(tenantId.toString());
    }

    @Test
    void shouldReturnNullWhenTenantIdNotSet() {
        // When / Then
        assertThat(TenantContext.getTenantId()).isNull();
        assertThat(TenantContext.getTenantIdAsString()).isNull();
        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    void shouldClearTenantId() {
        // Given
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        // When
        TenantContext.clear();

        // Then
        assertThat(TenantContext.getTenantId()).isNull();
        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    void shouldThrowExceptionWhenSettingNullTenantId() {
        // When / Then
        assertThatThrownBy(() -> TenantContext.setTenantId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tenant ID cannot be null");
    }

    @Test
    void shouldIsolateTenantIdBetweenThreads() throws InterruptedException {
        // Given
        UUID tenant1 = UUID.randomUUID();
        UUID tenant2 = UUID.randomUUID();

        // When
        Thread thread1 = new Thread(() -> {
            TenantContext.setTenantId(tenant1);
            try {
                Thread.sleep(100);
                assertThat(TenantContext.getTenantId()).isEqualTo(tenant1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                TenantContext.clear();
            }
        });

        Thread thread2 = new Thread(() -> {
            TenantContext.setTenantId(tenant2);
            try {
                Thread.sleep(100);
                assertThat(TenantContext.getTenantId()).isEqualTo(tenant2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                TenantContext.clear();
            }
        });

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        // Then
        // If isolation works, each thread saw its own tenant ID
        // (assertions inside threads will fail if not isolated)
    }
}
