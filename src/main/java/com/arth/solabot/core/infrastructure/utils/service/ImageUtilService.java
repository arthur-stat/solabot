package com.arth.solabot.core.infrastructure.utils.service;

import lombok.Getter;
import lombok.Setter;

import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * 图像处理工具类接口
 */
public interface ImageUtilService {

    /**
     * 安全解析IIOMetadataNode中的整数值
     *
     * @param n      元数据节点
     * @param attr   属性名
     * @param defVal 默认值
     * @return 解析后的整数值
     */
    int parseIntSafe(IIOMetadataNode n, String attr, int defVal);

    /**
     * 深拷贝BufferedImage
     *
     * @param src 源图像
     * @return 深拷贝后的图像
     */
    BufferedImage deepCopy(BufferedImage src);

    /**
     * 在IIOMetadataNode树中递归查找指定名称的节点
     *
     * @param root 根节点
     * @param name 要查找的节点名称
     * @return 找到的节点，未找到返回null
     */
    IIOMetadataNode findNode(IIOMetadataNode root, String name);

    /**
     * 从GIF元数据中读取循环次数
     *
     * @param metadata 元数据
     * @return 循环次数
     */
    int readLoopCount(IIOMetadata metadata);

    /**
     * 从GIF元数据中读取帧延迟时间（以厘秒为单位）
     *
     * @param metadata 元数据
     * @return 延迟时间（厘秒）
     */
    int readDelayCs(IIOMetadata metadata);

    /**
     * 将图像指定区域清除为透明
     *
     * @param img 图像
     * @param x   区域x坐标
     * @param y   区域y坐标
     * @param w   区域宽度
     * @param h   区域高度
     */
    void clearRectTransparent(BufferedImage img, int x, int y, int w, int h);
}