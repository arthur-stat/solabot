package com.arth.solabot.core.web.service.pjsk;

import com.arth.solabot.adapter.controller.http.dto.ResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface PjskPluginService {

    Resource getMysekaiMap(String region, String id) throws IOException;

    Resource getMysekaiOverview(String region, String id) throws IOException;

    Resource getShadowrocketModuleForCnMysekai();

    ProxyResponse proxyUpload(HttpServletRequest request, String original) throws IOException;

    ResponseDTO<String> handleUpload(MultipartFile file, String filetype, String region) throws IOException;

    class ProxyResponse {
        public final Resource body;
        public final HttpHeaders headers;
        public final HttpStatusCode status;

        public ProxyResponse(Resource body, HttpHeaders headers, HttpStatusCode status) {
            this.body = body;
            this.headers = headers;
            this.status = status;
        }
    }
}
