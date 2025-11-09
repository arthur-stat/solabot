package com.arth.solabot.core.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@ConditionalOnBean(ServletWebServerFactory.class)
public class WebSocketContainerConfig {

    /**
     * 配置 TomCat 缓冲区大小，
     * TomCat 默认仅配置 8KB 缓冲区
     * @return
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer(
            @Value("${spring.ws-container.max-text-message-buffer-size}") int maxTextMessageBufferSize,
            @Value("${spring.ws-container.max-binary-message-buffer-size}") int maxBinaryMessageBufferSize,
            @Value("${spring.ws-container.max-session-idle-timeout}") long maxSessionIdleTimeout
            ) {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(maxTextMessageBufferSize);
        container.setMaxBinaryMessageBufferSize(maxBinaryMessageBufferSize);
        container.setMaxSessionIdleTimeout(maxSessionIdleTimeout);
        return container;
    }
}
