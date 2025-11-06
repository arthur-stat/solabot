package com.arth.solabot.core.general.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class FileUtils {

    private final WebClient webClient;

    // 常见图片类型响应头到后缀名的映射
    private static final Map<String, String> EXT_MAP = Map.of(
            MediaType.IMAGE_PNG_VALUE, ".png",
            MediaType.IMAGE_JPEG_VALUE, ".jpg",
            "image/webp", ".webp",
            MediaType.IMAGE_GIF_VALUE, ".gif"
    );

    /**
     * 从 url 下载图片并保存到指定路径，需指定基本文件名（无后缀），返回文件名（带后缀）
     *
     * @param url
     * @param saveDir
     * @param baseName
     * @return
     */
    public CompletableFuture<String> downloadImageAsync(String url, Path saveDir, String baseName) {
        return webClient.get()
                .uri(url)
//                .headers(h -> {
//                    if (authToken != null && !authToken.isEmpty()) {
//                        h.setBearerAuth(authToken);
//                    }
//                })
                .retrieve()
                .toEntity(byte[].class)
                .flatMap(entity -> {
                    var contentType = entity.getHeaders().getContentType();
                    String extension = EXT_MAP.getOrDefault(
                            contentType != null ? contentType.toString() : "",
                            ".bin"
                    );
                    byte[] body = entity.getBody();
                    if (body == null) {
                        return Mono.error(new IllegalStateException("Empty response body"));
                    }

                    String fileName = baseName + extension;
                    Path savePath = saveDir.resolve(fileName);

                    return Mono.fromRunnable(() -> {
                                try {
                                    Files.createDirectories(saveDir);
                                    Files.write(savePath, body);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            })
                            .subscribeOn(Schedulers.boundedElastic())
                            .thenReturn(fileName);
                })
                .toFuture();
    }

    /**
     * 尝试删除文件，如果删除成功则返回 true，删除失败、路径指向目录、文件不存在则返回 false
     *
     * @param path
     * @return
     */
    public boolean tryDeleteFile(Path path) {
        if (path == null) return false;

        try {
            if (!Files.exists(path) || !Files.isRegularFile(path)) return false;
            Files.delete(path);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 文件不存在时自动创建，文件已存在时获取该文件
     *
     * @param path
     * @throws IOException
     */
    public static File getOrCreateFile(Path path) throws IOException {
        if (Files.notExists(path)) {
            Files.createFile(path);
        }
        return path.toFile();
    }
}
