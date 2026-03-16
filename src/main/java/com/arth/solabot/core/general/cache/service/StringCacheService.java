package com.arth.solabot.core.general.cache.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.*;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class StringCacheService {

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 添加String键
     * @param key
     * @param value
     * @param ttl_min
     */
    public void setStringKey(String key, String value, int ttl_min) {
        redisTemplate.opsForValue().set(key, value, Duration.ofMinutes(ttl_min));
    }

    /**
     * 获取String键
     * @param key
     * @return
     */
    public String getStringKey(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public List<String> getList(String key){
        return redisTemplate.opsForList().range(key,0,-1);
    }

    public void listAppend(String key, String value) {
        redisTemplate.opsForList().rightPush(key, value);
    }

    public boolean listHas(String key, String value) {
        Long index = redisTemplate.opsForList().indexOf(key, value);
        return index != null;
    }

    public Set<String> getMatchedKeySet(String pattern) {
        return redisTemplate.execute((RedisCallback<? extends Set<String>>) connection -> {
            Set<String> keySet = new HashSet<>();

            ScanOptions options = KeyScanOptions
                    .scanOptions(DataType.STRING)
                    .match(pattern)
                    .count(300L)
                    .build();

            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    String key = redisTemplate.getStringSerializer().deserialize(cursor.next());
                    keySet.add(key);
                }
            }catch (Exception e){
                log.error(e.getMessage(),e);
            }

            return keySet;
        });
    }
}
