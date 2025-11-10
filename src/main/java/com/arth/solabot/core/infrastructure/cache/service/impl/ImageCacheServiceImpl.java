package com.arth.solabot.core.infrastructure.cache.service.impl;

import com.arth.solabot.core.infrastructure.cache.service.ImageCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImageCacheServiceImpl implements ImageCacheService {

    private final RedisTemplate<String, byte[]> redisTemplate;

    @Value("${app.parameter.cache.tmp-img.ttl}")
    private int ttl;
    @Value("${app.parameter.cache.tmp-img.max-size}")
    private int maxSize;

    /**
     * 缓存静态图片方法，要求输入 BufferedImage，返回 Redis 缓存的 UUID
     *
     * @param img
     * @param imgType
     * @return
     * @throws IOException
     */
    public String cacheImage(BufferedImage img, String imgType) throws IOException {
        String uuid = UUID.randomUUID().toString();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, imgType, baos);
        byte[] bytes = baos.toByteArray();
        redisTemplate.opsForValue().set("temp:image:" + imgType + ":" + uuid, bytes, Duration.ofMinutes(ttl));
        return uuid;
    }

    /**
     * 缓存静态图片方法，要求输入 BufferedImage，返回 Redis 缓存的 UUID，默认 PNG
     *
     * @param img
     * @return
     * @throws IOException
     */
    public String cacheImage(BufferedImage img) throws IOException {
        return cacheImage(img, "png");
    }

    /**
     * 缓存静态图片方法，要求输入 byte[]，返回 Redis 缓存的 UUID
     * ** 所有缓存方法都以本方法为入口 **
     *
     * @param bytes
     * @param imgType
     * @return
     */
    public String cacheImage(byte[] bytes, String imgType) {
        if (bytes.length > maxSize) {
            throw new IllegalArgumentException("Image size exceeds limit: " + bytes.length + " > " + maxSize);
        }
        String uuid = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set("temp:image:" + imgType + ":" + uuid, bytes, Duration.ofMinutes(ttl));
        return uuid;
    }

    /**
     * 缓存静态图片方法，要求输入 byte[]，返回 Redis 缓存的 UUID，默认 GIF
     *
     * @param bytes
     * @return
     */
    public String cacheImage(byte[] bytes) {
        return cacheImage(bytes, "gif");
    }

    /**
     * 缓存多张静态图片的方法，要求输入 List<BufferedImage>，返回 Redis 缓存的 UUIDs
     *
     * @param imgs
     * @param imgTypes
     * @return
     * @throws IOException
     */
    public List<String> cacheImage(List<BufferedImage> imgs, List<String> imgTypes) throws IOException {
        if (imgs.size() != imgTypes.size()) throw new IllegalArgumentException("size of imgs and types not matched");
        List<String> uuids = new ArrayList<>(imgs.size());
        for (int i = 0; i < imgs.size(); i++) {
            uuids.add(cacheImage(imgs.get(i), imgTypes.get(i)));
        }
        return uuids;
    }

    /**
     * 缓存多张静态图片的方法，要求输入 List<BufferedImage>，返回 Redis 缓存的 UUIDs，默认 PNG
     *
     * @param imgs
     * @return
     * @throws IOException
     */
    public List<String> cacheImage(List<BufferedImage> imgs) throws IOException {
        List<String> imgTypes = Collections.nCopies(imgs.size(), "png");
        return cacheImage(imgs, imgTypes);
    }

    /**
     * 缓存多张静态图片的方法，要求输入 byte[][]，返回 Redis 缓存的 UUIDs
     *
     * @param bytesList
     * @param imgTypes
     * @return
     */
    public List<String> cacheImage(byte[][] bytesList, List<String> imgTypes) {
        if (bytesList.length != imgTypes.size())
            throw new IllegalArgumentException("size of bytes list and types not matched");
        List<String> uuids = new ArrayList<>(bytesList.length);
        for (int i = 0; i < bytesList.length; i++) {
            uuids.add(cacheImage(bytesList[i], imgTypes.get(i)));
        }
        return uuids;
    }

    /**
     * 缓存多张静态图片的方法，要求输入 byte[][]，返回 Redis 缓存的 UUIDs，默认 GIF
     *
     * @param bytesList
     * @return
     */
    public List<String> cacheImage(byte[][] bytesList) {
        List<String> imgTypes = Collections.nCopies(bytesList.length, "gif");
        return cacheImage(bytesList, imgTypes);
    }
}
