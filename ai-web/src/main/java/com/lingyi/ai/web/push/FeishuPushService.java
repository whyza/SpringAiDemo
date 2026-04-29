package com.lingyi.ai.web.push;

import com.lingyi.ai.model.vo.DailyReportPushVO;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 飞书推送服务
 *
 * @author lingyi
 */
@Slf4j
@Service
public class FeishuPushService {

    @Value("${feishu.webhook.url:}")
    private String webhookUrl;

    @Value("${feishu.webhook.enabled:false}")
    private boolean enabled;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 推送日报到飞书
     */
    public void pushDailyReport(DailyReportPushVO report) {
        if (!enabled || webhookUrl == null || webhookUrl.isEmpty()) {
            log.warn("飞书推送未启用，跳过推送");
            return;
        }

        try {
            // 构建飞书富文本消息
            Map<String, Object> message = buildFeishuMessage(report);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(message, headers);

            String response = restTemplate.postForObject(webhookUrl, request, String.class);
            log.info("飞书推送成功，响应：{}", response);

        } catch (Exception e) {
            log.error("飞书推送失败", e);
            throw new RuntimeException("飞书推送失败：" + e.getMessage(), e);
        }
    }

    /**
     * 构建飞书消息体（富文本格式）
     */
    private Map<String, Object> buildFeishuMessage(DailyReportPushVO report) {
        Map<String, Object> message = new HashMap<>();
        message.put("msg_type", "interactive");

        // 构建交互式卡片
        Map<String, Object> card = new HashMap<>();
        card.put("config", Map.of("wide_screen_mode", true));

        // 卡片元素
        List<Map<String, Object>> elements = new java.util.ArrayList<>();

        // 1. 标题模块
        elements.add(createHeader("📊 电商运营日报报告", "blue"));

        // 2. 日期模块
        elements.add(createNote("📅 报告日期：" + report.getReportDate()));

        // 3. 分隔线
        elements.add(createDivider());

        // 4. 核心结论
        elements.add(createSection("📌 今日概览", report.getSummary(), null));

        // 5. 核心数据表格（用字段集展示）
        elements.add(createDataSection(report.getData()));

        // 6. 商品健康度
        elements.add(createHealthSection(report.getData(), report.getHealthScore()));

        // 7. 亮点分析
        if (report.getHighlights() != null && !report.getHighlights().isEmpty()) {
            elements.add(createListSection("✅ 亮点分析", report.getHighlights(), "green"));
        }

        // 8. 风险预警
        if (report.getRisks() != null && !report.getRisks().isEmpty()) {
            elements.add(createListSection("⚠️ 风险预警", report.getRisks(), "red"));
        }

        // 9. 运营建议（分优先级）
        if (report.getSuggestions() != null && !report.getSuggestions().isEmpty()) {
            elements.add(createSuggestionSection(report.getSuggestions()));
        }

        // 10. 底部按钮（查看完整报告）
        elements.add(createAction("💡 查看完整报告", "https://example.com"));

        card.put("elements", elements);
        message.put("card", card);

        return message;
    }

    /**
     * 创建标题模块
     */
    private Map<String, Object> createHeader(String title, String color) {
        Map<String, Object> header = new HashMap<>();
        header.put("tag", "header");
        Map<String, Object> titleObj = new HashMap<>();
        titleObj.put("tag", "plain_text");
        titleObj.put("content", title);
        header.put("title", titleObj);
        return header;
    }

    /**
     * 创建备注模块
     */
    private Map<String, Object> createNote(String text) {
        Map<String, Object> note = new HashMap<>();
        note.put("tag", "note");
        Map<String, Object> textObj = new HashMap<>();
        textObj.put("tag", "plain_text");
        textObj.put("content", text);
        note.put("elements", List.of(textObj));
        return note;
    }

    /**
     * 创建分隔线
     */
    private Map<String, Object> createDivider() {
        Map<String, Object> divider = new HashMap<>();
        divider.put("tag", "hr");
        return divider;
    }

    /**
     * 创建内容模块
     */
    private Map<String, Object> createSection(String title, String text, String color) {
        Map<String, Object> section = new HashMap<>();
        section.put("tag", "section");

        Map<String, Object> field = new HashMap<>();
        field.put("is_short", true);
        Map<String, Object> titleObj = new HashMap<>();
        titleObj.put("tag", "lark_md");
        titleObj.put("content", "**" + title + "**");
        field.put("title", titleObj);

        Map<String, Object> textObj = new HashMap<>();
        textObj.put("tag", "lark_md");
        textObj.put("content", text);
        field.put("text", textObj);

        section.put("fields", List.of(field));
        return section;
    }

    /**
     * 创建数据展示模块
     */
    private Map<String, Object> createDataSection(DailyReportPushVO.ReportDataVO data) {
        Map<String, Object> section = new HashMap<>();
        section.put("tag", "section");

        String salesGrowthIcon = data.getSalesGrowthRate() >= 0 ? "🟢" : "🔴";
        String revenueGrowthIcon = data.getRevenueGrowthRate() >= 0 ? "🟢" : "🔴";

        Map<String, Object> field1 = new HashMap<>();
        field1.put("is_short", true);
        field1.put("text", createLarkMdText(
                "**核心数据**\n\n" +
                        "📦 今日销量：**" + data.getTodaySales() + "** 件\n" +
                        "📉 昨日销量：" + data.getYesterdaySales() + " 件\n" +
                        "📊 环比：" + salesGrowthIcon + " " + String.format("%+.2f", data.getSalesGrowthRate()) + "%"
        ));

        Map<String, Object> field2 = new HashMap<>();
        field2.put("is_short", true);
        field2.put("text", createLarkMdText(
                "**销售额**\n\n" +
                        "💰 今日：**" + data.getTodayRevenue() + "**\n" +
                        "💰 昨日：" + data.getYesterdayRevenue() + "\n" +
                        "📊 环比：" + revenueGrowthIcon + " " + String.format("%+.2f", data.getRevenueGrowthRate()) + "%"
        ));

        section.put("fields", List.of(field1, field2));
        return section;
    }

    /**
     * 创建商品健康度模块
     */
    private Map<String, Object> createHealthSection(DailyReportPushVO.ReportDataVO data, Integer healthScore) {
        Map<String, Object> section = new HashMap<>();
        section.put("tag", "section");

        String healthEmoji = getHealthEmoji(healthScore);
        String healthText = getHealthText(healthScore);

        Map<String, Object> field = new HashMap<>();
        field.put("is_short", false);
        field.put("text", createLarkMdText(
                "**🔍 商品健康度**\n\n" +
                        "🟢 上涨商品：**" + data.getRisingLinks() + "** 个\n" +
                        "🔴 下跌商品：**" + data.getFallingLinks() + "** 个\n" +
                        "⚪ 7 天未出单：**" + data.getNoOrderLinks() + "** 个\n\n" +
                        "💯 健康度评分：**" + healthScore + "** " + healthEmoji + " " + healthText
        ));

        section.put("fields", List.of(field));
        return section;
    }

    /**
     * 创建列表模块
     */
    private Map<String, Object> createListSection(String title, List<String> items, String color) {
        Map<String, Object> section = new HashMap<>();
        section.put("tag", "section");

        StringBuilder sb = new StringBuilder();
        sb.append("**").append(title).append("**\n\n");
        for (String item : items) {
            sb.append("• ").append(item).append("\n");
        }

        Map<String, Object> field = new HashMap<>();
        field.put("is_short", false);
        field.put("text", createLarkMdText(sb.toString()));

        section.put("fields", List.of(field));
        return section;
    }

    /**
     * 创建运营建议模块（分层级）
     */
    private Map<String, Object> createSuggestionSection(List<String> suggestions) {
        Map<String, Object> section = new HashMap<>();
        section.put("tag", "section");

        StringBuilder sb = new StringBuilder();
        sb.append("**💡 运营建议**\n\n");

        for (String suggestion : suggestions) {
            // 识别优先级标记
            if (suggestion.contains("🔴") || suggestion.contains("止血")) {
                sb.append("🔴 **止血层**：").append(suggestion.replace("🔴", "").replace("止血层：", "").trim()).append("\n");
            } else if (suggestion.contains("🟡") || suggestion.contains("优化")) {
                sb.append("🟡 **优化层**：").append(suggestion.replace("🟡", "").replace("优化层：", "").trim()).append("\n");
            } else if (suggestion.contains("🟢") || suggestion.contains("战略")) {
                sb.append("🟢 **战略层**：").append(suggestion.replace("🟢", "").replace("战略层：", "").trim()).append("\n");
            } else {
                sb.append("• ").append(suggestion).append("\n");
            }
        }

        Map<String, Object> field = new HashMap<>();
        field.put("is_short", false);
        field.put("text", createLarkMdText(sb.toString()));

        section.put("fields", List.of(field));
        return section;
    }

    /**
     * 创建按钮模块
     */
    private Map<String, Object> createAction(String text, String url) {
        Map<String, Object> action = new HashMap<>();
        action.put("tag", "action");

        Map<String, Object> button = new HashMap<>();
        button.put("tag", "button");
        button.put("text", createLarkMdText(text));
        button.put("type", "primary");
        button.put("url", url);

        action.put("actions", List.of(button));
        return action;
    }

    /**
     * 创建飞书 Markdown 文本对象
     */
    private Map<String, Object> createLarkMdText(String content) {
        Map<String, Object> textObj = new HashMap<>();
        textObj.put("tag", "lark_md");
        textObj.put("content", content);
        return textObj;
    }

    /**
     * 获取健康度表情
     */
    private String getHealthEmoji(Integer score) {
        if (score == null) return "⚪";
        if (score >= 80) return "🟢";
        if (score >= 60) return "🟡";
        if (score >= 40) return "🟠";
        return "🔴";
    }

    /**
     * 获取健康度文本
     */
    private String getHealthText(Integer score) {
        if (score == null) return "未知";
        if (score >= 80) return "优秀";
        if (score >= 60) return "良好";
        if (score >= 40) return "一般";
        return "较差";
    }
}
