package com.arth.solabot.core.general.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Slf4j
@Configuration
@EnableCaching
public class RedisCacheConfig {

    private final RedisConnectionFactory redisConnectionFactory;

    public RedisCacheConfig(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Bean
    public RedisTemplate<String, byte[]> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, byte[]> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // 字符串序列化 key
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // 字节数组序列化 command
        template.setValueSerializer(RedisSerializer.byteArray());
        template.setHashValueSerializer(RedisSerializer.byteArray());

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 检查 Redis 连接
     */
    @EventListener(ApplicationReadyEvent.class)
    public void checkRedisConnection() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.ping();
            log.info("[redis] redis connection successful");
        } catch (Exception e) {
            log.error("[redis] redis connection failed!", e);
        }
    }
}