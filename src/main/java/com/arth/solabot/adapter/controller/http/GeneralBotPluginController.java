package com.arth.solabot.adapter.controller.http;

import com.arth.solabot.adapter.controller.ApiPaths;
import com.arth.solabot.core.infrastructure.LocalData;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequiredArgsConstructor
public class GeneralBotPluginController {

    private final LocalData localData;

    /**
     * 插件 Gallery 的普通图像请求
     *
     * @param pid
     * @return
     * @throws IOException
     */
    @GetMapping(ApiPaths.GALLERY_IMG)
    public ResponseEntity<Resource> getGalleryImg(@PathVariable String pid) throws IOException {
        Resource resource = localData.getGalleryImgResource(pid);
        Path path = resource.getFile().toPath();

        String contentType = Files.probeContentType(path);
        if (contentType == null) contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    /**
     * 插件 Gallery 的缩略图请求
     *
     * @param role
     * @return
     * @throws IOException
     */
    @GetMapping(ApiPaths.GALLERY_THUMBNAILS)
    public ResponseEntity<Resource> getGalleryThumbnail(@PathVariable String role) throws IOException {
        Resource resource = localData.getGalleryThumbnailResource(role);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(resource);
    }
}
