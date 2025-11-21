package com.arth.solabot.core.web.service.pjsk;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface PjskService {

    Resource getMysekaiMap(String region, String id) throws IOException;

    Resource getMysekaiOverview(String region, String id) throws IOException;

    Resource getShadowrocketModuleForCnMysekai();

    Resource handleSuiteUpload(MultipartFile file, String region) throws IOException;

    Resource handleMysekaiUpload(MultipartFile file, String region, String gameId) throws IOException;

    ResponseEntity<Resource> proxyUpload(HttpServletRequest request, String original) throws IOException;
}