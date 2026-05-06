package com.lingyi.ai.service.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.Resource;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.*;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

/**
 * 大模型调用配置：直接使用 OkHttp 调用 MAAS OpenAI 兼容端点
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
    public OkHttpClient maasOkHttpClient() {
        return new OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory(), trustAllManager())
                .hostnameVerifier((hostname, session) -> true)
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
    }

    private ObjectMapper maasObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    public String callChatCompletion(String systemPrompt, String userPrompt) {
        try {
            String endpoint = baseUrl + "/compatible-mode/v1/chat/completions";
            String json = maasObjectMapper().writeValueAsString(new Object() {
                public String model = DashScopeConfig.this.model;
                public Message[] messages = new Message[]{
                        new Message("system", systemPrompt),
                        new Message("user", userPrompt)
                };
            });

            Request request = new Request.Builder()
                    .url(endpoint)
                    .post(RequestBody.create(json, MediaType.parse("application/json; charset=utf-8")))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .build();

            try (Response response = maasOkHttpClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("AI 接口返回错误，状态码: " + response.code() + ", 响应: " + response.body().string());
                }
                String respJson = response.body().string();
                log.info("AI 响应: {} (前100字)", respJson.substring(0, Math.min(100, respJson.length())));
                ChatCompletionResponse resp = maasObjectMapper().readValue(respJson, ChatCompletionResponse.class);
                return resp.choices[0].message.content;
            }
        } catch (Exception e) {
            throw new RuntimeException("AI 调用失败: " + e.getMessage(), e);
        }
    }

    private X509TrustManager trustAllManager() {
        return new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            public void checkClientTrusted(X509Certificate[] certs, String authType) {}
            public void checkServerTrusted(X509Certificate[] certs, String authType) {}
        };
    }

    private SSLSocketFactory sslSocketFactory() {
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, new TrustManager[]{trustAllManager()}, new SecureRandom());
            return sc.getSocketFactory();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static class Message {
        public String role;
        public String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    static class ChatCompletionResponse {
        public String id;
        public Choice[] choices;
        public Usage usage;
    }

    static class Choice {
        public ChatMessage message;
        public String finish_reason;
    }

    static class ChatMessage {
        public String role;
        public String content;
    }

    static class Usage {
        public int prompt_tokens;
        public int completion_tokens;
        public int total_tokens;
    }
}
