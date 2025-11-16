package com.arth.solabot.adapter.controller.http;

import com.arth.solabot.adapter.controller.ApiPaths;
import com.arth.solabot.adapter.controller.http.advice.UnwrapData;
import com.arth.solabot.adapter.controller.http.dto.ResponseDTO;
import com.arth.solabot.core.web.service.CachedResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CachedResourceController {

    private final CachedResourceService cachedResourceService;

    /**
     * Redis PNG 图片缓存
     *
     * @param uuid
     * @return
     */
    @UnwrapData
    @GetMapping(ApiPaths.CACHE_IMG_PNG)
    public ResponseEntity<ResponseDTO<Resource>> getPng(@PathVariable String uuid) {
        return ResponseDTO.fromEntity(cachedResourceService.getPng(uuid));
    }

    /**
     * Redis GIF 图片缓存
     *
     * @param uuid
     * @return
     */
    @UnwrapData
    @GetMapping(ApiPaths.CACHE_IMG_GIF)
    public ResponseEntity<ResponseDTO<Resource>> getGif(@PathVariable String uuid) {
        return ResponseDTO.fromEntity(cachedResourceService.getGif(uuid));
    }
}