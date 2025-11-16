package com.arth.solabot.adapter.controller.http;

import com.arth.solabot.adapter.controller.ApiPaths;
import com.arth.solabot.adapter.controller.http.advice.UnwrapData;
import com.arth.solabot.adapter.controller.http.dto.ResponseDTO;
import com.arth.solabot.core.web.service.GeneralBotService;
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

    private final GeneralBotService generalBotService;

    /**
     * 插件 Gallery 的普通图像请求
     *
     * @param pid
     * @return
     * @throws IOException
     */
    @UnwrapData
    @GetMapping(ApiPaths.GALLERY_IMG)
    public ResponseEntity<ResponseDTO<Resource>> getGalleryImg(@PathVariable String pid) throws IOException {
        Resource resource = generalBotService.getGalleryImgResource(pid);
        Path path = resource.getFile().toPath();

        String contentType = Files.probeContentType(path);
        if (contentType == null) contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(ResponseDTO.success(resource));
    }

    /**
     * 插件 Gallery 的缩略图请求
     *
     * @param role
     * @return
     * @throws IOException
     */
    @UnwrapData
    @GetMapping(ApiPaths.GALLERY_THUMBNAILS)
    public ResponseEntity<ResponseDTO<Resource>> getGalleryThumbnail(@PathVariable String role) throws IOException {
        Resource resource = generalBotService.getGalleryThumbnailResource(role);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(ResponseDTO.success(resource));
    }
}
