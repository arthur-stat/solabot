package com.arth.solabot.adapter.controller.http;

import com.arth.solabot.adapter.controller.ApiPaths;
import com.arth.solabot.adapter.controller.http.dto.ApiResponse;
import com.arth.solabot.adapter.utils.NetworkUtils;
import com.arth.solabot.core.web.UserFileHandler;
import com.arth.solabot.plugin.resource.LocalData;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PjskPluginController {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final LocalData localData;
    private final UserFileHandler userFileHandler;

    /**
     * Mysekai 透视 map 请求
     *
     * @param region
     * @param id
     * @return
     * @throws IOException
     */
    @GetMapping(ApiPaths.PJSK_MYSEKAI_MAP)
    public ResponseEntity<Resource> getMysekaiMap(@PathVariable String region, @PathVariable String id) throws IOException {
        Resource resource = localData.resolveMysekaiResourcePath(LocalData.PJSK_MYSEKAI_MAP, region, id);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(resource);
    }


    /**
     * Mysekai 透视 overview 请求
     *
     * @param region
     * @param id
     * @return
     * @throws IOException
     */
    @GetMapping(ApiPaths.PJSK_MYSEKAI_OVERVIEW)
    public ResponseEntity<Resource> getMysekaiOverview(@PathVariable String region, @PathVariable String id) throws IOException {
        Resource resource = localData.resolveMysekaiResourcePath(LocalData.PJSK_MYSEKAI_OVERVIEW, region, id);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(resource);
    }

    /**
     * Shadowrocket 模块下载请求：国服 Mysekai
     *
     * @return
     * @throws IOException
     */
    @GetMapping(ApiPaths.SHADOWROCKET_MODULE_DOWNLOAD_MYSEKAI_CN)
    public ResponseEntity<Resource> getShadowrocketModuleForCnMysekai() {
        try {
            Resource resource = new PathResource(LocalData.SHADOWROCKET_MODULE_DOWNLOAD_MYSEKAI_CN);
            if (!resource.exists()) return ResponseEntity.notFound().build();

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/plain; charset=utf-8"))
                    .header("Content-Disposition", "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 模块重定向至此处，后端负责向游戏服务器请求数据
     *
     * @param request
     * @param original
     * @return
     * @throws IOException
     */
    @RequestMapping(path = ApiPaths.MYSEKAI_UPLOAD_PROXY, method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Resource> proxyUpload(
            HttpServletRequest request,
            @RequestParam("original") String original
    ) throws IOException {
        final byte[] reqBody = NetworkUtils.readBody(request);
        ResponseEntity<Resource> upstream = NetworkUtils.proxyRequest(webClient, original, request, reqBody);
        log.debug("[adapter.http] proxied successfully status={}", upstream.getStatusCode().value());
        byte[] tmp = new byte[0];
        if (upstream.getBody() instanceof ByteArrayResource bar) {
            tmp = bar.getByteArray();
        } else if (upstream.getBody() != null) {
            try (InputStream is = upstream.getBody().getInputStream()) {
                tmp = is.readAllBytes();
            }
        }
        final byte[] upstreamBytes = tmp;
        MediaType ct = upstream.getHeaders().getContentType();
        if (ct == null) ct = MediaType.APPLICATION_OCTET_STREAM;
        final MediaType contentType = ct;
        webClient.post()
                .uri("http://localhost:8849/upload")
                .contentType(contentType)
                .header("X-Original-Url", original)
                .header("X-Upstream-Status", String.valueOf(upstream.getStatusCode().value()))
                .bodyValue(upstreamBytes)
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(e -> log.error("[adapter.http] async request failed", e))
                .subscribe(resp -> log.info("[adapter.http] python resp: {}", resp));

        return upstream;
    }

    /**
     * 前端返回格式：MultipartFile file（文件）  String filetype（文件类型，suite或mysekai）   String region（游戏区服）
     * 后端返回格式(JSON)：
     * {
     * "success" : 布尔值,
     * "message" : 字符串，消息
     * }
     *
     * @param file
     * @param filetype
     * @param region
     * @return
     * @throws IOException
     */
    @PostMapping(ApiPaths.PJSK_WEB_UPLOAD)
    public ApiResponse<String> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("filetype") String filetype,
            @RequestParam("region") String region
    ) throws IOException {

        byte[] body = file.getBytes();
        ApiResponse<String> response = new ApiResponse<>();
        switch (filetype) {
            case "mysekai":
                break;
            case "suite":
                response = userFileHandler.handleUploadedSuite(body, region);
                break;
            default:
                throw new IllegalArgumentException("未知的 filetype: " + filetype);
        }
        return response;
        //return ApiResponse.success("上传成功");
    }  //TODO:与前端对接
}
