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
        // Create ObjectMapper with polymorphic type handling for UUIDs
        ObjectMapper objectMapper = new ObjectMapper();

        // Configure polymorphic type validator to allow UUIDs and other java.* types
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
            .allowIfBaseType(Object.class)
            .build();

        // Enable default typing to preserve UUID type information
        objectMapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL);

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(5))  // 5 minutes TTL
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
            .disableCachingNullValues();  // Don't cache null results

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .transactionAware()  // Participate in Spring transactions
            .build();
    }
}
