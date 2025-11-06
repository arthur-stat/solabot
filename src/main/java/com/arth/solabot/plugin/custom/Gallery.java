package com.arth.solabot.plugin.custom;

import com.arth.solabot.adapter.controller.ApiPaths;
import com.arth.solabot.adapter.sender.Sender;
import com.arth.solabot.core.bot.dto.ParsedPayloadDTO;
import com.arth.solabot.core.bot.invoker.annotation.BotCommand;
import com.arth.solabot.core.bot.invoker.annotation.BotPlugin;
import com.arth.solabot.core.general.utils.FileUtils;
import com.arth.solabot.plugin.resource.LocalData;
import com.arth.solabot.plugin.resource.MemoryData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.web.reactive.function.client.WebClient;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

/**
 * 通过两个读写锁解决多线程竞态风险
 */
@Slf4j
@BotPlugin(value = {"看"}, glued = true)
@RequiredArgsConstructor
public class Gallery extends Plugin {

    @Value("${app.parameter.plugin.gallery.metadata-api}")
    private String METADATA_URL;
    @Value("${app.parameter.plugin.gallery.auth-token}")
    private String AUTH_TOKEN;
    @Value("${app.parameter.plugin.gallery.pic-api}")
    private String PIC_URL;
    @Value("${app.parameter.plugin.gallery.sync-delete}")
    private boolean SYNC_DELETE;  // 同步时是否也同步删除
    @Value("${app.parameter.plugin.gallery.update-ttl}")
    private int UPDATE_TTL;
    @Value("${app.parameter.plugin.gallery.update-cron}")
    private String UPDATE_CRON;

    /* 画廊普通图片写锁（该锁颗粒度极小，仅为了保证短时间内临界区资源的完整） */
    private static final ReentrantReadWriteLock imgLock = new ReentrantReadWriteLock();
    /* 画廊缩略预览图写锁（该锁作用于整个缩略图生成，但通过 Future 避免锁释放后被阻塞线程重复进行更新） */
    private static final ReentrantReadWriteLock thumbnailLock = new ReentrantReadWriteLock();
    private static volatile CompletableFuture<Void> updatingFuture;

    private Instant lastUpdateTime = null;
    private static Map<String, List<String>> roles = new HashMap<>();  // role → set(id)
    private static List<String> ids = new ArrayList<>();
    private static Map<String, FilePair> idToFile = new HashMap<>();   // id → (role, fileName)，全局扁平化、不区分 role
    private final Random rand = new Random();

    private final Sender sender;
    private final ApiPaths apiPaths;
    private final LocalData localData;
    private final FileUtils fileUtils;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final TaskScheduler taskScheduler;

    @Getter
    public final String helpText = """
            看看你的模块现支持随机看表情包/梗图：
              - 所有烤 oc，例如 saki、mnr
              - 所有 v，例如 miku、kaito，包括 teto
              - 看猪，鸟，夜鹭，西巴，kiwi，企鹅
            
            以上是紧凑命令，无需空格，使用方法示例是：/看miku
            
            也可以按 pid 查看，例如：/看912。
            
            如果需要查看各角色的id-缩略图，在 “看” 后加上 “所有” 即可，例如：/看所有miku
            
            "/看看"、"/看看你的" 则会在全角色内随机
            
            非紧凑命令（需要空格）：
              - update: 强制与图源进行本地增量同步
            
            图库来自luna茶""";

    @BotCommand("index")
    public void index(ParsedPayloadDTO payload, List<String> args) {
        tryUpdateGallery();

        for (String arg : args) {
            // 空字符
            if (arg == null || arg.isEmpty()) continue;

            // 全局随机
            if (arg.equals("看") || arg.equals("看你的")) {
                int idx = rand.nextInt(ids.size());
                String id = ids.get(idx);
                sender.sendImage(payload, apiPaths.buildGalleryImgUrl(id));
                continue;
            }

            // 缩略图
            if (arg.length() >= 2 && (arg.startsWith("所有") || arg.startsWith("全部"))) {
                String role = MemoryData.alias.get(arg.substring(2));
                sender.sendImage(payload, apiPaths.buildGalleryThumbnailUrl(role));
                continue;
            }

            // 如果输入是纯数字，视为 pid
            if (arg.matches("\\d+")) {
                sender.sendImage(payload, apiPaths.buildGalleryImgUrl(arg));
                continue;
            }

            // 否则解释为别名
            String role = MemoryData.alias.get(arg);
            if (role != null) {
                List<String> idsForRole = roles.get(role);
                if (idsForRole != null) {
                    int idx = rand.nextInt(idsForRole.size());
                    String id = idsForRole.get(idx);
                    sender.sendImage(payload, apiPaths.buildGalleryImgUrl(id));
                }
            }
        }
    }

    @Override
    public void index(ParsedPayloadDTO payload) {
    }

    @Override
    @BotCommand("help")
    public void help(ParsedPayloadDTO payload) {
        super.help(payload);
    }

    @Override
    public void registerTask() {
        Runnable task = () -> {
            try {
                int count = updateGalleryImgsWithLock();
                updateMapsAndDrawThumbnailsWithLock();
                log.info("[plugin.gallery] scheduled task completed successfully, updated {} images", count);
            } catch (Exception e) {
                log.error("[plugin.gallery] scheduled task failed", e);
            }
        };

        taskScheduler.schedule(task, new CronTrigger(UPDATE_CRON));

        log.info("[plugin.gallery] scheduled task registered for daily update at 4:00 AM");
    }

    @BotCommand({"update", "sync", "刷新", "更新", "同步"})
    public void update(ParsedPayloadDTO payload) {
        try {
            int count = updateGalleryImgsWithLock();
            updateMapsAndDrawThumbnailsWithLock();
            sender.replyText(payload, "已同步至最新图库……更新了" + count + "张图片");
        } catch (Exception e) {
            sender.replyText(payload, "同步图库失败：" + e.getCause());
        }
    }


    // ***** ============= helper ============= *****
    // ***** ============= helper ============= *****
    // ***** ============= helper ============= *****


    public static FilePair getFilePairByPidWithLock(String pid) {
        imgLock.readLock().lock();
        try {
            return idToFile.get(pid);
        } catch (Exception e) {
            log.error("[plugin.gallery] concurrency error");
            throw e;
        } finally {
            imgLock.readLock().unlock();
        }
    }

    public static Resource getThumbnailWithLock(Path path) {
        thumbnailLock.readLock().lock();
        try {
            return new PathResource(path);
        } catch (Exception e) {
            log.error("[plugin.gallery] concurrency error");
            throw e;
        } finally {
            thumbnailLock.readLock().unlock();
        }
    }

    private void tryUpdateGallery() {
        if (lastUpdateTime == null || Duration.between(lastUpdateTime, Instant.now()).toHours() > UPDATE_TTL) {
            updateGalleryImgsWithLock();
            updateMapsAndDrawThumbnailsWithLock();
        }
    }

    private int updateGalleryImgsWithLock() {
        log.info("[plugin.gallery] start syncing gallery...");
        int count = 0;

        // 依 idToFile 作为判断图片是否已否存在的标准，如果 idToFile 尚未初始化，则先遍历一次本地路径构建缓存
        if (idToFile == null || idToFile.isEmpty()) {
            updateMapsAndDrawThumbnailsWithLock();
        }

        try {
            String jsonResponse = webClient.get()
                    .uri(METADATA_URL)
                    .headers(headers -> headers.setBearerAuth(AUTH_TOKEN))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            ObjectNode metadata = (ObjectNode) objectMapper.readTree(jsonResponse);

            for (Map.Entry<String, JsonNode> entry : metadata.properties()) {
                String roleName = entry.getKey();
                JsonNode roleNode = entry.getValue();

                JsonNode picsArr = roleNode.get("pics");
                if (picsArr != null && picsArr.isArray()) {

                    // 如果 SYNC_DELETE=true，则需要考虑删除已不存在于最新元数据集合中的图片文件（后续更新映射表是以文件为依据的）
                    Set<String> newIdsForRole = null;
                    if (SYNC_DELETE && roles != null && roles.get(roleName) != null) {
                        newIdsForRole = new HashSet<>(roles.get(roleName).size());
                    }

                    for (JsonNode pic : picsArr) {
                        String pid = pic.get("pid").asText();

                        if (newIdsForRole != null) {
                            newIdsForRole.add(pid);
                        }

                        boolean shouldDownload;
                        imgLock.writeLock().lock();
                        try {
                            shouldDownload = !idToFile.containsKey(pid);
                        } catch (Exception e) {
                            log.error("[plugin.gallery] concurrency error", e);
                            throw e;
                        } finally {
                            imgLock.writeLock().unlock();
                        }

                        if (!shouldDownload) continue;

                        String url = PIC_URL.replace("{path}", pic.get("path").asText());
                        Path saveDir = localData.getGalleryRolePath(roleName);

                        CompletableFuture<String> future = fileUtils.downloadImageAsync(url, saveDir, pid);
                        String fileName = future.get();
                        log.debug("[plugin.gallery] downloaded image {}", fileName);

                        imgLock.writeLock().lock();
                        try {
                            if (!idToFile.containsKey(pid)) {
                                idToFile.put(pid, new FilePair(roleName, fileName));
                                count++;
                            }
                        } catch (Exception e) {
                            log.error("[plugin.gallery] concurrency error", e);
                            throw e;
                        } finally {
                            imgLock.writeLock().unlock();
                        }
                    }

                    if (newIdsForRole != null) {
                        HashSet<String> oldIdsForRole = new HashSet<>(roles.get(roleName));
                        oldIdsForRole.removeAll(newIdsForRole);
                        // 此处不需要更新映射表，后续通过遍历文件进行统一更新
                        for (String removeId : oldIdsForRole) {
                            Path path = localData.getGalleryImgPath(removeId);
                            fileUtils.tryDeleteFile(path);
                        }
                        log.debug("[plugin.gallery] deleted {} expired images for role \"{}\"", oldIdsForRole.size(), roleName);
                    }
                }
            }
            log.info("[plugin.gallery] successfully synced {} images", count);
            lastUpdateTime = Instant.now();
            return count;
        } catch (Exception e) {
            log.error("[plugin.gallery] failed to sync", e);
            return -1;
        }
    }

    /**
     * 更新映射表，同时绘制各角色缩略图（有锁，且通过 Future 避免重复执行）
     */
    private void updateMapsAndDrawThumbnailsWithLock() {
        CompletableFuture<Void> currentFuture = updatingFuture;

        // 如果其他线程已持有缩略图锁（正在更新），则等待其更新完成后直接返回，不重复尝试更新
        if (currentFuture != null) {
            currentFuture.join();
            return;
        }

        // 尝试获取锁
        if (thumbnailLock.writeLock().tryLock()) {
            CompletableFuture<Void> future = null;
            try {
                // 创建新 Future 实例
                future = new CompletableFuture<>();
                updatingFuture = future;

                // 执行更新逻辑
                updateMapsAndDrawThumbnails();
                // 标记完成
                future.complete(null);

            } catch (Exception e) {
                if (future != null) future.completeExceptionally(e);
                log.error("[plugin.gallery] concurrency error");
                throw e;
            } finally {
                updatingFuture = null;
                thumbnailLock.writeLock().unlock();
            }
        } else {
            // 双重检查（未能获取到锁，等待更新完成）
            CompletableFuture<Void> waitingFuture = updatingFuture;
            if (waitingFuture != null) {
                waitingFuture.join();
            }
        }
    }

    /**
     * 更新映射表，同时绘制各角色缩略图（无锁）
     * 通过遍历本地文件，同时更新角色映射表、文件名映射表并绘制缩略图（如果无更新则跳过绘制）（即新映射的依据是文件）
     */
    private void updateMapsAndDrawThumbnails() {
        Map<String, List<String>> newRoles = new HashMap<>((roles != null) ? roles.size() : 16);
        Map<String, FilePair> newIdToFile = new HashMap<>((idToFile != null) ? idToFile.size() : 16);

        try {
            Files.createDirectories(LocalData.GALLERY_THUMBNAILS_BASE);
        } catch (IOException e) {
            log.error("[plugin.gallery] failed to create thumbnail directory");
        }

        try (Stream<Path> roleDirs = Files.list(LocalData.GALLERY_IMG_DIR)) {
            roleDirs.filter(Files::isDirectory).forEach(roleDir -> {
                // 出于性能考虑的一些初始化逻辑（设置合适的初始容量）
                String roleName = roleDir.getFileName().toString();
                int mapSize = 16;
                if (roles != null) {
                    List<String> oldIdSet = roles.get(roleName);
                    if (oldIdSet != null) {
                        mapSize = oldIdSet.size();
                    }
                }
                Map<String, String> idToFileOfRole = new HashMap<>(mapSize);

                // 遍历角色对应的图库，更新临时映射表
                try (Stream<Path> files = Files.list(roleDir)) {
                    files.filter(Files::isRegularFile).forEach(file -> {
                        String fileName = file.getFileName().toString();
                        String id = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
                        idToFileOfRole.put(id, fileName);
                        newIdToFile.put(id, new FilePair(roleName, fileName));
                    });
                } catch (IOException e) {
                    log.error("[plugin.gallery] failed to list files for role: {}", roleName, e);
                }

                // 当且仅当缩略图的 id 集合较之旧版本发生变化时才考虑更新缩略图（图片太多时，绘制缩略图比较花时间）
                if (roles == null || roles.get(roleName) == null || !idToFileOfRole.keySet().equals(new HashSet<>(roles.get(roleName)))) {
                    newRoles.put(roleName, new ArrayList<>(idToFileOfRole.keySet()));

                    try {
                        int maxPerRow = 15;     // 每行最多 15 张图
                        int thumbWidth = 120;   // 缩略图最大宽度
                        int thumbHeight = 120;  // 缩略图最大高度
                        int labelHeight = 20;   // id标签高度
                        int margin = 10;        // 间距

                        int imageCount = idToFileOfRole.size();
                        int rowCount = (int) Math.ceil(imageCount / (double) maxPerRow);

                        int canvasWidth = maxPerRow * (thumbWidth + margin) + margin;
                        int canvasHeight = rowCount * (thumbHeight + labelHeight + margin) + margin;

                        BufferedImage gallery = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_RGB);
                        Graphics2D g = gallery.createGraphics();
                        g.setColor(Color.WHITE);
                        g.fillRect(0, 0, canvasWidth, canvasHeight);
                        g.setColor(Color.BLACK);
                        g.setFont(new Font("SansSerif", Font.PLAIN, 12));

                        AtomicInteger index = new AtomicInteger(0);
                        idToFileOfRole.forEach((id, fileName) -> {
                            try {
                                Path file = roleDir.resolve(fileName);
                                BufferedImage img = ImageIO.read(file.toFile());
                                if (img == null) return;  // skip if unreadable

                                int w = img.getWidth();
                                int h = img.getHeight();

                                double scale = Math.min((double) thumbWidth / w, (double) thumbHeight / h);
                                int scaledW = (int) (w * scale);
                                int scaledH = (int) (h * scale);

                                int xIndex = index.get() % maxPerRow;
                                int yIndex = index.get() / maxPerRow;
                                int x = margin + xIndex * (thumbWidth + margin);
                                int y = margin + yIndex * (thumbHeight + labelHeight + margin);

                                g.drawImage(img, x + (thumbWidth - scaledW) / 2, y + (thumbHeight - scaledH) / 2, scaledW, scaledH, null);
                                g.drawString(id, x + (thumbWidth - g.getFontMetrics().stringWidth(id)) / 2, y + thumbHeight + 15);
                                index.incrementAndGet();
                            } catch (Exception e) {
                                log.warn("[plugin.gallery] failed to process image for role {} id {}", roleName, id, e);
                            }
                        });

                        g.dispose();
                        Path outFile = LocalData.GALLERY_THUMBNAILS_BASE.resolve(roleName + ".png");
                        ImageIO.write(gallery, "png", outFile.toFile());
                        log.debug("[plugin.gallery] thumbnail generated: {}", outFile);

                    } catch (IOException e) {
                        log.error("[plugin.gallery] failed to generate thumbnail for role {}", roleName, e);
                    }
                }
            });
        } catch (IOException e) {
            log.error("[plugin.gallery] failed to update roles from local directory", e);
            return;
        }

        roles = newRoles;
        idToFile = newIdToFile;
        ids = new ArrayList<>(idToFile.keySet());
        log.info("[plugin.gallery] all roles updated and thumbnails generated, found {} imgs", idToFile.size());
    }

    public record FilePair(String roleName, String fileName) {
    }
}