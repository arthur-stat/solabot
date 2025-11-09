package com.arth.solabot.plugin.custom;

import com.arth.solabot.adapter.controller.ApiPaths;
import com.arth.solabot.adapter.sender.Sender;
import com.arth.solabot.core.bot.dto.ParsedPayloadDTO;
import com.arth.solabot.core.bot.exception.InternalServerErrorException;
import com.arth.solabot.core.bot.invoker.annotation.BotCommand;
import com.arth.solabot.core.bot.invoker.annotation.BotPlugin;
import com.arth.solabot.core.infrastructure.cache.service.ImageCacheService;
import com.arth.solabot.core.infrastructure.network.service.ImgNetworkService;
import com.arth.solabot.core.infrastructure.network.service.ImgNetworkService.GifData;
import com.arth.solabot.plugin.system.Plugin;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.imageio.*;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.List;

@BotPlugin(name = {"img"})
@RequiredArgsConstructor
public class Img extends Plugin {

    private final Sender sender;
    private final ImgNetworkService imgNetworkService;
    private final ImageCacheService imageCacheService;
    private final ApiPaths apiPaths;

    private static final int GIF_MIN_CS = 2;      // GIF 最小播放时间间隔
    private static final int GIF_MAX_CS = 65535;  // GIF 最大播放时间间隔

    @Getter
    public final String helpText = """
            img 图片处理模块目前支持以下命令：
              - l 或 left: 镜像对称，左对称
              - r 或 right: 镜像对称，右对称
              - u 或 up：镜像对称，上对称
              - d 或 down：镜像对称，下对称
              - rotate <90整数倍>：顺时针旋转，默认90°
              - speed <n>: 加速 gif 为 n 倍
                n 可以为负数，表示倒放
              - cut: 纯色背景抠图透明底
                可以跟 <阈值> 参数指定阈值，默认100，阈值表示像素与背景RGB欧氏距离的最大均方误差
              - gray: 转灰度图
              - invert: 反色
              - mirror: 水平镜像翻转
              - gif: 转gif
                在QQ里看起来会更像表情包
              - png: 转png
              - check: 检查图片url
            
            命令使用示例：/img l""";

    @Override
    @BotCommand(command = "index")
    public void index(ParsedPayloadDTO payload) {
        sender.replyText(payload, "具体的图片处理命令是什么呢？");
    }

    @Override
    @BotCommand(command = "help")
    public void help(ParsedPayloadDTO payload) {
        super.help(payload);
    }

    @BotCommand(command = {"l", "left"})
    public void leftSymmetry(ParsedPayloadDTO payload) throws IOException {
        // 支持静态图片与 GIF（动态）混合输入，输出保持输入顺序
        List<String> urls = imgNetworkService.extractImgUrls(payload, true);
        if (urls == null || urls.isEmpty()) return;

        int n = urls.size();
        List<String> cacheUrlsOrdered = new ArrayList<>(Collections.nCopies(n, null));

        for (int i = 0; i < n; i++) {
            String url = urls.get(i);
            byte[] data = imgNetworkService.getBytes(url);
            if (data == null || data.length == 0) continue;
            String type = imgNetworkService.detectImageType(data);
            if ("gif".equals(type)) {
                // 处理 GIF：解析、逐帧 midLeft、写回 GIF
                GifData gif = imgNetworkService.getGifFlattened(new ByteArrayInputStream(data));
                if (gif == null) continue;
                gifSymmetryByLeft(List.of(gif));
                byte[] outBytes = writeGifToBytes(gif.getFrames(), gif.getDelaysCs(), gif.getLoopCount());
                String uuid = imageCacheService.cacheImage(outBytes);
                cacheUrlsOrdered.set(i, apiPaths.buildGifUrl(uuid));
            } else {
                // 处理静态图片（PNG/JPEG/...）
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
                if (img == null) continue;
                List<BufferedImage> imgs = new ArrayList<>();
                imgs.add(img);
                // 原有静态逻辑：midLeft
                symmetryByLeft(imgs);
                // 缓存为 png
                String uuid = imageCacheService.cacheImage(imgs.get(0), "png");
                cacheUrlsOrdered.set(i, apiPaths.buildPngUrl(uuid));
            }
        }

        List<String> cacheUrls = new ArrayList<>();
        for (String s : cacheUrlsOrdered) if (s != null) cacheUrls.add(s);
        if (!cacheUrls.isEmpty()) sender.sendImage(payload, cacheUrls);
    }

    @BotCommand(command = {"r", "right"})
    public void rightSymmetry(ParsedPayloadDTO payload) throws IOException {
        // 支持静态图片与 GIF（动态）混合输入，输出保持输入顺序
        List<String> urls = imgNetworkService.extractImgUrls(payload, true);
        if (urls == null || urls.isEmpty()) return;

        int n = urls.size();
        List<String> cacheUrlsOrdered = new ArrayList<>(Collections.nCopies(n, null));

        for (int i = 0; i < n; i++) {
            String url = urls.get(i);
            byte[] data = imgNetworkService.getBytes(url);
            if (data == null || data.length == 0) continue;
            String type = imgNetworkService.detectImageType(data);
            if ("gif".equals(type)) {
                GifData gif = imgNetworkService.getGifFlattened(new ByteArrayInputStream(data));
                if (gif == null) continue;
                gifSymmetryByRight(List.of(gif));
                byte[] outBytes = writeGifToBytes(gif.getFrames(), gif.getDelaysCs(), gif.getLoopCount());
                String uuid = imageCacheService.cacheImage(outBytes);
                cacheUrlsOrdered.set(i, apiPaths.buildGifUrl(uuid));
            } else {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
                if (img == null) continue;
                List<BufferedImage> imgs = new ArrayList<>();
                imgs.add(img);
                symmetryByRight(imgs);
                String uuid = imageCacheService.cacheImage(imgs.get(0), "png");
                cacheUrlsOrdered.set(i, apiPaths.buildPngUrl(uuid));
            }
        }

        List<String> cacheUrls = new ArrayList<>();
        for (String s : cacheUrlsOrdered) if (s != null) cacheUrls.add(s);
        if (!cacheUrls.isEmpty()) sender.sendImage(payload, cacheUrls);
    }

    @BotCommand(command = {"u", "up"})
    public void up(ParsedPayloadDTO payload) throws IOException {
        // 支持静态图片与 GIF（动态）混合输入，输出保持输入顺序
        List<String> urls = imgNetworkService.extractImgUrls(payload, true);
        if (urls == null || urls.isEmpty()) return;

        int n = urls.size();
        List<String> cacheUrlsOrdered = new ArrayList<>(Collections.nCopies(n, null));

        for (int i = 0; i < n; i++) {
            String url = urls.get(i);
            byte[] data = imgNetworkService.getBytes(url);
            if (data == null || data.length == 0) continue;
            String type = imgNetworkService.detectImageType(data);

            if ("gif".equals(type)) {
                // 解析 GIF 并逐帧处理
                GifData gif = imgNetworkService.getGifFlattened(new ByteArrayInputStream(data));
                if (gif == null) continue;
                gifSymmetryByUp(List.of(gif));
                byte[] outBytes = writeGifToBytes(gif.getFrames(), gif.getDelaysCs(), gif.getLoopCount());
                String uuid = imageCacheService.cacheImage(outBytes);
                cacheUrlsOrdered.set(i, apiPaths.buildGifUrl(uuid));
            } else {
                // 静态图片：单帧翻转
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
                if (img == null) continue;
                List<BufferedImage> imgs = new ArrayList<>();
                imgs.add(img);
                symmetryByUp(imgs);
                String uuid = imageCacheService.cacheImage(imgs.get(0), "png");
                cacheUrlsOrdered.set(i, apiPaths.buildPngUrl(uuid));
            }
        }

        List<String> cacheUrls = new ArrayList<>();
        for (String s : cacheUrlsOrdered) if (s != null) cacheUrls.add(s);
        if (!cacheUrls.isEmpty()) sender.sendImage(payload, cacheUrls);
    }

    @BotCommand(command = {"d", "down"})
    public void down(ParsedPayloadDTO payload) throws IOException {
        // 支持静态图片与 GIF（动态）混合输入，输出保持输入顺序
        List<String> urls = imgNetworkService.extractImgUrls(payload, true);
        if (urls == null || urls.isEmpty()) return;

        int n = urls.size();
        List<String> cacheUrlsOrdered = new ArrayList<>(Collections.nCopies(n, null));

        for (int i = 0; i < n; i++) {
            String url = urls.get(i);
            byte[] data = imgNetworkService.getBytes(url);
            if (data == null || data.length == 0) continue;
            String type = imgNetworkService.detectImageType(data);

            if ("gif".equals(type)) {
                GifData gif = imgNetworkService.getGifFlattened(new ByteArrayInputStream(data));
                if (gif == null) continue;
                gifSymmetryByDown(List.of(gif));
                byte[] outBytes = writeGifToBytes(gif.getFrames(), gif.getDelaysCs(), gif.getLoopCount());
                String uuid = imageCacheService.cacheImage(outBytes);
                cacheUrlsOrdered.set(i, apiPaths.buildGifUrl(uuid));
            } else {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
                if (img == null) continue;
                List<BufferedImage> imgs = new ArrayList<>();
                imgs.add(img);
                symmetryByDown(imgs);
                String uuid = imageCacheService.cacheImage(imgs.get(0), "png");
                cacheUrlsOrdered.set(i, apiPaths.buildPngUrl(uuid));
            }
        }

        List<String> cacheUrls = new ArrayList<>();
        for (String s : cacheUrlsOrdered) if (s != null) cacheUrls.add(s);
        if (!cacheUrls.isEmpty()) sender.sendImage(payload, cacheUrls);
    }

    /**
     * 兼容旧命令 mid
     *
     * @param payload
     * @throws IOException
     */
    @BotCommand(command = "mid")
    public void mid(ParsedPayloadDTO payload) throws IOException {
        leftSymmetry(payload);
    }

    /**
     * 兼容旧命令 mid <args>
     *
     * @param payload
     * @param args
     * @throws IOException
     */
    @BotCommand(command = "mid")
    public void mid(ParsedPayloadDTO payload, List<String> args) throws IOException {
        if (args == null || args.isEmpty() || args.get(0).equals("l") || args.get(0).equals("left")) {
            leftSymmetry(payload);
        } else if (args.get(0).equals("r") || args.get(0).equals("right")) {
            rightSymmetry(payload);
        } else {
            sender.replyText(payload, "mid 命令支持的参数是 l 或 r，默认 l");
        }
    }

    @BotCommand(command = {"rot", "rotate"})
    public void rotate(ParsedPayloadDTO payload, List<String> args) throws IOException {
        int angle = 90;

        if (args != null && !args.isEmpty()) {
            try {
                angle = Integer.parseInt(args.get(0));
            } catch (NumberFormatException e) {
                sender.replyText(payload, "非法的角度参数");
                return;
            }
        }

        while (angle < 0) angle += 360;
        angle %= 360;

        if (angle != 0 && angle != 90 && angle != 180 && angle != 270) {
            sender.replyText(payload, "仅支持 90° 整数倍的旋转");
            return;
        }

        // 统一提取输入图片
        List<String> urls = imgNetworkService.extractImgUrls(payload, true);
        if (urls == null || urls.isEmpty()) return;

        if (angle == 0) {
            sender.sendImage(payload, urls);
            return;
        }

        int n = urls.size();
        List<String> cacheUrlsOrdered = new ArrayList<>(Collections.nCopies(n, null));

        for (int i = 0; i < n; i++) {
            String url = urls.get(i);
            byte[] data = imgNetworkService.getBytes(url);
            if (data == null || data.length == 0) continue;
            String type = imgNetworkService.detectImageType(data);

            if ("gif".equals(type)) {
                // ---- 动态 GIF ----
                GifData gif = imgNetworkService.getGifFlattened(new ByteArrayInputStream(data));
                if (gif == null) continue;
                rotateGif(gif, angle);
                byte[] outBytes = writeGifToBytes(gif.getFrames(), gif.getDelaysCs(), gif.getLoopCount());
                String uuid = imageCacheService.cacheImage(outBytes);
                cacheUrlsOrdered.set(i, apiPaths.buildGifUrl(uuid));
            } else {
                // ---- 静态图片 ----
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
                if (img == null) continue;
                List<BufferedImage> imgs = new ArrayList<>();
                imgs.add(img);
                rotateImg(imgs, angle);
                String uuid = imageCacheService.cacheImage(imgs.get(0), "png");
                cacheUrlsOrdered.set(i, apiPaths.buildPngUrl(uuid));
            }
        }

        List<String> cacheUrls = new ArrayList<>();
        for (String s : cacheUrlsOrdered) if (s != null) cacheUrls.add(s);
        if (!cacheUrls.isEmpty()) sender.sendImage(payload, cacheUrls);
    }

    @BotCommand(command = "speed")
    public void speed(ParsedPayloadDTO payload, List<String> args) throws IOException {
        if (args == null || args.isEmpty()) {
            sender.replyText(payload, "没有指定加速 / 减速倍率哦");
            return;
        }

        String arg = args.get(0);
        if (arg.endsWith("x")) arg = arg.substring(0, arg.length() - 1);

        double rate;
        try {
            rate = Double.parseDouble(arg);
        } catch (NumberFormatException e) {
            sender.replyText(payload, "倍率参数不合法");
            return;
        }
        if (rate == 0) {
            sender.replyText(payload, "倍率不能为 0");
            return;
        }

        boolean reverse = rate < 0;
        double r = Math.abs(rate);

        List<String> urls = imgNetworkService.extractImgUrls(payload, true);
        if (urls == null || urls.isEmpty()) return;

        List<String> cacheUrls = new ArrayList<>();

        var gifs = imgNetworkService.getGifFlattened(urls);

        for (var gif : gifs) {
            GifData out = retime(gif, r, reverse);
            byte[] outBytes = writeGifToBytes(out.getFrames(), out.getDelaysCs(), out.getLoopCount());
            String uuid = imageCacheService.cacheImage(outBytes);
            cacheUrls.add(apiPaths.buildGifUrl(uuid));
        }

        if (!cacheUrls.isEmpty()) sender.sendImage(payload, cacheUrls);
    }

    @BotCommand(command = {"cut", "cutout"})
    public void cutout(ParsedPayloadDTO payload, List<String> args) throws IOException {
        int threshold = 100;

        if (args != null && !args.isEmpty()) {
            try {
                threshold = Integer.parseInt(args.get(0));
            } catch (NumberFormatException e) {
                sender.replyText(payload, "阈值参数不合法");
                return;
            }
        }

        List<String> urls = imgNetworkService.extractImgUrls(payload, true);
        if (urls == null || urls.isEmpty()) return;

        // 支持静态图片与 GIF 的混合输入，输出顺序保留输入顺序
        int n = urls.size();
        List<String> cacheUrlsOrdered = new ArrayList<>(Collections.nCopies(n, null));

        for (int i = 0; i < n; i++) {
            String url = urls.get(i);
            byte[] data = imgNetworkService.getBytes(url);
            if (data == null || data.length == 0) continue;
            String type = imgNetworkService.detectImageType(data);
            if ("gif".equals(type)) {
                // GIF：逐帧抠图
                GifData gif = imgNetworkService.getGifFlattened(new ByteArrayInputStream(data));
                if (gif == null) continue;
                // 检查帧尺寸，若有帧过小则直接提示并返回（与静态逻辑保持一致）
                for (BufferedImage frame : gif.getFrames()) {
                    if (frame.getWidth() < 6 || frame.getHeight() < 6) {
                        sender.replyText(payload, "图片过小");
                        return;
                    }
                }
                gifCutout(gif, threshold);
                byte[] outBytes = writeGifToBytes(gif.getFrames(), gif.getDelaysCs(), gif.getLoopCount());
                String uuid = imageCacheService.cacheImage(outBytes);
                cacheUrlsOrdered.set(i, apiPaths.buildGifUrl(uuid));
            } else {
                // 静态图片：保留原有完整逻辑（针对单张图片）
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
                if (img == null) continue;
                // 强制使用支持 ARGB 的 BufferedImage 子类型，否则写入时 alpha 通道会被 ImageIO 忽略
                List<BufferedImage> imgs = new ArrayList<>();
                imgs.add(img);
                toARGB(imgs);

                int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1}};

                for (BufferedImage targetImg : imgs) {
                    int width = targetImg.getWidth();
                    int height = targetImg.getHeight();
                    /* map pixel to index */
                    Map<Integer, Set<Long>> rgbSta = new HashMap<>();

                    if (width < 6 || height < 6) {
                        sender.replyText(payload, "图片过小");
                        return;
                    }

                    // 1. 统计四个边缘上出现频次最高的颜色，每条边宽度硬编码 3
                    for (int w = 0; w < width; w++) {
                        for (int k = 0; k < 3; k++) {
                            rgbSta.computeIfAbsent(targetImg.getRGB(w, k), kk -> new HashSet<>()).add(((long) w << 32) | (k & 0xffffffffL));
                            rgbSta.computeIfAbsent(targetImg.getRGB(w, height - k - 1), kk -> new HashSet<>()).add(((long) w << 32) | ((height - k - 1) & 0xffffffffL));
                        }
                    }
                    for (int h = 1; h < height - 1; h++) {
                        for (int k = 0; k < 3; k++) {
                            rgbSta.computeIfAbsent(targetImg.getRGB(k, h), kk -> new HashSet<>()).add(((long) k << 32) | (h & 0xffffffffL));
                            rgbSta.computeIfAbsent(targetImg.getRGB(width - k - 1, h), kk -> new HashSet<>()).add(((long) (width - k - 1) << 32) | (h & 0xffffffffL));
                        }
                    }

                    Set<Long> bgPixelIndex = rgbSta.values().stream()
                            .max(Comparator.comparingInt(Set::size))
                            .orElseThrow(() -> new InternalServerErrorException("rgbSta cannot be empty"));

                    if (bgPixelIndex.isEmpty()) throw new InternalServerErrorException("bgPixelIndex cannot be empty");

                    long aBgIndex = bgPixelIndex.iterator().next();
                    int bgRgb = targetImg.getRGB((int) (aBgIndex >>> 32), (int) aBgIndex);

                    // 2. 初始化优先队列，将所有边缘上出现频次最高的颜色加入优先队列
                    Set<Long> visited = new HashSet<>(bgPixelIndex);
                    PriorityQueue<Pixel> pq = new PriorityQueue<>(Comparator.comparingDouble(Pixel::dist));
                    for (long index : bgPixelIndex) {
                        int x = (int) (index >>> 32);
                        int y = (int) index;
                        pq.offer(new Pixel(x, y, targetImg.getRGB(x, y), 0));
                    }

                    // 3. BFS 抠图透明底
                    while (!pq.isEmpty()) {
                        Pixel p = pq.poll();
                        int x = p.x();
                        int y = p.y();

                        if (p.dist() > threshold) continue;
                        targetImg.setRGB(x, y, p.rgb() & 0x00ffffff);

                        for (int[] d : dirs) {
                            int nx = x + d[0];
                            int ny = y + d[1];
                            if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;

                            long idx = ((long) nx << 32) | (ny & 0xffffffffL);
                            if (visited.contains(idx)) continue;

                            visited.add(idx);
                            int nRgb = targetImg.getRGB(nx, ny);
                            int dist = distance(bgRgb, nRgb);

                            pq.offer(new Pixel(nx, ny, nRgb, dist));
                        }
                    }
                }

                String uuid = imageCacheService.cacheImage(imgs.get(0), "png");
                cacheUrlsOrdered.set(i, apiPaths.buildPngUrl(uuid));
            }
        }

        List<String> cacheUrls = new ArrayList<>();
        for (String s : cacheUrlsOrdered) if (s != null) cacheUrls.add(s);
        if (!cacheUrls.isEmpty()) sender.sendImage(payload, cacheUrls);
    }

    @BotCommand(command = "gray")
    public void gray(ParsedPayloadDTO payload) throws IOException {
        List<String> urls = imgNetworkService.extractImgUrls(payload, true);
        if (urls == null || urls.isEmpty()) return;

        int n = urls.size();
        List<String> cacheUrlsOrdered = new ArrayList<>(Collections.nCopies(n, null));

        for (int i = 0; i < n; i++) {
            String url = urls.get(i);
            byte[] data = imgNetworkService.getBytes(url);
            if (data == null || data.length == 0) continue;
            String type = imgNetworkService.detectImageType(data);
            if ("gif".equals(type)) {
                GifData gif = imgNetworkService.getGifFlattened(new ByteArrayInputStream(data));
                if (gif == null) continue;
                gifGray(gif);
                byte[] outBytes = writeGifToBytes(gif.getFrames(), gif.getDelaysCs(), gif.getLoopCount());
                String uuid = imageCacheService.cacheImage(outBytes);
                cacheUrlsOrdered.set(i, apiPaths.buildGifUrl(uuid));
            } else {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
                if (img == null) continue;
                int width = img.getWidth();
                int height = img.getHeight();

                BufferedImage grayImg = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
                Graphics g = grayImg.getGraphics();
                g.drawImage(img, 0, 0, null);
                g.dispose();
                // 为与原本输出为 png 的路径一致，缓存为 ARGB png
                BufferedImage outImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = outImg.createGraphics();
                g2.drawImage(grayImg, 0, 0, null);
                g2.dispose();

                String uuid = imageCacheService.cacheImage(outImg, "png");
                cacheUrlsOrdered.set(i, apiPaths.buildPngUrl(uuid));
            }
        }

        List<String> cacheUrls = new ArrayList<>();
        for (String s : cacheUrlsOrdered) if (s != null) cacheUrls.add(s);
        if (!cacheUrls.isEmpty()) sender.sendImage(payload, cacheUrls);
    }

    @BotCommand(command = "invert")
    public void invert(ParsedPayloadDTO payload) throws IOException {
        List<String> urls = imgNetworkService.extractImgUrls(payload, true);
        if (urls == null || urls.isEmpty()) return;

        int n = urls.size();
        List<String> cacheUrlsOrdered = new ArrayList<>(Collections.nCopies(n, null));

        for (int i = 0; i < n; i++) {
            String url = urls.get(i);
            byte[] data = imgNetworkService.getBytes(url);
            if (data == null || data.length == 0) continue;

            String type = imgNetworkService.detectImageType(data);

            if ("gif".equals(type)) {
                GifData gif = imgNetworkService.getGifFlattened(new ByteArrayInputStream(data));
                if (gif == null) continue;
                gifInvert(gif);
                byte[] outBytes = writeGifToBytes(gif.getFrames(), gif.getDelaysCs(), gif.getLoopCount());
                String uuid = imageCacheService.cacheImage(outBytes);
                cacheUrlsOrdered.set(i, apiPaths.buildGifUrl(uuid));
            } else {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
                if (img == null) continue;
                invertImage(img);
                String uuid = imageCacheService.cacheImage(img, "png");
                cacheUrlsOrdered.set(i, apiPaths.buildPngUrl(uuid));
            }
        }

        List<String> cacheUrls = new ArrayList<>();
        for (String s : cacheUrlsOrdered) if (s != null) cacheUrls.add(s);
        if (!cacheUrls.isEmpty()) sender.sendImage(payload, cacheUrls);
    }

    @BotCommand(command = "mirror")
    public void mirror(ParsedPayloadDTO payload) throws IOException {
        List<String> urls = imgNetworkService.extractImgUrls(payload, true);
        if (urls == null || urls.isEmpty()) return;

        int n = urls.size();
        List<String> cacheUrlsOrdered = new ArrayList<>(Collections.nCopies(n, null));

        for (int i = 0; i < n; i++) {
            String url = urls.get(i);
            byte[] data = imgNetworkService.getBytes(url);
            if (data == null || data.length == 0) continue;
            String type = imgNetworkService.detectImageType(data);
            if ("gif".equals(type)) {
                GifData gif = imgNetworkService.getGifFlattened(new ByteArrayInputStream(data));
                if (gif == null) continue;
                gifMirror(gif);
                byte[] outBytes = writeGifToBytes(gif.getFrames(), gif.getDelaysCs(), gif.getLoopCount());
                String uuid = imageCacheService.cacheImage(outBytes);
                cacheUrlsOrdered.set(i, apiPaths.buildGifUrl(uuid));
            } else {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
                if (img == null) continue;
                int width = img.getWidth();
                int height = img.getHeight();

                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width / 2; x++) {
                        int leftPixel = img.getRGB(x, y);
                        int rightPixel = img.getRGB(width - x - 1, y);

                        img.setRGB(x, y, rightPixel);
                        img.setRGB(width - x - 1, y, leftPixel);
                    }
                }

                String uuid = imageCacheService.cacheImage(img, "png");
                cacheUrlsOrdered.set(i, apiPaths.buildPngUrl(uuid));
            }
        }

        List<String> cacheUrls = new ArrayList<>();
        for (String s : cacheUrlsOrdered) if (s != null) cacheUrls.add(s);
        if (!cacheUrls.isEmpty()) sender.sendImage(payload, cacheUrls);
    }

    @BotCommand(command = "gif")
    public void gif(ParsedPayloadDTO payload) throws IOException {
        toType(payload, "gif");
    }

    @BotCommand(command = "png")
    public void png(ParsedPayloadDTO payload) throws IOException {
        toType(payload, "png");
    }

    @BotCommand(command = "check")
    public void check(ParsedPayloadDTO payload) {
        List<String> urls = imgNetworkService.extractImgUrls(payload, true);
        if (urls == null || urls.isEmpty()) {
            sender.sendText(payload, "引用消息里没有找到图片");
        } else {
            sender.replyText(payload, "提取到的图片 URL 为：" + urls);
        }
    }


    // ***** ============= helper ============= *****
    // ***** ============= helper ============= *****
    // ***** ============= helper ============= *****


    private record Pixel(int x, int y, int rgb, int dist) {
    }

    /**
     * 返回 RGB 的平方距离差值（避免欧氏距离的根号计算）
     *
     * @param rgb1
     * @param rgb2
     * @return
     */
    private int distance(int rgb1, int rgb2) {
        int r1 = (rgb1 >> 16) & 0xff;
        int g1 = (rgb1 >> 8) & 0xff;
        int b1 = rgb1 & 0xff;

        int r2 = (rgb2 >> 16) & 0xff;
        int g2 = (rgb2 >> 8) & 0xff;
        int b2 = rgb2 & 0xff;

        return (r1 - r2) * (r1 - r2) + (g1 - g2) * (g1 - g2) + (b1 - b2) * (b1 - b2);
    }

    private void toARGB(List<BufferedImage> imgs) {
        for (int i = 0; i < imgs.size(); i++) {
            BufferedImage img = imgs.get(i);
            if (img.getType() != BufferedImage.TYPE_INT_ARGB) {
                BufferedImage argbImg = new BufferedImage(
                        img.getWidth(),
                        img.getHeight(),
                        BufferedImage.TYPE_INT_ARGB
                );
                Graphics2D g = argbImg.createGraphics();
                g.drawImage(img, 0, 0, null);
                g.dispose();
                imgs.set(i, argbImg);
            }
        }
    }

    private byte[] writeGifToBytes(List<BufferedImage> frames, List<Integer> delaysCs, int loopCount) throws IOException {
        if (frames == null || frames.isEmpty()) throw new IllegalArgumentException("No frames");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        ImageWriteParam params = writer.getDefaultWriteParam();

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            ImageTypeSpecifier type = ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_ARGB);

            // 流级元数据：写入 NETSCAPE 2.0 循环扩展
            IIOMetadata streamMeta = writer.getDefaultImageMetadata(type, params);
            String format = streamMeta.getNativeMetadataFormatName();
            IIOMetadataNode root = (IIOMetadataNode) streamMeta.getAsTree(format);
            IIOMetadataNode appExts = new IIOMetadataNode("ApplicationExtensions");
            IIOMetadataNode appExt = new IIOMetadataNode("ApplicationExtension");
            appExt.setAttribute("applicationID", "NETSCAPE");
            appExt.setAttribute("authenticationCode", "2.0");
            int loop = Math.max(0, loopCount); // 0 = 无限
            byte[] loopBytes = new byte[]{0x01, (byte) (loop & 0xFF), (byte) ((loop >> 8) & 0xFF)};
            appExt.setUserObject(loopBytes);
            appExts.appendChild(appExt);
            root.appendChild(appExts);
            streamMeta.setFromTree(format, root);

            writer.prepareWriteSequence(streamMeta);

            for (int i = 0; i < frames.size(); i++) {
                BufferedImage bi = frames.get(i);
                int delay = Math.min(Math.max(delaysCs.get(i), GIF_MIN_CS), GIF_MAX_CS);

                IIOMetadata frameMeta = writer.getDefaultImageMetadata(type, params);
                IIOMetadataNode fmRoot = (IIOMetadataNode) frameMeta.getAsTree(format);
                IIOMetadataNode gce = imgNetworkService.findNode(fmRoot, "GraphicControlExtension");
                if (gce == null) {
                    gce = new IIOMetadataNode("GraphicControlExtension");
                    fmRoot.appendChild(gce);
                }
                gce.setAttribute("disposalMethod", "restoreToBackgroundColor");
                gce.setAttribute("userInputFlag", "FALSE");
                gce.setAttribute("transparentColorFlag", "FALSE");
                gce.setAttribute("delayTime", Integer.toString(delay));
                gce.setAttribute("transparentColorIndex", "0");

                frameMeta.setFromTree(format, fmRoot);
                writer.writeToSequence(new IIOImage(bi, null, frameMeta), params);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }

    private GifData retime(GifData gif, double rateAbs, boolean reverse) {
        if (rateAbs <= 0) throw new IllegalArgumentException("rate must be > 0");

        List<BufferedImage> frames = new ArrayList<>(gif.getFrames());
        List<Integer> delays = new ArrayList<>(gif.getDelaysCs());

        if (reverse) {
            Collections.reverse(frames);
            Collections.reverse(delays);
        }

        GifData out = new GifData();
        out.setLoopCount(gif.getLoopCount());

        if (rateAbs == 1.0) {
            out.setFrames(frames);
            out.setDelaysCs(delays);
            return out;
        }

        if (rateAbs < 1.0) {
            for (int i = 0; i < frames.size(); i++) {
                int d = delays.get(i);
                int nd = Math.max(GIF_MIN_CS, Math.min(GIF_MAX_CS, (int) Math.round(d / rateAbs)));
                out.getFrames().add(frames.get(i));
                out.getDelaysCs().add(nd);
            }
            return out;
        }

        double need = rateAbs;
        List<BufferedImage> curF = frames;
        List<Integer> curD = delays;

        while (true) {
            boolean allOk = true;
            for (int d : curD) {
                int scaled = Math.max((int) Math.round(d / need), GIF_MIN_CS);
                if (scaled <= GIF_MIN_CS) {
                    allOk = false;
                    break;
                }
            }
            if (allOk) break;

            // 当无法保证最小播放间隔时间处于合理范围时，考虑删帧
            // 删奇数帧（索引 1,3,5, ...），保留偶数 0,2,4,...
            if (curF.size() <= 1) break;
            List<BufferedImage> nf = new ArrayList<>((curF.size() + 1) / 2);
            List<Integer> nd = new ArrayList<>((curD.size() + 1) / 2);
            for (int i = 0; i < curF.size(); i += 2) {
                nf.add(curF.get(i));
                nd.add(curD.get(i));
            }
            curF = nf;
            curD = nd;

            need /= 2.0;
            if (need <= 1.0) break;
        }

        for (int i = 0; i < curF.size(); i++) {
            int d = curD.get(i);
            int scaled = Math.max((int) Math.round(d / Math.max(need, 1.0)), GIF_MIN_CS);
            out.getFrames().add(curF.get(i));
            out.getDelaysCs().add(scaled);
        }
        if (out.getFrames().isEmpty()) {
            out.getFrames().add(frames.get(0));
            out.getDelaysCs().add(Math.max(GIF_MIN_CS, (int) Math.round(delays.get(0) / rateAbs)));
        }
        return out;
    }

    private void symmetryByLeft(List<BufferedImage> imgs) {
        for (BufferedImage img : imgs) {
            int width = img.getWidth();
            int height = img.getHeight();

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width / 2; x++) {
                    int leftPixel = img.getRGB(x, y);
                    img.setRGB(width - x - 1, y, leftPixel);
                }
            }
        }
    }

    private void symmetryByRight(List<BufferedImage> imgs) {
        for (BufferedImage img : imgs) {
            int width = img.getWidth();
            int height = img.getHeight();

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width / 2; x++) {
                    int rightPixel = img.getRGB(width - x - 1, y);
                    img.setRGB(x, y, rightPixel);
                }
            }
        }
    }

    private void symmetryByUp(List<BufferedImage> imgs) {
        for (BufferedImage img : imgs) {
            int width = img.getWidth();
            int height = img.getHeight();
            int half = height / 2;
            for (int y = 0; y < half; y++) {
                for (int x = 0; x < width; x++) {
                    int topPixel = img.getRGB(x, y);
                    img.setRGB(x, height - y - 1, topPixel);
                }
            }
        }
    }

    private void symmetryByDown(List<BufferedImage> imgs) {
        for (BufferedImage img : imgs) {
            int width = img.getWidth();
            int height = img.getHeight();
            int half = height / 2;
            for (int y = 0; y < half; y++) {
                for (int x = 0; x < width; x++) {
                    int bottomPixel = img.getRGB(x, height - y - 1);
                    img.setRGB(x, y, bottomPixel);
                }
            }
        }
    }

    private void gifSymmetryByLeft(List<GifData> gifs) {
        for (GifData gif : gifs) symmetryByLeft(gif.getFrames());
    }

    private void gifSymmetryByRight(List<GifData> gifs) {
        for (GifData gif : gifs) symmetryByRight(gif.getFrames());
    }

    private void gifSymmetryByUp(List<GifData> gifs) {
        for (GifData gif : gifs) symmetryByUp(gif.getFrames());
    }

    private void gifSymmetryByDown(List<GifData> gifs) {
        for (GifData gif : gifs) symmetryByDown(gif.getFrames());
    }

    private void gifCutout(GifData gif, int threshold) {
        // 对每一帧单独执行 cutout 算法（与静态图片的处理逻辑对齐）
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
        List<BufferedImage> frames = gif.getFrames();
        for (int fi = 0; fi < frames.size(); fi++) {
            BufferedImage img = frames.get(fi);
            // 强制 ARGB
            if (img.getType() != BufferedImage.TYPE_INT_ARGB) {
                BufferedImage argbImg = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = argbImg.createGraphics();
                g.drawImage(img, 0, 0, null);
                g.dispose();
                frames.set(fi, argbImg);
                img = argbImg;
            }

            int width = img.getWidth();
            int height = img.getHeight();
            Map<Integer, Set<Long>> rgbSta = new HashMap<>();

            if (width < 6 || height < 6) {
                // 图片过小
                return;
            }

            // 1. 统计四个边缘上出现频次最高的颜色，每条边宽度硬编码 3
            for (int w = 0; w < width; w++) {
                for (int i = 0; i < 3; i++) {
                    rgbSta.computeIfAbsent(img.getRGB(w, i), k -> new HashSet<>()).add(((long) w << 32) | (i & 0xffffffffL));
                    rgbSta.computeIfAbsent(img.getRGB(w, height - i - 1), k -> new HashSet<>()).add(((long) w << 32) | ((height - i - 1) & 0xffffffffL));
                }
            }
            for (int h = 1; h < height - 1; h++) {
                for (int i = 0; i < 3; i++) {
                    rgbSta.computeIfAbsent(img.getRGB(i, h), k -> new HashSet<>()).add(((long) i << 32) | (h & 0xffffffffL));
                    rgbSta.computeIfAbsent(img.getRGB(width - i - 1, h), k -> new HashSet<>()).add(((long) (width - i - 1) << 32) | (h & 0xffffffffL));
                }
            }

            Set<Long> bgPixelIndex = rgbSta.values().stream()
                    .max(Comparator.comparingInt(Set::size))
                    .orElseThrow(() -> new InternalServerErrorException("rgbSta cannot be empty"));

            if (bgPixelIndex.isEmpty()) throw new InternalServerErrorException("bgPixelIndex cannot be empty");

            long aBgIndex = bgPixelIndex.iterator().next();
            int bgRgb = img.getRGB((int) (aBgIndex >>> 32), (int) aBgIndex);

            // 2. 初始化优先队列，将所有边缘上出现频次最高的颜色加入优先队列
            Set<Long> visited = new HashSet<>(bgPixelIndex);
            PriorityQueue<Pixel> pq = new PriorityQueue<>(Comparator.comparingDouble(Pixel::dist));
            for (long index : bgPixelIndex) {
                int x = (int) (index >>> 32);
                int y = (int) index;
                pq.offer(new Pixel(x, y, img.getRGB(x, y), 0));
            }

            // 3. BFS 抠图透明底
            while (!pq.isEmpty()) {
                Pixel p = pq.poll();
                int x = p.x();
                int y = p.y();

                if (p.dist() > threshold) continue;
                img.setRGB(x, y, p.rgb() & 0x00ffffff);

                for (int[] d : dirs) {
                    int nx = x + d[0];
                    int ny = y + d[1];
                    if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;

                    long idx = ((long) nx << 32) | (ny & 0xffffffffL);
                    if (visited.contains(idx)) continue;

                    visited.add(idx);
                    int nRgb = img.getRGB(nx, ny);
                    int dist = distance(bgRgb, nRgb);

                    pq.offer(new Pixel(nx, ny, nRgb, dist));
                }
            }
        }
    }

    private void gifGray(GifData gif) {
        List<BufferedImage> frames = gif.getFrames();
        for (int i = 0; i < frames.size(); i++) {
            BufferedImage img = frames.get(i);
            int width = img.getWidth();
            int height = img.getHeight();

            BufferedImage grayImg = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
            Graphics g = grayImg.getGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();

            // 转回 ARGB（writeGifToBytes 期望 ARGB）
            BufferedImage outImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = outImg.createGraphics();
            g2.drawImage(grayImg, 0, 0, null);
            g2.dispose();

            frames.set(i, outImg);
        }
    }

    private void gifMirror(GifData gif) {
        List<BufferedImage> frames = gif.getFrames();
        for (BufferedImage img : frames) {
            if (img.getType() != BufferedImage.TYPE_INT_ARGB) {
                BufferedImage argbImg = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = argbImg.createGraphics();
                g.drawImage(img, 0, 0, null);
                g.dispose();
                img = argbImg;
            }
            int width = img.getWidth();
            int height = img.getHeight();

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width / 2; x++) {
                    int leftPixel = img.getRGB(x, y);
                    int rightPixel = img.getRGB(width - x - 1, y);

                    img.setRGB(x, y, rightPixel);
                    img.setRGB(width - x - 1, y, leftPixel);
                }
            }
        }
    }

    private BufferedImage rotateImg(BufferedImage img, int angle) {
        int w = img.getWidth();
        int h = img.getHeight();
        BufferedImage dst;

        switch (angle) {
            case 90 -> {
                dst = new BufferedImage(h, w, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        dst.setRGB(h - 1 - y, x, img.getRGB(x, y));
                    }
                }
            }
            case 180 -> {
                dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        dst.setRGB(w - 1 - x, h - 1 - y, img.getRGB(x, y));
                    }
                }
            }
            case 270 -> {
                dst = new BufferedImage(h, w, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        dst.setRGB(y, w - 1 - x, img.getRGB(x, y));
                    }
                }
            }
            default -> throw new IllegalArgumentException("illegal angle argument");
        }

        return dst;
    }

    private void rotateImg(List<BufferedImage> imgs, int angle) {
        imgs.replaceAll(img -> rotateImg(img, angle));
    }

    private void rotateGif(GifData gif, int angle) {
        for (int i = 0; i < gif.getFrames().size(); i++) {
            BufferedImage frame = gif.getFrames().get(i);
            BufferedImage rotated = rotateImg(frame, angle);
            gif.getFrames().set(i, rotated);
        }
    }

    private void rotateGif(List<GifData> gifs, int angle) {
        for (GifData gif : gifs) {
            List<BufferedImage> frames = gif.getFrames();
            List<BufferedImage> rotated = new ArrayList<>(frames.size());
            for (BufferedImage frame : frames) {
                rotated.add(rotateImg(frame, angle));
            }
            gif.setFrames(rotated);
        }
    }

    private void invertImage(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgba = img.getRGB(x, y);
                Color col = new Color(rgba, true);
                int r = 255 - col.getRed();
                int g = 255 - col.getGreen();
                int b = 255 - col.getBlue();
                int a = col.getAlpha();
                img.setRGB(x, y, new Color(r, g, b, a).getRGB());
            }
        }
    }

    private void gifInvert(GifData gif) {
        for (BufferedImage frame : gif.getFrames()) {
            invertImage(frame);
        }
    }

    private void toType(ParsedPayloadDTO payload, String type) throws IOException {
        List<String> urls = imgNetworkService.extractImgUrls(payload, true);
        if (urls == null || urls.isEmpty()) return;
        List<BufferedImage> imgs = imgNetworkService.getBufferedImg(urls);

        List<String> uuids = new ArrayList<>();
        for (BufferedImage img : imgs) {
            uuids.add(imageCacheService.cacheImage(img, type));
        }

        List<String> cacheUrls = new ArrayList<>(uuids.size());
        for (String uuid : uuids) cacheUrls.add(apiPaths.buildImgUrl(type, uuid));
        if (!cacheUrls.isEmpty()) sender.sendImage(payload, cacheUrls);
    }
}
