package com.arth.solabot.core.web.service.pjsk;

import com.arth.solabot.adapter.controller.http.dto.ResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface PjskPluginService {

    Resource getMysekaiMap(String region, String id) throws IOException;

    Resource getMysekaiOverview(String region, String id) throws IOException;

    Resource getShadowrocketModuleForCnMysekai();

    ResponseEntity<Resource> proxyUpload(HttpServletRequest request, String original) throws IOException;

    ResponseDTO<String> handleUpload(MultipartFile file, String filetype, String region) throws IOException;
}