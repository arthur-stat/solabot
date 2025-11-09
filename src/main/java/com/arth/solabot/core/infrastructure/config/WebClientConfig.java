package com.arth.solabot.core.infrastructure.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Bean
    public ConnectionProvider connectionProvider() {
        return ConnectionProvider.builder("global")
                .maxConnections(200)           // 连接池大小
                .pendingAcquireMaxCount(500)   // 等待连接队列大小
                .maxIdleTime(Duration.ofSeconds(30))
                .maxLifeTime(Duration.ofMinutes(5))
                .build();
    }

    @Bean
    public HttpClient reactorHttpClient(ConnectionProvider provider) {
        return HttpClient.create(provider)
                .compress(true)
                .followRedirect(true)
                .responseTimeout(Duration.ofSeconds(5))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000);
        // .proxy(...)     // 代理
        // .secure(...)    // mTLS / 自签证书
        // .wiretap(true)  // 调试日志
    }

    @Bean
    public ReactorClientHttpConnector reactorClientHttpConnector(HttpClient httpClient) {
        return new ReactorClientHttpConnector(httpClient);
    }

    @Bean
    public WebClient.Builder webClientBuilder(ReactorClientHttpConnector connector) {
        return WebClient.builder()
                .clientConnector(connector)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                // 这里硬编码缓冲区大小 16MB，但实际上我们是通过流式传输实现的，不存在缓冲区大小不足的问题，因此没有提供配置项
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024));
    }

    @Bean
    public WebClient defaultWebClient(WebClient.Builder builder) {
        return builder.build();
    }
}
