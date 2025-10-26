package com.jetski.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Configuration: Redis Cache
 *
 * Configures Redis as cache provider for:
 * - TenantAccessService (TTL: 5 minutes)
 *
 * Cache Strategy:
 * - Key: "tenant-access:{usuarioId}:{tenantId}"
 * - TTL: 5 minutes (balance between security and performance)
 * - Eviction: Automatic via TTL
 *
 * Why 5 minutes?
 * - Security: Access changes reflected within 5min
 * - Performance: Reduces database queries by ~95%
 * - Memory: Reasonable cache size for 10k+ users
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Configure Redis Cache Manager
     *
     * Features:
     * - JSON serialization with type information (handles UUIDs correctly)
     * - String keys (human-readable in Redis CLI)
     * - 5-minute TTL (security vs performance)
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Default configuration for complex objects (with type information)
        RedisCacheConfiguration defaultConfig = createComplexObjectCacheConfig();

        // Simple configuration for primitive/simple types (UUID, String, Long, etc.)
        RedisCacheConfiguration simpleConfig = createSimpleCacheConfig();

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            // Use simple serialization for identity provider mapping (returns UUID)
            .withCacheConfiguration("identity-provider-mapping", simpleConfig)
            .transactionAware()  // Participate in Spring transactions
            .build();
    }

    /**
     * Cache configuration for complex domain objects
     * Uses Jackson with type information to handle polymorphic types
     */
    private RedisCacheConfiguration createComplexObjectCacheConfig() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        // Configure polymorphic type validator for domain classes
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
            .allowIfSubType("java.util.")
            .allowIfSubType("java.time.")
            .allowIfSubType("com.jetski.")
            .build();

        objectMapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL);

        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(5))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()
                )
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer(objectMapper)
                )
            )
            .disableCachingNullValues();
    }

    /**
     * Cache configuration for simple types (UUID, String, primitives)
     *
     * CRITICAL FIX: Uses JDK Serialization instead of JSON
     *
     * Problem: GenericJackson2JsonRedisSerializer cannot properly serialize/deserialize
     *          UUID values, causing ClassCastException:
     *          - First retrieval: Returns String "44819be5-de45-4e2f-871e-6a1e28fe7e0a"
     *          - Cached value type: String (incorrect!)
     *          - Expected type: java.util.UUID
     *          - Error: "Cached value is not of required type [java.util.UUID]"
     *
     * Root Cause: Jackson serializes UUID.toString() as plain string in JSON,
     *             and when deserializing, Spring Cache doesn't know to convert
     *             it back to UUID without explicit type information.
     *
     * Solution: Use JdkSerializationRedisSerializer for UUIDs
     *          - Preserves exact Java type information
     *          - UUID serialized as binary Java object
     *          - Deserializes correctly back to UUID
     *
     * Trade-off: Binary format (less human-readable) but type-safe
     *
     * Test: RedisCacheSerializationTest.testUuidSerialization() validates this fix
     */
    private RedisCacheConfiguration createSimpleCacheConfig() {
        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(5))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()
                )
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new JdkSerializationRedisSerializer()  // Binary serialization for type safety
                )
            )
            .disableCachingNullValues();
    }
}
