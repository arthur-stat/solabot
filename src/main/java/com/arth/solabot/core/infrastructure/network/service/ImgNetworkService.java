package com.arth.solabot.core.infrastructure.network.service;

import com.arth.solabot.core.bot.dto.ParsedPayloadDTO;
import lombok.Getter;
import lombok.Setter;

import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 插件 Img 的网络服务接口
 */
public interface ImgNetworkService {

    /**
     * 从 url 下载一张静态图片，返回 BufferedImage
     * 针对动态图片，本方法只能获取首帧
     *
     * @param url 图片URL
     * @return BufferedImage对象，下载失败返回null
     */
    BufferedImage getBufferedImg(String url);

    /**
     * 从输入流读取一张静态图片，返回 BufferedImage
     * 注意，本方法不会主动关闭传入的 InputStream
     * 解析逻辑与 getBufferedImg(String) 保持一致
     *
     * @param inputStream 输入流
     * @return BufferedImage对象，解析失败返回null
     */
    BufferedImage getBufferedImg(InputStream inputStream);

    /**
     * 从 url 下载多张静态图片，返回 List<BufferedImage>
     * 针对动态图片，本方法只能获取首帧
     *
     * @param urls 图片URL列表
     * @return BufferedImage列表
     */
    List<BufferedImage> getBufferedImg(List<String> urls);

    /**
     * 从多输入流读取多张静态图片，返回 List<BufferedImage>
     * 注意，本方法不会主动关闭传入的 InputStream
     *
     * @param inputs 输入流列表
     * @return BufferedImage列表
     */
    List<BufferedImage> getBufferedImgFromStreams(List<InputStream> inputs);

    /**
     * 从 url 流式分块下载一张图片，返回二进制数据 byte[]
     *
     * @param url 图片URL
     * @return 图片二进制数据，下载失败返回null
     */
    byte[] getBytes(String url);

    /**
     * 从输入流流式分块读取全部二进制数据，返回 byte[]
     * 注意，本方法不会主动关闭传入的 InputStream
     *
     * @param inputStream 输入流
     * @return 二进制数据，读取失败返回null
     */
    byte[] getBytes(InputStream inputStream);

    /**
     * 从 url 下载多张图片，返回二进制数据 byte[][]
     *
     * @param urls 图片URL列表
     * @return 二进制数据数组
     */
    byte[][] getBytes(List<String> urls);

    /**
     * 从多个输入流读取多份二进制数据，返回 byte[][]。
     * 注意，本方法不会主动关闭传入的 InputStream
     *
     * @param inputs 输入流列表
     * @return 二进制数据数组
     */
    byte[][] getBytesFromStreams(List<InputStream> inputs);

    /**
     * 从 url 下载一份 GIF 的二进制流并将流解析为可逐帧操作的数据结构
     *
     * @param url GIF图片URL
     * @return GifData对象
     * @throws IOException 当下载或解析失败时抛出
     */
    GifData getGifFlattened(String url) throws IOException;

    /**
     * 从输入流读取一份 GIF 的二进制流并将流解析为可逐帧操作的数据结构
     * 解析逻辑与 getGifFlattened(String) 保持完全一致（仅读取数据来源不同）
     * 注意，本方法不会主动关闭传入的 InputStream
     *
     * @param inputStream 输入流
     * @return GifData对象
     * @throws IOException 当解析失败时抛出
     */
    GifData getGifFlattened(InputStream inputStream) throws IOException;

    /**
     * 从 url 下载多份 GIF 的二进制流并将流解析为可逐帧操作的数据结构
     *
     * @param urls GIF图片URL列表
     * @return GifData列表
     * @throws IOException 当下载或解析失败时抛出
     */
    List<GifData> getGifFlattened(List<String> urls) throws IOException;

    /**
     * 从多个输入流读取并解析多份 GIF，返回 List<GifData>
     *
     * @param inputs 输入流列表
     * @return GifData列表
     * @throws IOException 当解析失败时抛出
     */
    List<GifData> getGifFlattenedFromStreams(List<InputStream> inputs) throws IOException;

    /**
     * 从 OneBot v11 报文中提取图片媒体资源的 url
     *
     * @param payload     解析后的载荷DTO
     * @param printPrompt 是否打印提示信息
     * @return 图片URL列表
     */
    List<String> extractImgUrls(ParsedPayloadDTO payload, boolean printPrompt);

    /**
     * 从 OneBot v11 报文中提取图片媒体资源的 url（不打印提示信息）
     *
     * @param payload 解析后的载荷DTO
     * @return 图片URL列表
     */
    List<String> extractImgUrls(ParsedPayloadDTO payload);

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

    /**
     * 打开一个 url 并返回可读的 InputStream，stream.close() 会在关闭时断开底层连接
     *
     * @param url 资源URL
     * @return 输入流
     * @throws IOException 当打开连接失败时抛出
     */
    InputStream openUrlInputStream(String url) throws IOException;

    /**
     * 为多个 url 打开 InputStream 列表
     * 注意，本方法不会主动关闭传入的 InputStream
     *
     * @param urls URL列表
     * @return 输入流列表
     * @throws IOException 当打开连接失败时抛出
     */
    List<InputStream> openUrlInputStreams(List<String> urls) throws IOException;

    /**
     * 根据二进制数据判断图片格式（魔数判断）
     * 支持 gif/png/jpeg/bmp/webp，未识别则返回 "unknown"。
     *
     * @param data 完整的文件二进制（建议至少前 12 字节）
     * @return 文件类型字符串，如 "gif"、"png"、"jpeg"、"bmp"、"webp"、"unknown"
     */
    String detectImageType(byte[] data);

    /**
     * 根据输入流读取全部二进制后判断图片格式（此方法会消耗并读取完整输入流）
     * 建议使用 openUrlInputStream + getBytes(...) 读取后再调用 detectImageType(byte[]) 以避免重复读取
     *
     * @param in 输入流
     * @return 文件类型字符串
     * @throws IOException 当读取输入流失败时抛出
     */
    String detectImageType(InputStream in) throws IOException;

    /**
     * 从 URL 流式下载并写入指定文件（自动创建且覆盖 targetPath）。
     * 适用于视频/大文件等不希望全部加载到内存的场景。
     *
     * @param url        资源URL
     * @param targetPath 目标文件路径
     * @return 下载的文件路径
     */
    Path downloadToFile(String url, Path targetPath);

    /**
     * GIF 数据结构
     * 包含帧列表、延时信息和循环次数
     */
    @Setter
    @Getter
    class GifData {

        public List<BufferedImage> frames = new ArrayList<>();

        public List<Integer> delaysCs = new ArrayList<>();

        public int loopCount = 0;
    }
}