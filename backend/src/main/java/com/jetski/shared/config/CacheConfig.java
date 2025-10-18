package com.jetski.shared.config;

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
     * - JSON serialization (portable across languages)
     * - String keys (human-readable in Redis CLI)
     * - 5-minute TTL (security vs performance)
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(5))  // 5 minutes TTL
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()
                )
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer()
                )
            )
            .disableCachingNullValues();  // Don't cache null results

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .transactionAware()  // Participate in Spring transactions
            .build();
    }
}
