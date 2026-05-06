package com.lingyi.ai.service.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import javax.net.ssl.*;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * DashScope 大模型配置（排除自动配置后手动创建 Bean，绕过 SSL 证书校验）
 */
@Slf4j
@Configuration
public class DashScopeConfig {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    @Value("${spring.ai.dashscope.base-url}")
    private String baseUrl;

    @Value("${spring.ai.dashscope.chat.options.model:qwen-max}")
    private String model;

    @Bean
    public DashScopeApi dashScopeApi() {
        log.info("创建 DashScopeApi, baseUrl={}, model={}", baseUrl, model);
        return new DashScopeApi(baseUrl, apiKey, "", sslRestClientBuilder(), sslWebClientBuilder(), new DefaultResponseErrorHandler());
    }

    @Bean
    public DashScopeChatModel dashScopeChatModel(DashScopeApi dashScopeApi) {
        return new DashScopeChatModel(dashScopeApi, dashScopeChatOptions(), null, retryTemplate());
    }

    @Bean
    public DashScopeChatOptions dashScopeChatOptions() {
        return DashScopeChatOptions.builder().withModel(model).build();
    }

    @Bean
    public RetryTemplate retryTemplate() {
        return RetryTemplate.builder().maxAttempts(3).fixedBackoff(1000).build();
    }

    /**
     * 绕过 SSL 验证的 RestClient.Builder
     */
    private RestClient.Builder sslRestClientBuilder() {
        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(createInsecureHttpClient()));
    }

    private WebClient.Builder sslWebClientBuilder() {
        return WebClient.builder();
    }

    /**
     * 创建信任所有证书的 HTTP 客户端
     */
    private HttpClient createInsecureHttpClient() {
        try {
            X509TrustManager tm = new TrustAllManager();
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, new TrustManager[]{tm}, new SecureRandom());
            SSLParameters sslParams = sc.getDefaultSSLParameters();
            sslParams.setEndpointIdentificationAlgorithm(null);
            return HttpClient.newBuilder()
                    .sslContext(sc)
                    .sslParameters(sslParams)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("创建 HTTP 客户端失败", e);
        }
    }

    static class TrustAllManager implements X509TrustManager {
        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
    }
}
