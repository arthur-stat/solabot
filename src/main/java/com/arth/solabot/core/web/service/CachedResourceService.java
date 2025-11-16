package com.arth.solabot.core.web.service;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

public interface CachedResourceService {
    
    ResponseEntity<Resource> getPng(String uuid);
    
    ResponseEntity<Resource> getGif(String uuid);
}