package com.arth.solabot.core.infrastructure.cache.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

/**
 * 通用图片缓存服务接口：
 * 提供将图片数据缓存到Redis中的功能，支持单张和多张图片缓存
 */
public interface ImageCacheService {

    /**
     * 缓存静态图片方法，要求输入 BufferedImage，返回 Redis 缓存的 UUID
     *
     * @param img     要缓存的图片
     * @param imgType 图片类型（如：png, jpg, gif等）
     * @return 缓存的UUID标识
     * @throws IOException 图片处理异常
     */
    String cacheImage(BufferedImage img, String imgType) throws IOException;

    /**
     * 缓存静态图片方法，要求输入 BufferedImage，返回 Redis 缓存的 UUID，默认 PNG
     *
     * @param img 要缓存的图片
     * @return 缓存的UUID标识
     * @throws IOException 图片处理异常
     */
    String cacheImage(BufferedImage img) throws IOException;

    /**
     * 缓存静态图片方法，要求输入 byte[]，返回 Redis 缓存的 UUID
     * ** 所有缓存方法都以本方法为入口 **
     *
     * @param bytes   图片字节数组
     * @param imgType 图片类型
     * @return 缓存的UUID标识
     */
    String cacheImage(byte[] bytes, String imgType);

    /**
     * 缓存静态图片方法，要求输入 byte[]，返回 Redis 缓存的 UUID，默认 GIF
     *
     * @param bytes 图片字节数组
     * @return 缓存的UUID标识
     */
    String cacheImage(byte[] bytes);

    /**
     * 缓存多张静态图片的方法，要求输入 List<BufferedImage>，返回 Redis 缓存的 UUIDs
     *
     * @param imgs     图片列表
     * @param imgTypes 图片类型列表
     * @return UUID列表
     * @throws IOException 图片处理异常
     */
    List<String> cacheImage(List<BufferedImage> imgs, List<String> imgTypes) throws IOException;

    /**
     * 缓存多张静态图片的方法，要求输入 List<BufferedImage>，返回 Redis 缓存的 UUIDs，默认 PNG
     *
     * @param imgs 图片列表
     * @return UUID列表
     * @throws IOException 图片处理异常
     */
    List<String> cacheImage(List<BufferedImage> imgs) throws IOException;

    /**
     * 缓存多张静态图片的方法，要求输入 byte[][]，返回 Redis 缓存的 UUIDs
     *
     * @param bytesList 图片字节数组列表
     * @param imgTypes  图片类型列表
     * @return UUID列表
     */
    List<String> cacheImage(byte[][] bytesList, List<String> imgTypes);

    /**
     * 缓存多张静态图片的方法，要求输入 byte[][]，返回 Redis 缓存的 UUIDs，默认 GIF
     *
     * @param bytesList 图片字节数组列表
     * @return UUID列表
     */
    List<String> cacheImage(byte[][] bytesList);
}