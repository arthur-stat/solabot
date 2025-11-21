package com.arth.solabot.core.infrastructure.network;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class NetworkUtil {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    // 常见文件类型响应头到后缀名的映射
    private static final Map<String, String> EXT_MAP = Map.of(
            "application/json", ".json",
            "application/xml", ".xml",
            "application/pdf", ".pdf",
            "application/zip", ".zip",
            "application/octet-stream", ".bin",
            MediaType.IMAGE_JPEG_VALUE, ".jpg",
            MediaType.IMAGE_PNG_VALUE, ".png",
            MediaType.IMAGE_GIF_VALUE, ".gif",
            "image/webp", ".webp",
            "image/svg+xml", ".svg"
    );

    // 常见浏览器请求头
    private static final Map<String, String> BROWSER_HEADERS = Map.of(
            "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
            "Accept", "application/json, text/plain, */*",
            "Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8"
    );


    /**
     * 同步阻塞 GET 请求字符串响应的通用方法
     *
     * @param url
     * @param headers
     * @param timeout
     * @return
     */
    public String getString(String url, Map<String, String> headers, Duration timeout) {
        log.debug("GET request to url: {}", url);

        var request = webClient.get().uri(url);

        // 设置 headers
        if (headers != null) {
            request = request.headers(h -> headers.forEach(h::add));
        }
        WebClient.ResponseSpec responseSpec = request.retrieve();

        // 通用错误处理
        responseSpec = responseSpec.onStatus(
                HttpStatusCode::isError,
                response -> response.bodyToMono(String.class)
                        .flatMap(body -> Mono.error(new IOException(
                                "request failed with status code: " + response.statusCode() + "\n" + body)))
        );

        Mono<String> result = responseSpec.bodyToMono(String.class);

        // 设置超时
        if (timeout != null) {
            result = result.timeout(timeout);
        }

        return result.doOnError(e -> log.error("GET request failed for url: {}", url, e))
                .doOnSuccess(resp -> log.debug("GET response for url {}: {}", url, resp))
                .block();
    }


    /**
     * 带有浏览器请求头的同步阻塞 GET 响应字符串的简单方法
     *
     * @param url
     * @param timeout
     * @return
     */
    public String getStringWithBrowserHeaders(String url, Duration timeout) {
        return getString(url, BROWSER_HEADERS, timeout);
    }


    /**
     * 带有 auth token 的同步阻塞 GET 请求字符串响应的简单方法
     *
     * @param url
     * @param token
     * @return
     */
    public String getStringWithAuthToken(String url, String token) {
        return getString(url, Map.of("Authorization", "Bearer " + token), null);
    }


    /**
     * 同步阻塞 GET 响应 json 的通用方法
     *
     * @param url
     * @param headers
     * @param timeout
     * @return
     */
    public JsonNode getJsonNode(String url, Map<String, String> headers, Duration timeout) {
        String jsonString = getString(url, headers, timeout);
        try {
            return objectMapper.readTree(jsonString);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON response", e);
        }
    }


    /**
     * 带有浏览器请求头的同步阻塞 GET 响应 json 的简单方法
     *
     * @param url
     * @param timeout
     * @return
     */
    public JsonNode getJsonNodeWithBrowserHeaders(String url, Duration timeout) {
        return getJsonNode(url, BROWSER_HEADERS, timeout);
    }


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
     * 异步请求的通用方法 fire-and-forget
     *
     * @param method
     * @param url
     * @param contentType
     * @param body
     * @param headers
     * @return
     */
    public void asyncRequest(HttpMethod method,
                                     String url,
                                     MediaType contentType,
                                     Object body,
                                     Map<String, String> headers) {
        webClient.method(method)
                .uri(url)
                .headers(h -> headers.forEach(h::add))
                .contentType(contentType)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(e -> log.error("async request failed for url: {}", url, e))
                .doOnSuccess(resp -> log.debug("async response for url {}: {}", url, resp))
                .subscribe();
    }


    /**
     * 异步 POST 请求的通用方法 fire-and-forget
     *
     * @param targetUri
     * @param contentType
     * @param body
     * @param headers
     * @return
     */
    public void asyncPost(String targetUri,
                                  MediaType contentType,
                                  Object body,
                                  Map<String, String> headers) {
        asyncRequest(HttpMethod.POST, targetUri, contentType, body, headers);
    }
}