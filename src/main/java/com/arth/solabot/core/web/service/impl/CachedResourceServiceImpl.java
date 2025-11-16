package com.arth.solabot.core.web.service.impl;

import com.arth.solabot.core.infrastructure.exception.BadRequestException;
import com.arth.solabot.core.infrastructure.exception.ResourceNotFoundException;
import com.arth.solabot.core.web.service.CachedResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CachedResourceServiceImpl implements CachedResourceService {
    
    private final RedisTemplate<String, byte[]> redisTemplate;
    
    @Override
    public ResponseEntity<Resource> getPng(String uuid) {
        // 400: 校验 uuid 格式
        if (!uuid.matches("[a-zA-Z0-9_-]+")) {
            throw new BadRequestException("invalid uuid", "无效的 uuid");
        }
        
        String key = "temp:image:png:" + uuid;
        byte[] imageBytes = redisTemplate.opsForValue().get(key);
        
        // 404
        if (imageBytes == null || imageBytes.length == 0) {
            throw new ResourceNotFoundException("not found", "资源未找到");
        }
        
        // 200
        ByteArrayResource resource = new ByteArrayResource(imageBytes);
        String contentDisposition = "inline; filename=\"" + uuid + ".png\"";
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header("Cache-Control", "max-age=180")
                .header("Content-Disposition", contentDisposition)
                .body(resource);
    }
    
    @Override
    public ResponseEntity<Resource> getGif(String uuid) {
        // 400: 校验 uuid 格式
        if (!uuid.matches("[a-zA-Z0-9_-]+")) {
            throw new BadRequestException("invalid uuid", "无效的 uuid");
        }
        
        String key = "temp:image:gif:" + uuid;
        byte[] imageBytes = redisTemplate.opsForValue().get(key);
        
        // 404
        if (imageBytes == null || imageBytes.length == 0) {
            throw new ResourceNotFoundException("not found", "资源未找到");
        }
        
        // 200
        ByteArrayResource resource = new ByteArrayResource(imageBytes);
        String contentDisposition = "inline; filename=\"" + uuid + ".gif\"";
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_GIF)
                .header("Cache-Control", "max-age=180")
                .header("Content-Disposition", contentDisposition)
                .body(resource);
    }
}