package com.arth.solabot.adapter.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.*;

@Slf4j
public class NetworkUtils {

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade"
    );

    private NetworkUtils() {
    }

    /**
     * 将请求透明转发至目标 URL，再将收到的响应透明转发回请求者
     *
     * @param webClient
     * @param targetUrl
     * @return
     */
    public static ResponseEntity<Resource> proxyRequest(
            WebClient webClient,
            String targetUrl,
            HttpServletRequest request,
            byte[] body
    ) {
        log.debug("[adapter.util] prepared to forward request...");
        final HttpMethod method;
        try {
            method = HttpMethod.valueOf(request.getMethod());
        } catch (IllegalArgumentException e) {
            log.error("[adapter.util] Unsupported HTTP method: {}", request.getMethod(), e);
            throw new ServerWebInputException("Unsupported HTTP method: " + request.getMethod());
        }
        final boolean absolute = targetUrl.startsWith("http://") || targetUrl.startsWith("https://");
        final boolean hasQuery = targetUrl.contains("?");
        final URI uri = absolute
                ? URI.create(targetUrl)
                : UriComponentsBuilder.fromUriString(targetUrl)
                .query(!hasQuery ? request.getQueryString() : null)
                .build(true)
                .toUri();
        WebClient.RequestBodySpec requestSpec = webClient.method(method).uri(uri);
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String lname = headerName.toLowerCase(Locale.ROOT);
            if (HOP_BY_HOP_HEADERS.contains(lname)) continue;
            if ("host".equals(lname) || "content-length".equals(lname)) continue;
            List<String> values = Collections.list(request.getHeaders(headerName));
            if (!values.isEmpty()) {
                requestSpec.header(headerName, values.toArray(String[]::new));
            }
        }
        WebClient.RequestHeadersSpec<?> specToSend =
                (body != null && body.length > 0) ? requestSpec.bodyValue(body) : requestSpec;
        log.debug("[adapter.util] forwarding request to the target url: {}", uri);
        return specToSend.exchangeToMono(clientResponse -> {
            final HttpStatusCode status = clientResponse.statusCode();
            final HttpHeaders respHeaders = new HttpHeaders();
            clientResponse.headers().asHttpHeaders().forEach(respHeaders::addAll);
            return clientResponse.bodyToMono(byte[].class)
                    .defaultIfEmpty(new byte[0])
                    .map(bytes -> {
                        Resource resource = new ByteArrayResource(bytes);
                        return ResponseEntity.status(status.value())
                                .headers(respHeaders)
                                .body(resource);
                    })
                    .doOnNext(resp -> log.debug("[adapter.util] forward success, status: {}", status.value()));
        }).block();
    }

    public static byte[] readBody(HttpServletRequest request) throws IOException {
        try (InputStream inputStream = request.getInputStream()) {
            return inputStream.readAllBytes();
        }
    }

    public static String getRequestInfoStr(ObjectMapper objectMapper, HttpServletRequest request) {
        if (request == null) {
            return "HttpServletRequest is null";
        }

        Map<String, Object> requestInfo = new LinkedHashMap<>();
        requestInfo.put("method", request.getMethod());
        requestInfo.put("url", request.getRequestURL().toString());
        requestInfo.put("uri", request.getRequestURI());
        requestInfo.put("queryString", request.getQueryString());
        requestInfo.put("remoteAddr", request.getRemoteAddr());
        requestInfo.put("contentType", request.getContentType());
        requestInfo.put("parameters", request.getParameterMap());

        Map<String, String> simpleHeaders = new LinkedHashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            simpleHeaders.put(name, request.getHeader(name));
        }
        requestInfo.put("headers", simpleHeaders);

        try {
            ObjectMapper mapper = objectMapper != null ? objectMapper : new ObjectMapper();
            mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestInfo);
        } catch (Exception e) {
            return "Error serializing request info: " + e.getMessage();
        }
    }

    public static String getRequestInfoStr(ObjectMapper objectMapper) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return "No active request (RequestContextHolder is null)";
        }
        HttpServletRequest request = attrs.getRequest();
        return getRequestInfoStr(objectMapper, request);
    }
}