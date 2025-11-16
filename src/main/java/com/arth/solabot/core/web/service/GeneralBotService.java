package com.arth.solabot.core.web.service;

import org.springframework.core.io.Resource;

import java.io.IOException;

public interface GeneralBotService {
    
    Resource getGalleryImgResource(String pid) throws IOException;
    
    Resource getGalleryThumbnailResource(String role) throws IOException;
}