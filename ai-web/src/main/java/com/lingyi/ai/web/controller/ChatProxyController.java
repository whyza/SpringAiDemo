package com.lingyi.ai.web.controller;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/v1/chat")
public class ChatProxyController {

    private static final String API_KEY = "";
    private static final String ALI_URL = "https://ws-mbwk291770zz1xc1.cn-beijing.maas.aliyuncs.com/api/v1/services/aigc/text-generation/generation";

    @RequestMapping("/completions")
    public Object completions(@RequestBody String body) {
        JSONObject reqJson = JSON.parseObject(body);
        boolean isStream = reqJson.getBooleanValue("stream");

        JSONObject aliReq = new JSONObject();
        aliReq.put("model", "qwen3.6-max-preview");
        aliReq.put("input", new JSONObject().fluentPut("messages", reqJson.getJSONArray("messages")));
        aliReq.put("parameters", new JSONObject().fluentPut("stream", isStream));

        if (!isStream) {
            try {
                HttpResponse resp = HttpRequest.post(ALI_URL)
                                               .header("Authorization", "Bearer " + API_KEY)
                                               .body(aliReq.toString()).execute();

                JSONObject aliResp = JSON.parseObject(resp.body());
                String content = aliResp.getJSONObject("output")
                                        .getJSONArray("choices")
                                        .getJSONObject(0)
                                        .getJSONObject("message")
                                        .getString("content");

                JSONObject result = new JSONObject();
                result.put("id", "proxy-" + System.currentTimeMillis());
                result.put("object", "chat.completion");
                result.put("created", System.currentTimeMillis() / 1000);
                result.put("model", "qwen3.6-max-preview");
                result.put("choices", new JSONArray().fluentAdd(
                        new JSONObject().fluentPut("index", 0)
                                        .fluentPut("message", new JSONObject().fluentPut("role", "assistant").fluentPut("content", content))
                ));
                return result;
            } catch (Exception e) {
                e.printStackTrace();
                return new JSONObject();
            }
        }

        SseEmitter emitter = new SseEmitter(-1L);
        new Thread(() -> {
            try {
                HttpResponse response = HttpRequest.post(ALI_URL)
                                                   .header("Authorization", "Bearer " + API_KEY)
                                                   .header("Accept", "text/event-stream")
                                                   .body(aliReq.toString())
                                                   .executeAsync();

                BufferedReader reader = new BufferedReader(new InputStreamReader(response.bodyStream(), StandardCharsets.UTF_8));
                String line;

                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.startsWith("data:")) continue;

                    String data = line.substring(5).trim();
                    if (data.isEmpty() || data.equals("[DONE]")) continue;

                    try {
                        JSONObject jo = JSON.parseObject(data);
                        String content = jo.getJSONObject("output")
                                           .getJSONArray("choices")
                                           .getJSONObject(0)
                                           .getJSONObject("message")
                                           .getString("content");

                        if (!content.isEmpty()) {
                            emitter.send(content); // 🔥 只发纯文本，保留流式
                        }
                    } catch (Exception ignored) {}
                }
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }
}