package com.jetski.shared.config;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.usuarios.domain.Membro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Integration Test: Redis Cache Serialization
 *
 * Critical test that validates Redis cache serialization/deserialization.
 * This test prevents ClassCastException issues that occur when:
 * 1. First request caches data with incorrect serialization
 * 2. Second request retrieves corrupted data from cache
 *
 * **Why this test is important:**
 * - Detects cache serialization bugs BEFORE they reach production
 * - Validates UUID serialization (common failure point)
 * - Ensures complex objects are cached correctly
 * - Runs in CI pipeline to catch regressions
 *
 * **What it tests:**
 * 1. Simple types (UUID) - must serialize/deserialize cleanly
 * 2. Complex objects (domain entities) - must preserve type information
 * 3. Multiple cache retrievals - simulates real-world usage
 *
 * @author Jetski Team
 * @since 0.7.0
 */
@DisplayName("Redis Cache Serialization Integration Tests")
class RedisCacheSerializationTest extends AbstractIntegrationTest {

    // Redis container for testing
    private static final GenericContainer<?> redis;

    static {
        redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
        redis.start();
    }

    @DynamicPropertySource
    static void configureRedis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.cache.type", () -> "redis");  // Enable Redis cache
    }

    @Autowired
    private CacheManager cacheManager;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void setUp() {
        // Clear all caches before each test
        if (cacheManager != null) {
            cacheManager.getCacheNames().forEach(cacheName -> {
                Cache cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    cache.clear();
                }
            });
        }
    }

    @Test
    @DisplayName("Should serialize and deserialize UUID correctly (identity-provider-mapping cache)")
    void testUuidSerialization() {
        // Given: A UUID value to cache
        UUID originalUuid = UUID.fromString("44819be5-de45-4e2f-871e-6a1e28fe7e0a");
        String cacheKey = "keycloak:44819be5-de45-4e2f-871e-6a1e28fe7e0a";

        // Get the identity-provider-mapping cache
        Cache cache = cacheManager.getCache("identity-provider-mapping");
        assertThat(cache).isNotNull();

        // When: Put UUID in cache
        cache.put(cacheKey, originalUuid);

        // Then: Retrieve from cache multiple times (simulates real usage)
        // First retrieval
        UUID firstRetrieval = cache.get(cacheKey, UUID.class);
        assertThat(firstRetrieval)
            .isNotNull()
            .isEqualTo(originalUuid)
            .isInstanceOf(UUID.class);

        // Second retrieval (this is where ClassCastException occurred before fix)
        UUID secondRetrieval = cache.get(cacheKey, UUID.class);
        assertThat(secondRetrieval)
            .isNotNull()
            .isEqualTo(originalUuid)
            .isInstanceOf(UUID.class);

        // Third retrieval for good measure
        UUID thirdRetrieval = cache.get(cacheKey, UUID.class);
        assertThat(thirdRetrieval)
            .isNotNull()
            .isEqualTo(originalUuid)
            .isInstanceOf(UUID.class);
    }

    @Test
    @DisplayName("Should NOT throw ClassCastException when retrieving cached UUID")
    void testNoClassCastExceptionOnUuidRetrieval() {
        // Given: UUID cached in identity-provider-mapping
        UUID uuid = UUID.randomUUID();
        String cacheKey = "test-key";
        Cache cache = cacheManager.getCache("identity-provider-mapping");

        cache.put(cacheKey, uuid);

        // When/Then: Multiple retrievals should not throw ClassCastException
        assertThatCode(() -> {
            UUID first = cache.get(cacheKey, UUID.class);
            UUID second = cache.get(cacheKey, UUID.class);
            UUID third = cache.get(cacheKey, UUID.class);

            assertThat(first).isEqualTo(second).isEqualTo(third);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should reject null values (disableCachingNullValues = true)")
    void testNullValueHandling() {
        // Given: Cache key with null value
        String cacheKey = "null-test-key";
        Cache cache = cacheManager.getCache("identity-provider-mapping");

        // When/Then: Attempting to cache null should throw exception
        // This is expected behavior because we configured disableCachingNullValues()
        assertThatCode(() -> cache.put(cacheKey, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not allow 'null' values");

        // Verify key does not exist in cache
        assertThat(cache.get(cacheKey)).isNull();
    }

    @Test
    @DisplayName("Should cache and retrieve String values correctly")
    void testStringSerialization() {
        // Given: String value
        String originalValue = "test-string-value";
        String cacheKey = "string-key";
        Cache cache = cacheManager.getCache("identity-provider-mapping");

        // When: Cache string
        cache.put(cacheKey, originalValue);

        // Then: Retrieve correctly
        String retrieved = cache.get(cacheKey, String.class);
        assertThat(retrieved).isEqualTo(originalValue);
    }

    @Test
    @DisplayName("Should cache and retrieve Long values correctly")
    void testLongSerialization() {
        // Given: Long value
        Long originalValue = 12345L;
        String cacheKey = "long-key";
        Cache cache = cacheManager.getCache("identity-provider-mapping");

        // When: Cache long
        cache.put(cacheKey, originalValue);

        // Then: Retrieve correctly (multiple times)
        Long first = cache.get(cacheKey, Long.class);
        Long second = cache.get(cacheKey, Long.class);

        assertThat(first).isEqualTo(originalValue);
        assertThat(second).isEqualTo(originalValue);
    }

    @Test
    @DisplayName("Should serialize complex domain objects with default cache config")
    void testComplexObjectSerialization() {
        // Given: Complex domain object (Membro)
        UUID tenantId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        String[] papeis = {"ADMIN_TENANT", "GERENTE"};

        Membro originalMembro = Membro.builder()
            .tenantId(tenantId)
            .usuarioId(usuarioId)
            .papeis(papeis)
            .ativo(true)
            .build();

        String cacheKey = "membro:" + usuarioId;

        // Get default cache (not identity-provider-mapping)
        Cache cache = cacheManager.getCache("tenant-access");
        if (cache == null) {
            // Create cache on-demand if doesn't exist
            cache = cacheManager.getCache("test-complex-cache");
        }

        // When: Cache complex object
        cache.put(cacheKey, originalMembro);

        // Then: Retrieve and verify all fields
        Membro retrieved = cache.get(cacheKey, Membro.class);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getTenantId()).isEqualTo(tenantId);
        assertThat(retrieved.getUsuarioId()).isEqualTo(usuarioId);
        assertThat(retrieved.getPapeis()).containsExactly(papeis);
        assertThat(retrieved.getAtivo()).isTrue();

        // Second retrieval should also work
        Membro secondRetrieval = cache.get(cacheKey, Membro.class);
        assertThat(secondRetrieval).isNotNull();
        assertThat(secondRetrieval.getTenantId()).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("Should handle List of domain objects correctly")
    void testListSerialization() {
        // Given: List of domain objects (using ArrayList for Jackson compatibility)
        UUID tenant1 = UUID.randomUUID();
        UUID user1 = UUID.randomUUID();
        UUID tenant2 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        List<Membro> originalList = new java.util.ArrayList<>();
        originalList.add(Membro.builder()
            .tenantId(tenant1)
            .usuarioId(user1)
            .papeis(new String[]{"ADMIN"})
            .ativo(true)
            .build());
        originalList.add(Membro.builder()
            .tenantId(tenant2)
            .usuarioId(user2)
            .papeis(new String[]{"GERENTE"})
            .ativo(true)
            .build());

        String cacheKey = "membro-list";
        Cache cache = cacheManager.getCache("tenant-access");

        // When: Cache list
        cache.put(cacheKey, originalList);

        // Then: Retrieve list correctly
        Cache.ValueWrapper wrapper = cache.get(cacheKey);
        assertThat(wrapper).isNotNull();
        assertThat(wrapper.get()).isNotNull();

        // Verify it's a collection with 2 items
        @SuppressWarnings("unchecked")
        java.util.Collection<?> retrieved = (java.util.Collection<?>) wrapper.get();
        assertThat(retrieved).isNotNull().hasSize(2);
    }

    @Test
    @DisplayName("Should evict cache entries correctly")
    void testCacheEviction() {
        // Given: Cached UUID
        UUID uuid = UUID.randomUUID();
        String cacheKey = "evict-test";
        Cache cache = cacheManager.getCache("identity-provider-mapping");

        cache.put(cacheKey, uuid);
        assertThat(cache.get(cacheKey, UUID.class)).isNotNull();

        // When: Evict cache entry
        cache.evict(cacheKey);

        // Then: Entry no longer exists
        assertThat(cache.get(cacheKey, UUID.class)).isNull();
    }

    @Test
    @DisplayName("Should handle concurrent cache access without corruption")
    void testConcurrentCacheAccess() throws InterruptedException {
        // Given: Multiple threads accessing same cache
        UUID uuid = UUID.randomUUID();
        String cacheKey = "concurrent-test";
        Cache cache = cacheManager.getCache("identity-provider-mapping");

        cache.put(cacheKey, uuid);

        // When: Multiple threads read concurrently
        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    UUID retrieved = cache.get(cacheKey, UUID.class);
                    assertThat(retrieved).isEqualTo(uuid);
                }
            });
            threads[i].start();
        }

        // Wait for all threads
        for (Thread thread : threads) {
            thread.join();
        }

        // Then: Cache should still be consistent
        UUID finalRetrieval = cache.get(cacheKey, UUID.class);
        assertThat(finalRetrieval).isEqualTo(uuid);
    }
}
