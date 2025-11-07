package com.arth.solabot.core.general.cache.service;

import com.arth.solabot.core.bot.exception.ExternalServiceErrorException;
import com.arth.solabot.core.bot.exception.InternalServerErrorException;
import com.arth.solabot.core.bot.exception.ResourceNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * GalleryCacheService (已弃用，重构为本地增量同步)
 *
 * 说明：
 * - 该服务不在本地持久化画廊图片，依赖远端 API 提供图片及元数据
 * - 采用 Redis 缓存元数据：hash (gallery:{role}:pics) + zset (gallery:{role}:pids)
 * - 采用 per-role 分布式锁防止缓存击穿
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Deprecated
public class GalleryCacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    @Value("${app.parameter.cache.gallery-metadata-ttl}")
    private long ttl;  // HOURS
    private static final String GALLERY_GLOBAL_MARKER = "gallery:cache"; // 全量更新标志（带 TTL）
    private static final String GALLERY_LOCK_PREFIX = "gallery:lock:";   // per-role lock 前缀
    private static final String PID_ROLE_MAP_KEY = "gallery:pid2role";   // pid → role 映射表

//    @Value("${app.parameter.plugin.kan.metadata-api}")
//    private String metadataApi;
    private String metadataApi = "REMOVED";
//    @Value("${app.parameter.plugin.kan.pic-api-path}")
//    private String picApiPath;
    private  String picApiPath = "REMOVED";
//    @Value("${app.parameter.plugin.kan.auth-token}")
//    private String authToken;
    private String authToken = "REMOVED";

    // 剩余过期时间大于该值时，不更新
    private static final long REFRESH_THRESHOLD_SECONDS = 300L;  // 5 minutes

    // 锁持续时间
    private static final long LOCK_BASE_SECONDS = 30L;

    // 最大等待时间：当检测到 role 正在更新时，读取端等待这个时间（ms）
    private static final long READ_WAIT_MAX_MILLIS = 5000L; // 5s

    // 等待轮询间隔（ms）
    private static final long READ_WAIT_POLL_MILLIS = 100L;

    // role 权重表（用于支持全局随机）
    private final AtomicReference<RolesSnapshot> rolesSnapshot = new AtomicReference<>(null);

    public byte[] getPic(String role, String pid) {
        tryUpdateGalleryCache();

        String hashKey = "gallery:" + role + ":pics";
        Object picJson = redisTemplate.opsForHash().get(hashKey, pid);

        if (picJson == null) {
            // 如果没拿到缓存，短暂重试以等待可能正在更新的线程完成更新
            // 如果检测到 role 正在被更新，则等待直到锁释放并轮询数据（最大等待 READ_WAIT_MAX_MILLIS）
            waitForRoleDataIfUpdating(role, () -> redisTemplate.opsForHash().get(hashKey, pid));
            picJson = redisTemplate.opsForHash().get(hashKey, pid);

            if (picJson == null) {
                throw new ResourceNotFoundException("pic metadata not found for role=" + role + ", pid=" + pid);
            }
        }

        Map<String, Object> picMeta;
        try {
            picMeta = objectMapper.readValue(picJson.toString(), Map.class);
        } catch (JsonProcessingException e) {
            throw new InternalServerErrorException("failed to deserialize pic metadata for pid=" + pid);
        }

        Object pathObj = picMeta.get("path");
        if (pathObj == null) {
            throw new InternalServerErrorException("missing 'path' in metadata for pid=" + pid);
        }
        String path = pathObj.toString().trim();
        String url = picApiPath + path;

        try {
            byte[] resp = webClient.get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(),
                            r -> Mono.error(new ExternalServiceErrorException("failed to fetch image from remote API: " + url)))
                    .bodyToMono(byte[].class)
                    .block();

            if (resp == null) {
                throw new ExternalServiceErrorException("failed to fetch image from remote API: " + url);
            }
            return resp;
        } catch (Exception e) {
            log.error("[core.cache] error fetching image {}: {}", url, e.getMessage());
            throw new ExternalServiceErrorException("error fetching image from remote API: " + url);
        }
    }

    /**
     * 尝试更新整个画廊缓存（注意：远端 API 返回的是全量数据，但我们在本地以 role 为单位分别缓存/过期/加锁）
     * <p>
     * 锁策略：
     * - 先检查全局标志 GALLERY_GLOBAL_MARKER 的 TTL（如果存在且 >阈值，则不刷新）
     * - 拉取远端全量数据后，按 role 分别更新各自的 hash/zset，并在每个 role 上设置独立随机 TTL
     * - 每个 role 更新时使用 per-role 锁（带 token）并用 compare-and-delete 的方式释放锁，避免误删
     * - 更新完成后设置 GALLERY_GLOBAL_MARKER 的 TTL（作为全局短期标志，便于快速判断是否需要刷新）
     */
    public void tryUpdateGalleryCache() {
        try {
            // 过期时间大于阈值，不更新
            Long globalTtl = redisTemplate.getExpire(GALLERY_GLOBAL_MARKER);
            if (globalTtl != null && globalTtl > REFRESH_THRESHOLD_SECONDS) {
                return;
            }
        } catch (Exception e) {
            log.warn("failed to read global marker TTL, will attempt refresh: {}", e.getMessage());
            // continue
        }

        log.info("[core.cache] find gallery cache expired, try to update");

        Map<String, Map<String, Object>> galleries;
        try {
            galleries = webClient.get()
                    .uri(metadataApi)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(),
                            r -> Mono.error(new ExternalServiceErrorException("failed to fetch gallery metadata from remote API: " + metadataApi)))
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Map<String, Object>>>() {
                    })
                    .block();

            if (galleries == null || galleries.isEmpty()) {
                log.warn("[core.cache] remote gallery API returned empty body");
                return;
            }
        } catch (Exception e) {
            log.error("[core.cache] failed to fetch gallery metadata from remote API: {}", e.getMessage());
            return;
        }

        try {
            redisTemplate.delete(PID_ROLE_MAP_KEY);
        } catch (Exception e) {
            log.warn("[core.cache] failed to clear pid → role map before refresh: {}", e.getMessage());
        }

        Map<String, Integer> roleCounts = new HashMap<>(Math.max(16, galleries.size()));

        for (Map.Entry<String, Map<String, Object>> roleEntry : galleries.entrySet()) {
            String role = roleEntry.getKey();
            if (role == null) continue;
            Object picsObj = roleEntry.getValue().get("pics");
            if (!(picsObj instanceof List)) continue;
            List<Map<String, Object>> pics = (List<Map<String, Object>>) picsObj;

            String lockKey = GALLERY_LOCK_PREFIX + role;
            String lockToken = UUID.randomUUID().toString();
            long lockTtlSeconds = LOCK_BASE_SECONDS + ThreadLocalRandom.current().nextLong(10, 20);

            Boolean acquired = false;
            try {
                acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, lockToken, Duration.ofSeconds(lockTtlSeconds));
                if (!Boolean.TRUE.equals(acquired)) {
                    roleCounts.put(role, pics.size());
                    continue;
                }

                String hashKey = "gallery:" + role + ":pics";
                String sortedSetKey = "gallery:" + role + ":pids";
                try {
                    Set<String> oldKeys = new HashSet<>();
                    oldKeys.add(hashKey);
                    oldKeys.add(sortedSetKey);
                    redisTemplate.delete(oldKeys);
                } catch (Exception e) {
                    log.warn("[core.cache] failed to delete old keys for role {}: {}", role, e.getMessage());
                }

                final String finalHashKey = hashKey;
                final String finalSortedSetKey = sortedSetKey;

                redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                    StringRedisSerializer serializer = (StringRedisSerializer) redisTemplate.getStringSerializer();
                    for (Map<String, Object> pic : pics) {
                        Object pidObj = pic.get("pid");
                        if (pidObj == null) continue;
                        String pid = pidObj.toString();
                        try {
                            String picJson = objectMapper.writeValueAsString(pic);
                            connection.hSet(serializer.serialize(finalHashKey), serializer.serialize(pid),
                                    serializer.serialize(picJson));
                            connection.hSet(serializer.serialize(PID_ROLE_MAP_KEY),
                                    serializer.serialize(pid),
                                    serializer.serialize(role));
                        } catch (JsonProcessingException e) {
                            log.warn("[core.cache] serialize pic error for role={}, pid={}, cause={}", role, pid, e.getMessage());
                        }

                        try {
                            double score = Double.parseDouble(pid);
                            connection.zAdd(serializer.serialize(finalSortedSetKey),
                                    score,
                                    serializer.serialize(pid));
                        } catch (NumberFormatException nfe) {
                            connection.zAdd(serializer.serialize(finalSortedSetKey), 0.0, serializer.serialize(pid));
                        }
                    }
                    return null;
                });

                long extraSeconds = ThreadLocalRandom.current().nextLong(300, 1800); // 5~30 min
                long ttlSeconds = TimeUnit.HOURS.toSeconds(ttl) + extraSeconds;
                try {
                    redisTemplate.expire(finalHashKey, ttlSeconds, TimeUnit.SECONDS);
                    redisTemplate.expire(finalSortedSetKey, ttlSeconds, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.warn("[core.cache] failed to set TTL for role {} keys: {}", role, e.getMessage());
                }

                roleCounts.put(role, pics.size());

            } finally {
                if (Boolean.TRUE.equals(acquired)) {
                    try {
                        redisTemplate.execute((RedisCallback<Object>) connection -> {
                            byte[] key = redisTemplate.getStringSerializer().serialize(lockKey);
                            byte[] val = connection.get(key);
                            if (val != null) {
                                String current = redisTemplate.getStringSerializer().deserialize(val);
                                if (lockToken.equals(current)) {
                                    connection.del(key);
                                }
                            }
                            return null;
                        });
                    } catch (Exception e) {
                        log.warn("[core.cache] failed to release lock {} safely: {}", lockKey, e.getMessage());
                    }
                }
            }
        }

        try {
            long globalExtra = ThreadLocalRandom.current().nextLong(60, 600); // 1~10 min
            long globalTtlSeconds = TimeUnit.HOURS.toSeconds(ttl) + globalExtra;
            redisTemplate.opsForValue().set(GALLERY_GLOBAL_MARKER, String.valueOf(System.currentTimeMillis()));
            redisTemplate.expire(GALLERY_GLOBAL_MARKER, globalTtlSeconds, TimeUnit.SECONDS);
            redisTemplate.expire(PID_ROLE_MAP_KEY, globalTtlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[core.cache] failed to set global gallery marker TTL: {}", e.getMessage());
        }

        try {
            RolesSnapshot snapshot = RolesSnapshot.buildFromRoleCounts(roleCounts);
            rolesSnapshot.set(snapshot);
            log.debug("[core.cache] rolesSnapshot updated: roles={}, totalPics={}", snapshot.roles.size(), snapshot.total);
        } catch (Exception e) {
            log.warn("[core.cache] failed to build rolesSnapshot: {}", e.getMessage());
        }

        log.info("[core.cache] updated gallery cache successfully");
    }

    /**
     * 将指定 pid 的元数据 URL 返回（辅助方法内部使用）
     */
    public String getPicUrlByPid(String pid) {
        String role = (String) redisTemplate.opsForHash().get(PID_ROLE_MAP_KEY, pid);
        if (role == null) {
            throw new ResourceNotFoundException("role not found for pid: " + pid);
        }
        Map<String, Object> metadata = getPicMetadataByPid(role, pid);
        Object path = metadata.get("path");
        if (path == null) {
            throw new ExternalServiceErrorException("path not found for pid: " + pid);
        }
        return picApiPath + path;
    }

    public String getRandomPicUrl(String role) {
        Map<String, Object> metadata = getRandomPicMetadata(role);
        Object path = metadata.get("path");
        if (path == null) {
            throw new ExternalServiceErrorException("path not found in random pic metadata");
        }
        return picApiPath + path;
    }

    public Map<String, Object> getPicMetadataByPid(String role, String pid) {
        tryUpdateGalleryCache();

        String hashKey = "gallery:" + role + ":pics";
        Object picJson = redisTemplate.opsForHash().get(hashKey, pid);
        if (picJson == null) {
            // 等待正在更新的 role（若存在），直到数据可用或超时
            waitForRoleDataIfUpdating(role, () -> redisTemplate.opsForHash().get(hashKey, pid));
            picJson = redisTemplate.opsForHash().get(hashKey, pid);
            if (picJson == null) throw new ResourceNotFoundException(
                    "gallery pics not found for role=" + role + ", pid=" + pid);
        }

        try {
            return objectMapper.readValue(picJson.toString(), Map.class);
        } catch (JsonProcessingException e) {
            throw new InternalServerErrorException("failed to deserialize pic data");
        }
    }

    public Map<String, Object> getRandomPicMetadata(String role) {
        String pid = getRandomPid(role);
        return getPicMetadataByPid(role, pid);
    }

    public String getRandomPid(String role) {
        tryUpdateGalleryCache();

        String sortedSetKey = "gallery:" + role + ":pids";
        Long size = redisTemplate.opsForZSet().size(sortedSetKey);
        if (size == null || size == 0) {
            // 若 size==0，可能正处于更新中，等待锁释放并重试
            waitForRoleDataIfUpdating(role, () -> {
                Long s = redisTemplate.opsForZSet().size(sortedSetKey);
                return s == null ? null : String.valueOf(s);
            });
            size = redisTemplate.opsForZSet().size(sortedSetKey);
            if (size == null || size == 0) {
                throw new ResourceNotFoundException("Gallery not found for role: " + role);
            }
        }

        try {
            String maybe = redisTemplate.execute((RedisCallback<String>) connection -> {
                byte[] keyBytes = redisTemplate.getStringSerializer().serialize(sortedSetKey);
                if (keyBytes == null) return null;
                try {
                    byte[] member = connection.zRandMember(keyBytes);
                    if (member == null) return null;
                    return redisTemplate.getStringSerializer().deserialize(member);
                } catch (UnsupportedOperationException | AbstractMethodError e) {
                    return null;
                }
            });
            if (maybe != null) return maybe;
        } catch (Exception ignore) {
        }

        long idx = ThreadLocalRandom.current().nextLong(size);
        Set<String> result = redisTemplate.opsForZSet().range(sortedSetKey, idx, idx);
        if (result == null || result.isEmpty()) throw new ResourceNotFoundException("no pid found for role: " + role);
        return result.iterator().next();
    }

    /**
     * 从全局（所有角色）范围随机返回一张图片 URL（按权重=每角色图片数）。
     * <p>
     * 实现：读取内存 snapshot（rolesSnapshot），按权重随机选择角色，然后调用已有的按角色随机实现。
     */
    public String getRandomPicUrlGlobal() {
        tryUpdateGalleryCache();

        RolesSnapshot snapshot = rolesSnapshot.get();
        if (snapshot == null || snapshot.total <= 0 || snapshot.roles.isEmpty()) {
            forceRefreshCache();
            snapshot = rolesSnapshot.get();
            if (snapshot == null || snapshot.total <= 0 || snapshot.roles.isEmpty()) {
                throw new ResourceNotFoundException("global gallery is empty");
            }
        }

        int total = snapshot.total;
        int rnd = ThreadLocalRandom.current().nextInt(total);
        int idx = snapshot.pickRoleIndex(rnd);
        String chosenRole = snapshot.roles.get(idx);
        return getRandomPicUrl(chosenRole);
    }

    public List<Long> getPidList(String role) {
        tryUpdateGalleryCache();

        String sortedSetKey = "gallery:" + role + ":pids";
        Set<String> pids = redisTemplate.opsForZSet().range(sortedSetKey, 0, -1);
        if (pids == null || pids.isEmpty()) {
            // 可能正在更新，等待并重试
            waitForRoleDataIfUpdating(role, () -> {
                Set<String> s = redisTemplate.opsForZSet().range(sortedSetKey, 0, -1);
                return s == null ? null : String.join(",", s);
            });
            pids = redisTemplate.opsForZSet().range(sortedSetKey, 0, -1);
            if (pids == null || pids.isEmpty()) throw new ResourceNotFoundException("gallery role not found");
        }

        List<Long> list = new ArrayList<>(pids.size());
        for (String pidStr : pids) {
            if (pidStr == null) continue;
            try {
                list.add(Long.parseLong(pidStr));
            } catch (NumberFormatException e) {
                throw new InternalServerErrorException("invalid pid format stored in Redis: " + pidStr);
            }
        }
        return list;
    }

    public void forceClearCache() {
        try {
            log.info("[core.cache] force clearing all gallery caches");

            Set<String> keys = redisTemplate.keys("gallery:*");
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("[core.cache] cleared {} gallery cache keys", keys.size());
            } else {
                log.info("[core.cache] no gallery cache keys found to clear");
            }
        } catch (Exception e) {
            log.error("[core.cache] failed to clear gallery cache: {}", e.getMessage(), e);
            throw new InternalServerErrorException("failed to clear gallery cache");
        }
    }

    public void forceRefreshCache() {
        log.info("[core.cache] force refreshing gallery cache...");
        forceClearCache();
        tryUpdateGalleryCache();
    }

    /**
     * 如果检测到 role 正在更新（通过存在 per-role lock），则等待直到锁释放或者提供的 supplier
     * 返回非空值（表示数据已就绪），最多等待 READ_WAIT_MAX_MILLIS。
     * <p>
     * supplier 用来在轮询中尝试读取目标数据（返回 null 表示未就绪）。
     */
    private void waitForRoleDataIfUpdating(String role, DataSupplier supplier) {
        String lockKey = GALLERY_LOCK_PREFIX + role;
        try {
            // 如果没有锁存在，直接返回（避免不必要等待）
            Long lockTtl = redisTemplate.getExpire(lockKey);
            if (lockTtl == null || lockTtl < 0) {
                // 没有正在更新
                return;
            }

            long waited = 0L;
            while (waited < READ_WAIT_MAX_MILLIS) {
                // 如果数据就绪（supplier 返回非 null/非空字符串/非空集合），立即返回
                Object s = null;
                try {
                    s = supplier.get();
                } catch (Exception ignored) {
                }
                if (isNonEmptyResult(s)) {
                    return;
                }

                // 检查锁是否还存在；若锁已被释放，尝试再次读取并返回（下次循环会验证 supplier）
                Long ttl = redisTemplate.getExpire(lockKey);
                if (ttl == null || ttl < 0) {
                    // 锁已释放，下一次 supplier.get() 如果拿到数据就返回；但这里先短暂等待一次，让写入线程有机会完成写操作
                    try {
                        Thread.sleep(READ_WAIT_POLL_MILLIS);
                    } catch (InterruptedException ignored) {
                    }
                    Object s2 = null;
                    try {
                        s2 = supplier.get();
                    } catch (Exception ignored) {
                    }
                    if (isNonEmptyResult(s2)) return;
                    // 否则继续循环直到超时
                } else {
                    // 锁仍存在，短暂等待一段时间再轮询
                    try {
                        Thread.sleep(READ_WAIT_POLL_MILLIS);
                    } catch (InterruptedException ignored) {
                    }
                }
                waited += READ_WAIT_POLL_MILLIS;
            }
        } catch (Exception e) {
            log.warn("[core.cache] waitForRoleDataIfUpdating encountered exception for role {}: {}", role, e.getMessage());
        }
    }

    private boolean isNonEmptyResult(Object s) {
        if (s == null) return false;
        if (s instanceof String) {
            return !((String) s).isEmpty();
        }
        if (s instanceof Collection) {
            return !((Collection<?>) s).isEmpty();
        }
        if (s instanceof Map) {
            return !((Map<?, ?>) s).isEmpty();
        }
        return true;
    }

    @FunctionalInterface
    private interface DataSupplier {
        Object get();
    }

    /**
     * 角色权重快照类
     */
    private record RolesSnapshot(List<String> roles, int[] prefixSums, int total) {

        static RolesSnapshot buildFromRoleCounts(Map<String, Integer> roleCounts) {
            if (roleCounts == null || roleCounts.isEmpty()) {
                return new RolesSnapshot(Collections.emptyList(), new int[0], 0);
            }
            List<Map.Entry<String, Integer>> entries = new ArrayList<>(roleCounts.entrySet());
            entries.sort(Comparator.comparing(Map.Entry::getKey));
            List<String> roles = new ArrayList<>(entries.size());
            int[] prefix = new int[entries.size()];
            int sum = 0;
            for (int i = 0; i < entries.size(); i++) {
                Map.Entry<String, Integer> e = entries.get(i);
                roles.add(e.getKey());
                int cnt = Math.max(0, e.getValue() == null ? 0 : e.getValue());
                sum += cnt;
                prefix[i] = sum;
            }
            return new RolesSnapshot(Collections.unmodifiableList(roles), prefix, sum);
        }

        /**
         * 根据随机数 x (0 <= x < total) 二分查找角色索引
         */
        int pickRoleIndex(int x) {
            int l = 0, r = prefixSums.length - 1;
            while (l < r) {
                int m = (l + r) >>> 1;
                if (x < prefixSums[m]) {
                    r = m;
                } else {
                    l = m + 1;
                }
            }
            return l;
        }
    }
}
