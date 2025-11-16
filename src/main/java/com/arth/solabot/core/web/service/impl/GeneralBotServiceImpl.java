package com.arth.solabot.core.web.service.impl;

import com.arth.solabot.core.infrastructure.LocalData;
import com.arth.solabot.core.web.service.GeneralBotService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class GeneralBotServiceImpl implements GeneralBotService {
    
    private final LocalData localData;
    
    @Override
    public Resource getGalleryImgResource(String pid) throws IOException {
        return localData.getGalleryImgResource(pid);
    }
    
    @Override
    public Resource getGalleryThumbnailResource(String role) throws IOException {
        return localData.getGalleryThumbnailResource(role);
    }
}