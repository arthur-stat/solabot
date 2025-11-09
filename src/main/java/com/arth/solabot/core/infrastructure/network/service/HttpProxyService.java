package com.arth.solabot.core.infrastructure.network.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ServerWebInputException;

import java.io.IOException;

/**
 * HTTP 代理服务接口
 * 提供请求转发、请求信息获取等功能
 */
public interface HttpProxyService {

    /**
     * 将请求透明转发至目标 URL，再将收到的响应透明转发回请求者
     *
     * @param targetUrl 目标URL
     * @param request   原始HTTP请求
     * @param body      请求体数据
     * @return 转发响应实体
     * @throws ServerWebInputException 当HTTP方法不支持时抛出
     */
    ResponseEntity<Resource> proxyRequest(
            String targetUrl,
            HttpServletRequest request,
            byte[] body
    );

    /**
     * 从 HttpServletRequest 中读取请求体数据
     *
     * @param request HTTP请求
     * @return 请求体字节数组
     * @throws IOException 当读取输入流失败时抛出
     */
    byte[] readBody(HttpServletRequest request) throws IOException;
}