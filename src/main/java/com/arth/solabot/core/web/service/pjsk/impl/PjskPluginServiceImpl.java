package com.arth.solabot.core.web.service.pjsk.impl;

import com.arth.solabot.adapter.controller.http.dto.ResponseDTO;
import com.arth.solabot.core.infrastructure.LocalData;
import com.arth.solabot.core.infrastructure.exception.BadRequestException;
import com.arth.solabot.core.infrastructure.exception.ResourceNotFoundException;
import com.arth.solabot.core.infrastructure.network.NetworkUtil;
import com.arth.solabot.core.infrastructure.network.service.HttpProxyService;
import com.arth.solabot.core.web.UserFileHandler;
import com.arth.solabot.core.web.service.pjsk.PjskPluginService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PjskPluginServiceImpl implements PjskPluginService {

    private final HttpProxyService httpProxyService;
    private final LocalData localData;
    private final UserFileHandler userFileHandler;
    private final NetworkUtil networkUtil;

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
    public ProxyResponse proxyUpload(HttpServletRequest request, String original) throws IOException {
        final byte[] reqBody = httpProxyService.readBody(request);
        var upstream = httpProxyService.proxyRequest(original, request, reqBody);

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

        Resource body = upstream.getBody();
        if (body == null) {
            throw new ResourceNotFoundException("upstream no body", "上游无响应体");
        }

        return new ProxyResponse(body, upstream.getHeaders(), upstream.getStatusCode());
    }

    @Override
    public ResponseDTO<String> handleUpload(MultipartFile file, String filetype, String region) throws IOException {
        byte[] body = file.getBytes();
        ResponseDTO<String> response = new ResponseDTO<>();
        switch (filetype) {
            case "mysekai" -> {
                // no-op for now
            }
            case "suite" -> response = userFileHandler.handleUploadedSuite(body, region);
            default -> throw new BadRequestException("未知的 filetype: " + filetype, "未知的 filetype");
        }
        return response;
    }
}
