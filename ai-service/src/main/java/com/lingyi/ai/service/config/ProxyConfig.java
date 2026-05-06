package com.lingyi.ai.service.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * HTTP 代理配置（用于公司内网访问外部 API）
 * <p>
 * 通过环境变量或 application.yml 配置：
 * - http.proxyHost / https.proxyHost
 * - http.proxyPort / https.proxyPort
 * - http.proxyUser / https.proxyUser (可选)
 * - http.proxyPassword / https.proxyPassword (可选)
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "http.proxy-host")
public class ProxyConfig {

    @Value("${http.proxy-host}")
    private String proxyHost;

    @Value("${http.proxy-port}")
    private int proxyPort;

    @Value("${http.proxy-user:}")
    private String proxyUser;

    @Value("${http.proxy-password:}")
    private String proxyPassword;

    @PostConstruct
    public void init() {
        log.info("配置 HTTP 代理：{}:{}", proxyHost, proxyPort);

        // 设置 HTTPS 代理
        System.setProperty("https.proxyHost", proxyHost);
        System.setProperty("https.proxyPort", String.valueOf(proxyPort));

        // 设置 HTTP 代理
        System.setProperty("http.proxyHost", proxyHost);
        System.setProperty("http.proxyPort", String.valueOf(proxyPort));

        // 可选：代理认证
        if (proxyUser != null && !proxyUser.isEmpty()) {
            System.setProperty("https.proxyUser", proxyUser);
            System.setProperty("https.proxyPassword", proxyPassword);
        }

        log.info("HTTP 代理配置完成");
    }
}
