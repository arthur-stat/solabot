package com.arth.solabot.adapter.fetcher.http;

import com.arth.solabot.adapter.fetcher.websocket.ReplyFetcher;
import com.arth.solabot.adapter.sender.Sender;
import com.arth.solabot.core.bot.dto.ParsedPayloadDTO;
import com.arth.solabot.core.bot.dto.ReplayedMessagePayloadDTO;
import com.arth.solabot.core.bot.dto.replay.ImageRef;
import com.arth.solabot.core.bot.dto.replay.MfaceRef;
import com.arth.solabot.core.bot.exception.InvalidCommandArgsException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImgService {

    private final Sender sender;
    private final ReplyFetcher replyFetcher;
    private final WebClient webClient;

    /**
     * 从 url 下载一张静态图片，返回 BufferedImage
     * 针对动态图片，本方法只能获取首帧
     *
     * @param url
     * @return
     */
    public BufferedImage getBufferedImg(String url) {
        // 流式 getBytes(String) 实现（因为我真的碰到了缓冲区不足的问题）
        byte[] bytes = getBytes(url);
        if (bytes == null) return null;
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            return ImageIO.read(in);  // 阻塞操作在业务线程执行，千万不要卡住 Netty I/O 线程
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 从输入流读取一张静态图片，返回 BufferedImage
     * 注意，本方法不会主动关闭传入的 InputStream
     * 解析逻辑与 getBufferedImg(String) 保持一致
     *
     * @param inputStream
     * @return
     */
    public BufferedImage getBufferedImg(InputStream inputStream) {
        try {
            return ImageIO.read(inputStream);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 url 下载多张静态图片，返回 List<BufferedImage>
     * 针对动态图片，本方法只能获取首帧
     *
     * @param urls
     * @return
     */
    public List<BufferedImage> getBufferedImg(List<String> urls) {
        List<BufferedImage> imgs = new ArrayList<>();
        for (String url : urls) {
            imgs.add(getBufferedImg(url));
        }
        return imgs;
    }

    /**
     * 从多输入流读取多张静态图片，返回 List<BufferedImage>
     * 注意，本方法不会主动关闭传入的 InputStream
     *
     * @param inputs
     * @return
     */
    public List<BufferedImage> getBufferedImgFromStreams(List<InputStream> inputs) {
        List<BufferedImage> imgs = new ArrayList<>();
        for (InputStream in : inputs) {
            imgs.add(getBufferedImg(in));
        }
        return imgs;
    }

    /**
     * 从 url 流式分块下载一张图片，返回二进制数据 byte[]
     *
     * @param url
     * @return
     */
    public byte[] getBytes(String url) {
        try {
            // 流式 bodyToFlux(DataBuffer) 写入 ByteArrayOutputStream
            Flux<DataBuffer> flux = webClient.get()
                    .uri(url)
                    .accept(MediaType.APPLICATION_OCTET_STREAM)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, rsp -> rsp.createException().flatMap(Mono::error))
                    .bodyToFlux(DataBuffer.class);

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 WritableByteChannel channel = Channels.newChannel(baos)) {

                // 将每个 DataBuffer 写入 channel 并释放 DataBuffer
                flux.doOnNext(dataBuffer -> {
                            try {
                                channel.write(dataBuffer.asByteBuffer());
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            } finally {
                                DataBufferUtils.release(dataBuffer);
                            }
                        })
                        .blockLast(Duration.ofSeconds(60));  // 超时时间

                return baos.toByteArray();
            }
        } catch (Exception e) {
            log.error("Failed to get response from {}", url, e);
            return null;
        }
    }

    /**
     * 从输入流流式分块读取全部二进制数据，返回 byte[]
     * 注意，本方法不会主动关闭传入的 InputStream
     *
     * @param inputStream
     * @return
     */
    public byte[] getBytes(InputStream inputStream) {
        if (inputStream == null) return null;
        try (BufferedInputStream bis = new BufferedInputStream(inputStream);
             ByteArrayOutputStream baos = new ByteArrayOutputStream(8192)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = bis.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("HTTP error: ", e);
            return null;
        }
    }

    /**
     * 从 url 下载多张图片，返回二进制数据 byte[][]
     *
     * @param urls
     * @return
     */
    public byte[][] getBytes(List<String> urls) {
        List<byte[]> imgs = new ArrayList<>();
        for (String url : urls) {
            imgs.add(getBytes(url));
        }
        return imgs.toArray(new byte[0][]);
    }

    /**
     * 从多个输入流读取多份二进制数据，返回 byte[][]。
     * 注意，本方法不会主动关闭传入的 InputStream
     *
     * @param inputs
     * @return
     */
    public byte[][] getBytesFromStreams(List<InputStream> inputs) {
        List<byte[]> imgs = new ArrayList<>();
        for (InputStream in : inputs) {
            imgs.add(getBytes(in));
        }
        return imgs.toArray(new byte[0][]);
    }

    /**
     * 从 url 下载一份 GIF 的二进制流并将流解析为可逐帧操作的数据结构
     *
     * @param url
     * @return
     * @throws IOException
     */
    public GifData getGifFlattened(String url) throws IOException {
        byte[] inBytes = getBytes(url);
        if (inBytes == null || inBytes.length == 0) throw new IOException("empty file");
        GifData g = new GifData();
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(inBytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) throw new IOException("No GIF reader");
            ImageReader reader = readers.next();
            reader.setInput(in, false);
            int num = reader.getNumImages(true);
            if (num <= 0) {
                reader.dispose();
                return g;
            }
            // ----------- 1) 取画布尺寸：先 stream metadata 的 LSD，兜底遍历帧，再兜底第0帧尺寸 -----------
            int canvasW = 0, canvasH = 0;
            IIOMetadata streamMeta = reader.getStreamMetadata();
            if (streamMeta != null) {
                try {
                    String sfmt = streamMeta.getNativeMetadataFormatName();
                    if (sfmt != null) {
                        IIOMetadataNode sroot = (IIOMetadataNode) streamMeta.getAsTree(sfmt);
                        IIOMetadataNode lsd = findNode(sroot, "LogicalScreenDescriptor");
                        if (lsd != null) {
                            canvasW = parseIntSafe(lsd, "logicalScreenWidth", 0);
                            canvasH = parseIntSafe(lsd, "logicalScreenHeight", 0);
                        }
                    }
                } catch (Exception ignore) {
                }
            }
            if (canvasW <= 0 || canvasH <= 0) {
                // 遍历每帧，基于 ImageDescriptor 的 (x,y,w,h) 求最大包围盒
                for (int i = 0; i < num; i++) {
                    try {
                        IIOMetadata im = reader.getImageMetadata(i);
                        String ifmt = im.getNativeMetadataFormatName();
                        IIOMetadataNode iroot = (IIOMetadataNode) im.getAsTree(ifmt);
                        IIOMetadataNode imgDesc = findNode(iroot, "ImageDescriptor");
                        int fx = 0, fy = 0, fw, fh;
                        if (imgDesc != null) {
                            fx = parseIntSafe(imgDesc, "imageLeftPosition", 0);
                            fy = parseIntSafe(imgDesc, "imageTopPosition", 0);
                            fw = parseIntSafe(imgDesc, "imageWidth", reader.getWidth(i));
                            fh = parseIntSafe(imgDesc, "imageHeight", reader.getHeight(i));
                        } else {
                            // 有些实现没有 ImageDescriptor，就退回帧图尺寸
                            fw = reader.getWidth(i);
                            fh = reader.getHeight(i);
                        }
                        canvasW = Math.max(canvasW, fx + fw);
                        canvasH = Math.max(canvasH, fy + fh);
                    } catch (Exception e) {
                        // continue
                    }
                }
            }
            if (canvasW <= 0) canvasW = reader.getWidth(0);
            if (canvasH <= 0) canvasH = reader.getHeight(0);
            // ----------- 2) 准备工作画布 -----------
            BufferedImage work = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D wg = work.createGraphics();
            wg.setComposite(AlphaComposite.SrcOver);
            // ----------- 3) 帧循环：按处置规则拍平为“独立全画布帧” -----------
            for (int i = 0; i < num; i++) {
                BufferedImage raw = reader.read(i);
                IIOMetadata im = reader.getImageMetadata(i);
                String ifmt = im.getNativeMetadataFormatName();
                IIOMetadataNode iroot = (IIOMetadataNode) im.getAsTree(ifmt);
                // 读取帧矩形
                IIOMetadataNode imgDesc = findNode(iroot, "ImageDescriptor");
                int fx = (imgDesc != null) ? parseIntSafe(imgDesc, "imageLeftPosition", 0) : 0;
                int fy = (imgDesc != null) ? parseIntSafe(imgDesc, "imageTopPosition", 0) : 0;
                int fw = (imgDesc != null) ? parseIntSafe(imgDesc, "imageWidth", raw.getWidth()) : raw.getWidth();
                int fh = (imgDesc != null) ? parseIntSafe(imgDesc, "imageHeight", raw.getHeight()) : raw.getHeight();
                // 读取处置
                IIOMetadataNode gce = findNode(iroot, "GraphicControlExtension");
                String disposal = (gce != null) ? gce.getAttribute("disposalMethod") : "none";
                if (disposal == null || disposal.isEmpty()) disposal = "none";
                // 在绘制当前帧之前保存快照（仅当本帧的处置是 restoreToPrevious 时需要）
                BufferedImage prevSnapshot = null;
                if ("restoreToPrevious".equals(disposal)) {
                    prevSnapshot = deepCopy(work);
                }
                // 把当前帧绘制到工作画布 (fx, fy)
                wg.drawImage(raw, fx, fy, null);
                // 生成独立全画布帧快照
                g.frames.add(deepCopy(work));
                // 记录延时和循环
                int delayCs = readDelayCs(im);
                g.delaysCs.add(delayCs > 0 ? delayCs : 10);
                if (i == 0) g.loopCount = readLoopCount(im);
                // 根据处置法处理工作画布
                if ("restoreToBackgroundColor".equals(disposal)) {
                    clearRectTransparent(work, fx, fy, fw, fh);
                } else if ("restoreToPrevious".equals(disposal) && prevSnapshot != null) {
                    Graphics2D g2 = work.createGraphics();
                    g2.setComposite(AlphaComposite.Src);
                    g2.drawImage(prevSnapshot, 0, 0, null);
                    g2.dispose();
                }
                // nothing to do for case none
            }
            wg.dispose();
            reader.dispose();
        }
        return g;
    }

    /**
     * 从输入流读取一份 GIF 的二进制流并将流解析为可逐帧操作的数据结构
     * 解析逻辑与 getGifFlattened(String) 保持完全一致（仅读取数据来源不同）
     * 注意，本方法不会主动关闭传入的 InputStream
     *
     * @param inputStream
     * @return
     * @throws IOException
     */
    public GifData getGifFlattened(InputStream inputStream) throws IOException {
        byte[] inBytes = getBytes(inputStream);
        if (inBytes == null || inBytes.length == 0) throw new IOException("empty file");
        GifData g = new GifData();
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(inBytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) throw new IOException("No GIF reader");
            ImageReader reader = readers.next();
            reader.setInput(in, false);
            int num = reader.getNumImages(true);
            if (num <= 0) {
                reader.dispose();
                return g;
            }
            // ----------- 1) 取画布尺寸：先 stream metadata 的 LSD，兜底遍历帧，再兜底第0帧尺寸 -----------
            int canvasW = 0, canvasH = 0;
            IIOMetadata streamMeta = reader.getStreamMetadata();
            if (streamMeta != null) {
                try {
                    String sfmt = streamMeta.getNativeMetadataFormatName();
                    if (sfmt != null) {
                        IIOMetadataNode sroot = (IIOMetadataNode) streamMeta.getAsTree(sfmt);
                        IIOMetadataNode lsd = findNode(sroot, "LogicalScreenDescriptor");
                        if (lsd != null) {
                            canvasW = parseIntSafe(lsd, "logicalScreenWidth", 0);
                            canvasH = parseIntSafe(lsd, "logicalScreenHeight", 0);
                        }
                    }
                } catch (Exception ignore) {
                }
            }
            if (canvasW <= 0 || canvasH <= 0) {
                // 遍历每帧，基于 ImageDescriptor 的 (x,y,w,h) 求最大包围盒
                for (int i = 0; i < num; i++) {
                    try {
                        IIOMetadata im = reader.getImageMetadata(i);
                        String ifmt = im.getNativeMetadataFormatName();
                        IIOMetadataNode iroot = (IIOMetadataNode) im.getAsTree(ifmt);
                        IIOMetadataNode imgDesc = findNode(iroot, "ImageDescriptor");
                        int fx = 0, fy = 0, fw, fh;
                        if (imgDesc != null) {
                            fx = parseIntSafe(imgDesc, "imageLeftPosition", 0);
                            fy = parseIntSafe(imgDesc, "imageTopPosition", 0);
                            fw = parseIntSafe(imgDesc, "imageWidth", reader.getWidth(i));
                            fh = parseIntSafe(imgDesc, "imageHeight", reader.getHeight(i));
                        } else {
                            // 有些实现没有 ImageDescriptor，就退回帧图尺寸
                            fw = reader.getWidth(i);
                            fh = reader.getHeight(i);
                        }
                        canvasW = Math.max(canvasW, fx + fw);
                        canvasH = Math.max(canvasH, fy + fh);
                    } catch (Exception e) {
                        // continue
                    }
                }
            }
            if (canvasW <= 0) canvasW = reader.getWidth(0);
            if (canvasH <= 0) canvasH = reader.getHeight(0);
            // ----------- 2) 准备工作画布 -----------
            BufferedImage work = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D wg = work.createGraphics();
            wg.setComposite(AlphaComposite.SrcOver);
            // ----------- 3) 帧循环：按处置规则拍平为“独立全画布帧” -----------
            for (int i = 0; i < num; i++) {
                BufferedImage raw = reader.read(i);
                IIOMetadata im = reader.getImageMetadata(i);
                String ifmt = im.getNativeMetadataFormatName();
                IIOMetadataNode iroot = (IIOMetadataNode) im.getAsTree(ifmt);
                // 读取帧矩形
                IIOMetadataNode imgDesc = findNode(iroot, "ImageDescriptor");
                int fx = (imgDesc != null) ? parseIntSafe(imgDesc, "imageLeftPosition", 0) : 0;
                int fy = (imgDesc != null) ? parseIntSafe(imgDesc, "imageTopPosition", 0) : 0;
                int fw = (imgDesc != null) ? parseIntSafe(imgDesc, "imageWidth", raw.getWidth()) : raw.getWidth();
                int fh = (imgDesc != null) ? parseIntSafe(imgDesc, "imageHeight", raw.getHeight()) : raw.getHeight();
                // 读取处置
                IIOMetadataNode gce = findNode(iroot, "GraphicControlExtension");
                String disposal = (gce != null) ? gce.getAttribute("disposalMethod") : "none";
                if (disposal == null || disposal.isEmpty()) disposal = "none";
                // 在绘制当前帧之前保存快照（仅当本帧的处置是 restoreToPrevious 时需要）
                BufferedImage prevSnapshot = null;
                if ("restoreToPrevious".equals(disposal)) {
                    prevSnapshot = deepCopy(work);
                }
                // 把当前帧绘制到工作画布 (fx, fy)
                wg.drawImage(raw, fx, fy, null);
                // 生成独立全画布帧快照
                g.frames.add(deepCopy(work));
                // 记录延时和循环
                int delayCs = readDelayCs(im);
                g.delaysCs.add(delayCs > 0 ? delayCs : 10);
                if (i == 0) g.loopCount = readLoopCount(im);
                // 根据处置法处理工作画布
                if ("restoreToBackgroundColor".equals(disposal)) {
                    clearRectTransparent(work, fx, fy, fw, fh);
                } else if ("restoreToPrevious".equals(disposal) && prevSnapshot != null) {
                    Graphics2D g2 = work.createGraphics();
                    g2.setComposite(AlphaComposite.Src);
                    g2.drawImage(prevSnapshot, 0, 0, null);
                    g2.dispose();
                }
                // nothing to do for case none
            }
            wg.dispose();
            reader.dispose();
        }
        return g;
    }

    /**
     * 从 url 下载多份 GIF 的二进制流并将流解析为可逐帧操作的数据结构
     *
     * @param urls
     * @return
     * @throws IOException
     */
    public List<GifData> getGifFlattened(List<String> urls) throws IOException {
        List<GifData> gifs = new ArrayList<>();
        for (String url : urls) {
            gifs.add(getGifFlattened(url));
        }
        return gifs;
    }

    /**
     * 从多个输入流读取并解析多份 GIF，返回 List<GifData>
     *
     * @param inputs
     * @return
     * @throws IOException
     */
    public List<GifData> getGifFlattenedFromStreams(List<InputStream> inputs) throws IOException {
        List<GifData> gifs = new ArrayList<>();
        for (InputStream in : inputs) {
            gifs.add(getGifFlattened(in));
        }
        return gifs;
    }

    /**
     * 从 OneBot v11 报文中提取图片媒体资源的 url
     *
     * @param payload
     * @param printPrompt
     * @return
     */
    public List<String> extractImgUrls(ParsedPayloadDTO payload, boolean printPrompt) {
        String replyMsgId = payload.getReplyToMessageId();
        if (replyMsgId == null || replyMsgId.isBlank()) {
            if (printPrompt) sender.replyText(payload, "请引用一条图片消息哦");
            throw new InvalidCommandArgsException("未提取到引用消息", "未提取到引用消息");
        }

        ReplayedMessagePayloadDTO r;
        try {
            r = replyFetcher.fetch(payload.getSelfId(), Long.parseLong(replyMsgId));
        } catch (Exception e) {
            if (printPrompt) sender.replyText(payload, "获取引用消息失败：" + e.getMessage());
            return List.of();
        }
        if ((r == null) || (r.getImages().isEmpty() && r.getMfaces().isEmpty())) {
            if (printPrompt) sender.sendText(payload, "引用的消息里没有找到图片或表情包");
            return List.of();
        }

        // 提取图片 URL
        List<String> imgUrls = r.getImages().stream()
                .map(ImageRef::getSource)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        // 提取表情包 URL
        List<String> mfaceUrls = r.getMfaces().stream()
                .map(MfaceRef::getUrl)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        // 合并
        List<String> urls = Stream.concat(imgUrls.stream(), mfaceUrls.stream()).toList();

        if (urls.isEmpty()) {
            if (printPrompt) sender.sendText(payload, "引用的图片或表情包缺少可用的 url/file 字段");
            return List.of();
        }

        return urls;
    }

    public List<String> extractImgUrls(ParsedPayloadDTO payload) {
        return extractImgUrls(payload, false);
    }

    // ==================  helper  ==================
    // ==================  helper  ==================
    // ==================  helper  ==================

    public int parseIntSafe(IIOMetadataNode n, String attr, int defVal) {
        if (n == null) return defVal;
        try {
            String v = n.getAttribute(attr);
            if (v == null || v.isEmpty()) return defVal;
            return Integer.parseInt(v);
        } catch (Exception e) {
            return defVal;
        }
    }

    public BufferedImage deepCopy(BufferedImage src) {
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = dst.createGraphics();
        g2.setComposite(AlphaComposite.Src);
        g2.drawImage(src, 0, 0, null);
        g2.dispose();
        return dst;
    }

    public IIOMetadataNode findNode(IIOMetadataNode root, String name) {
        if (root == null) return null;
        if (name.equals(root.getNodeName())) return root;
        for (int i = 0; i < root.getLength(); i++) {
            IIOMetadataNode res = findNode((IIOMetadataNode) root.item(i), name);
            if (res != null) return res;
        }
        return null;
    }

    public int readLoopCount(IIOMetadata metadata) {
        try {
            String format = metadata.getNativeMetadataFormatName();
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
            IIOMetadataNode aes = findNode(root, "ApplicationExtensions");
            if (aes != null) {
                for (int i = 0; i < aes.getLength(); i++) {
                    IIOMetadataNode ae = (IIOMetadataNode) aes.item(i);
                    if ("ApplicationExtension".equals(ae.getNodeName())) {
                        String appID = ae.getAttribute("applicationID");
                        String auth = ae.getAttribute("authenticationCode");
                        if ("NETSCAPE".equals(appID) && "2.0".equals(auth)) {
                            byte[] bytes = (byte[]) ae.getUserObject();
                            if (bytes != null && bytes.length >= 3) {
                                return ((bytes[2] & 0xFF) << 8) | (bytes[1] & 0xFF);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignore) {
        }
        return 0;
    }

    public int readDelayCs(IIOMetadata metadata) {
        try {
            String format = metadata.getNativeMetadataFormatName();
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
            IIOMetadataNode gce = findNode(root, "GraphicControlExtension");
            if (gce != null) {
                String delay = gce.getAttribute("delayTime");
                return Integer.parseInt(delay);
            }
        } catch (Exception ignore) {
        }
        return 10;
    }

    public void clearRectTransparent(BufferedImage img, int x, int y, int w, int h) {
        Graphics2D g2 = img.createGraphics();
        g2.setComposite(AlphaComposite.Clear);
        g2.fillRect(x, y, w, h);
        g2.dispose();
    }

    /**
     * 打开一个 url 并返回可读的 InputStream，stream.close() 会在关闭时断开底层连接
     *
     * @param url
     * @return InputStream
     * @throws IOException
     */
    public InputStream openUrlInputStream(String url) throws IOException {
        try {
            // 使用流式下载到临时文件并返回 FileInputStream 以避免内存聚合
            Path tmp = Files.createTempFile("imgsvc-", ".tmp");
            // 下载并覆盖写入临时文件
            downloadToFile(url, tmp);
            return new FileInputStream(tmp.toFile());
        } catch (Exception e) {
            throw new IOException("Failed to fetch URL: " + url, e);
        }
    }

    /**
     * 为多个 url 打开 InputStream 列表
     * 注意，本方法不会主动关闭传入的 InputStream
     *
     * @param urls
     * @return
     * @throws IOException
     */
    public List<InputStream> openUrlInputStreams(List<String> urls) throws IOException {
        List<InputStream> list = new ArrayList<>();
        for (String u : urls) list.add(openUrlInputStream(u));
        return list;
    }

    /**
     * 根据二进制数据判断图片格式（魔数判断）
     * 支持 gif/png/jpeg/bmp/webp，未识别则返回 "unknown"。
     *
     * @param data 完整的文件二进制（建议至少前 12 字节）
     * @return 文件类型字符串，如 "gif"、"png"、"jpeg"、"bmp"、"webp"、"unknown"
     */
    public String detectImageType(byte[] data) {
        if (data == null || data.length < 4) return "unknown";
        // GIF
        if (data.length >= 6) {
            String h6 = new String(data, 0, 6, StandardCharsets.US_ASCII);
            if ("GIF87a".equals(h6) || "GIF89a".equals(h6)) return "gif";
        }
        // PNG
        if (data.length >= 8) {
            if ((data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G' && data[4] == 0x0D && data[5] == 0x0A && data[6] == 0x1A && data[7] == 0x0A)
                return "png";
        }
        // JPEG
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) return "jpeg";
        // BMP
        if (data[0] == 'B' && data[1] == 'M') return "bmp";
        // WEBP (RIFF....WEBP)
        if (data.length >= 12) {
            String riff = new String(data, 0, 4, StandardCharsets.US_ASCII);
            String webp = new String(data, 8, 4, StandardCharsets.US_ASCII);
            if ("RIFF".equals(riff) && "WEBP".equals(webp)) return "webp";
        }
        return "unknown";
    }

    /**
     * 根据输入流读取全部二进制后判断图片格式（此方法会消耗并读取完整输入流）
     * 建议使用 openUrlInputStream + getBytes(...) 读取后再调用 detectImageType(byte[]) 以避免重复读取
     *
     * @param in
     * @return
     * @throws IOException
     */
    public String detectImageType(InputStream in) throws IOException {
        byte[] data = getBytes(in);
        return detectImageType(data);
    }

    /**
     * 从 URL 流式下载并写入指定文件（自动创建且覆盖 targetPath）。
     * 适用于视频/大文件等不希望全部加载到内存的场景。
     *
     * @param url
     * @param targetPath
     * @return
     */
    public Path downloadToFile(String url, Path targetPath) {
        Flux<DataBuffer> flux = webClient.get()
                .uri(url)
                .accept(MediaType.APPLICATION_OCTET_STREAM)
                .retrieve()
                .onStatus(HttpStatusCode::isError, rsp -> rsp.createException().flatMap(Mono::error))
                .bodyToFlux(DataBuffer.class);

        try {
            if (targetPath.getParent() != null) Files.createDirectories(targetPath.getParent());
            // StandardOpenOption.CREATE, TRUNCATE_EXISTING, WRITE
            try (FileOutputStream fos = new FileOutputStream(targetPath.toFile());
                 WritableByteChannel channel = Channels.newChannel(fos)) {

                flux.doOnNext(dataBuffer -> {
                            try {
                                channel.write(dataBuffer.asByteBuffer());
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            } finally {
                                DataBufferUtils.release(dataBuffer);
                            }
                        })
                        .blockLast(Duration.ofMinutes(10));  // 考虑大文件，超时设置 10min

                return targetPath;
            }
        } catch (Exception e) {
            log.error("Failed to downloadToFile: " + url, e);
            return null;
        }
    }

    @Data
    public static class GifData {

        private List<BufferedImage> frames = new ArrayList<>();

        private List<Integer> delaysCs = new ArrayList<>();

        private int loopCount = 0;
    }
}
