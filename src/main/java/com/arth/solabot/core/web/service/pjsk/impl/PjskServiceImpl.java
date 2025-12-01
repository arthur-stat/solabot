package com.arth.solabot.core.web.service.pjsk.impl;

import com.arth.solabot.adapter.controller.http.dto.ResponseDTO;
import com.arth.solabot.core.infrastructure.LocalData;
import com.arth.solabot.core.infrastructure.exception.InternalServerErrorException;
import com.arth.solabot.core.infrastructure.exception.ResourceNotFoundException;
import com.arth.solabot.core.infrastructure.network.NetworkUtil;
import com.arth.solabot.core.infrastructure.network.service.HttpProxyService;
import com.arth.solabot.core.web.service.pjsk.PjskService;
import com.arth.solabot.core.web.utils.PlayerDataDecryptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PjskServiceImpl implements PjskService {

    private final HttpProxyService httpProxyService;
    private final LocalData localData;
    private final NetworkUtil networkUtil;
    private final ObjectMapper objectMapper;

    @Override
    public Resource getMysekaiMap(String region, String id) throws IOException {
        return localData.resolveMysekaiResourcePath(LocalData.PJSK_MYSEKAI_MAP, region, id);
    }

    @Override
    public Resource getMysekaiOverview(String region, String id) throws IOException {
        return localData.resolveMysekaiResourcePath(LocalData.PJSK_MYSEKAI_OVERVIEW, region, id);
    }

    @Override
    public Resource getShadowrocketModuleForCnMysekai() {
        Resource resource = new PathResource(LocalData.SHADOWROCKET_MODULE_DOWNLOAD_MYSEKAI_CN);
        if (!resource.exists()) throw new ResourceNotFoundException("module not found", "模块未找到");
        return resource;
    }

    @Override
    public ResponseEntity<Resource> proxyUpload(HttpServletRequest request, String original) throws IOException {
        final byte[] reqBody = httpProxyService.readBody(request);
        ResponseEntity<Resource> upstream = httpProxyService.proxyRequest(original, request, reqBody);
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

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Original-Url", original);
        headers.put("X-Upstream-Status", String.valueOf(upstream.getStatusCode().value()));
        networkUtil.asyncPost("http://localhost:8849/upload", contentType, upstreamBytes, headers);

        return upstream;
    }

    @Override
    public Resource handleSuiteUpload(MultipartFile file, String region) throws IOException {
        byte[] body = file.getBytes();
        ResponseDTO<String> response = new ResponseDTO<>();
        JsonNode node = objectMapper.readTree(body);
        try {
            node = objectMapper.readTree(body);
        } catch (Exception e) {
            //log.error(e.getMessage(),e);
            try {
                node = PlayerDataDecryptor.forRegion(objectMapper,PlayerDataDecryptor.Region.valueOf(region.toUpperCase()))
                        .decrypt(body)
                        .toJsonNode();
            } catch (Exception err) {
                throw new InternalServerErrorException("wtf");
            }
        }

        String playerId = node.get("userGamedata").get("userId").asText();
        Path suitePath = localData.getSuitePath(region,playerId);
        switch (region) {
            case "cn" -> Files.createDirectories(LocalData.PJSK_SUITE_CN.toAbsolutePath());
            case "jp" -> Files.createDirectories(LocalData.PJSK_SUITE_JP.toAbsolutePath());
            case "tw" -> Files.createDirectories(LocalData.PJSK_SUITE_TW.toAbsolutePath());
        }

        Files.writeString(suitePath, objectMapper.writeValueAsString(node));
        return null;
    }
    
    @Override
    public Resource handleMysekaiUpload(MultipartFile file, String region, String gameId) throws IOException {
        byte[] fileData = file.getBytes();

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Original-Url", "https://mkcn-prod-public-60001-1.dailygn.com/api/user/" + gameId + "/mysekai?isForceAllReloadOnlyMysekai=True");
        headers.put("X-Upstream-Status", "200");

        networkUtil.asyncPost(
            "http://localhost:8849/upload", 
            MediaType.APPLICATION_OCTET_STREAM, 
            fileData, 
            headers
        );

        return null;
    }
}