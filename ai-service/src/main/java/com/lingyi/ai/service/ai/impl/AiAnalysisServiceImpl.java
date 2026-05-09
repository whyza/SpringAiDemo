package com.lingyi.ai.service.ai.impl;

import com.lingyi.ai.model.dto.EcommerceDataDTO;
import com.lingyi.ai.model.vo.DailyReportPushVO;
import com.lingyi.ai.service.DailyReportService;
import com.lingyi.ai.service.ai.AiAnalysisService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 分析服务实现（基于 Spring AI Alibaba）
 *
 * @author lingyi
 */
@Slf4j
@Service
public class AiAnalysisServiceImpl implements AiAnalysisService {

    public static final String SYSTEM_PROMPT = """
            你是一名电商运营人员，负责分析每日数据。你**只能**使用用户输入的实际数据，以下是绝对规则：
            
            1. **禁止编造任何数据中不存在的数字** - 只使用输入中提供的数据
            2. **禁止凭空推测** - 不要写"可能"、"或许"、"季节性"、"预计"等推测性内容
            3. **禁止衍生计算** - 不要自行计算衍生指标（如"每 SKU 产出"、"日均销量"等），只能用输入中直接提供的数字
            4. **数据不足以支撑分析的，直接跳过，不要硬写**
            
            ## 输出格式（按顺序输出，每个区块都要充分展开）
            
            ### 📊 关键指标速览
            | 指标 | 今日 | 昨日 | 环比 |
            |------|------|------|------|
            | 销量 | X 件 | X 件 | ↑/↓X% |
            | 销售额 | X 元 | X 元 | ↑/↓X% |
            | 客单价 | X 元 | X 元 | ↑/↓X% |
            
            ### 核心结论
            用 3-5 条✅/⚠️开头的要点总结今日表现，每条 1-2 句话，必须引用输入中的具体数字
            
            ### 业务本质
            2-3 条，基于数据解读：
            - 销量和销售额的涨跌情况
            - 客单价变化情况
            - 量价关系（只描述数据，不推测原因）
            
            ### 多维度评估（基于输入数据）
            - 增长质量：销量增速 vs 销售额增速的差距
            - 商品结构：上涨和下跌商品的数量和比例
            - SKU 健康度：7 天未出单 SKU 的数量和占比
            
            ### 亮点（有就写，2-4 条）
            ✅ [简短标题]：引用输入中的具体数字
            
            ### 风险预警（有就写，2-4 条）
            ⚠️ [简短标题]：引用输入中的具体数字
            
            ### 运营建议（必须写，3-5 条，分优先级）
            最紧急要处理的 1 条建议
            次重要的 1条建议
            可择机推进的 1 条建议
            
            ## 核心原则
            - 所有数字必须直接来自输入数据
            - 不写"数据有限"之类的客套话
            - 使用✅表示亮点，⚠️表示风险
            - 深入分析是指详细说明数据之间的关系，不是衍生新数字
            """;

    private static final String SUMMARY_SYSTEM_PROMPT = """
            你是一名电商运营人员，需要对以下完整分析报告进行**精简总结**，生成一份**关键报告摘要**。
            
            ## 要求
            1. **不要重复**亮点、风险、运营建议的具体内容（这些已在上方结构化展示）
            2. **只总结**：核心指标速览表格、核心结论、业务本质、多维度评估
            3. 保持 Markdown 格式，简洁清晰
            4. 所有数字必须来自原始数据，禁止编造
            5. 300-500 字为宜
            """;

    @Resource
    private ChatModel chatModel;

    @Resource
    private DailyReportService dailyReportService;


    @Override
    public String callAiAnalysis(String systemPrompt, String userPrompt) {
        log.info("调用 AI 分析，system 长度：{}, user 长度：{}", systemPrompt.length(), userPrompt.length());
        try {
            Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt)));
            String result = chatModel.call(prompt).getResult().getOutput().getContent();
            log.info("AI 分析完成");
            return result;
        } catch (Exception e) {
            log.error("AI 调用失败", e);
            throw new RuntimeException("AI 分析失败，请稍后重试：" + e.getMessage(), e);
        }
    }

    @Override
    public DailyReportPushVO generateDailyReport(EcommerceDataDTO dataDTO) {
        log.info("生成电商日报，日期：{}", dataDTO.getReportDate());
        try {
            String aiResponse = callAiAnalysis(SYSTEM_PROMPT, buildPrompt(dataDTO));
            return buildAndSaveReport(dataDTO, aiResponse);
        } catch (Exception e) {
            log.error("日报生成失败", e);
            throw new RuntimeException("日报生成失败：" + e.getMessage(), e);
        }
    }

    /**
     * 构建 AI 提示词
     */
    public String buildPrompt(EcommerceDataDTO dataDTO) {
        Double salesGrowthRate = calculateGrowthRate(dataDTO.getYesterdaySales(), dataDTO.getTodaySales());
        Double revenueGrowthRate = calculateGrowthRate(dataDTO.getYesterdayRevenue(), dataDTO.getTodayRevenue());
        return buildDailyReportPrompt(dataDTO, salesGrowthRate, revenueGrowthRate);
    }

    /**
     * 解析 AI 响应并保存报告
     */
    public DailyReportPushVO buildAndSaveReport(EcommerceDataDTO dataDTO, String aiResponse) {
        Double salesGrowthRate = calculateGrowthRate(dataDTO.getYesterdaySales(), dataDTO.getTodaySales());
        Double revenueGrowthRate = calculateGrowthRate(dataDTO.getYesterdayRevenue(), dataDTO.getTodayRevenue());
        Integer healthScore = calculateHealthScore(dataDTO, salesGrowthRate);

        DailyReportPushVO reportVO = new DailyReportPushVO();
        reportVO.setReportDate(dataDTO.getReportDate() != null ? dataDTO.getReportDate() : java.time.LocalDate.now());

        DailyReportPushVO.ReportDataVO dataVO = new DailyReportPushVO.ReportDataVO();
        dataVO.setTodaySales(dataDTO.getTodaySales());
        dataVO.setYesterdaySales(dataDTO.getYesterdaySales());
        dataVO.setSalesGrowthRate(salesGrowthRate);
        dataVO.setTodayRevenue(formatCurrency(dataDTO.getTodayRevenue()));
        dataVO.setYesterdayRevenue(formatCurrency(dataDTO.getYesterdayRevenue()));
        dataVO.setRevenueGrowthRate(revenueGrowthRate);
        dataVO.setRisingLinks(dataDTO.getRisingLinks());
        dataVO.setFallingLinks(dataDTO.getFallingLinks());
        dataVO.setNoOrderLinks(dataDTO.getNoOrderLinks());
        reportVO.setData(dataVO);

        parseAiResponse(aiResponse, reportVO);
        reportVO.setHealthScore(healthScore);
        reportVO.setFullReport(aiResponse);

        try {
            String keyReport = generateKeyReport(aiResponse);
            reportVO.setKeyReport(keyReport);
        } catch (Exception e) {
            log.warn("生成关键报告失败，使用完整报告", e);
            reportVO.setKeyReport(aiResponse);
        }

        log.info("日报生成完成，健康度评分：{}", healthScore);
        dailyReportService.saveReport(reportVO, dataDTO);
        return reportVO;
    }

    private String generateKeyReport(String fullReport) {
        log.info("生成关键报告摘要，原文长度：{}", fullReport.length());
        String userPrompt = "请对以下电商日报进行精简总结（保留核心指标速览、核心结论、业务本质、多维度评估，不要亮点/风险/建议）：\n\n" + fullReport;
        return callAiAnalysis(SUMMARY_SYSTEM_PROMPT, userPrompt);
    }

    private Double calculateGrowthRate(Integer yesterday, Integer today) {
        if (yesterday == null || yesterday == 0) {
            return today == null || today == 0 ? 0.0 : 100.0;
        }
        if (today == null) {
            return -100.0;
        }
        BigDecimal y = BigDecimal.valueOf(yesterday);
        BigDecimal t = BigDecimal.valueOf(today);
        return t.subtract(y).multiply(BigDecimal.valueOf(100)).divide(y, 2, RoundingMode.HALF_UP).doubleValue();
    }

    private Double calculateGrowthRate(BigDecimal yesterday, BigDecimal today) {
        if (yesterday == null || yesterday.compareTo(BigDecimal.ZERO) == 0) {
            return today == null || today.compareTo(BigDecimal.ZERO) == 0 ? 0.0 : 100.0;
        }
        if (today == null) {
            return -100.0;
        }
        return today
                .subtract(yesterday)
                .multiply(BigDecimal.valueOf(100))
                .divide(yesterday, 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private Integer calculateHealthScore(EcommerceDataDTO dataDTO, Double salesGrowthRate) {
        int score = 50;
        if (salesGrowthRate != null) {
            if (salesGrowthRate > 10) {
                score += 30;
            } else if (salesGrowthRate > 5) {
                score += 20;
            } else if (salesGrowthRate > 0) {
                score += 10;
            } else if (salesGrowthRate < -10) {
                score -= 20;
            } else if (salesGrowthRate < -5) {
                score -= 10;
            }
        }
        int totalLinks = dataDTO.getRisingLinks() + dataDTO.getFallingLinks();
        if (totalLinks > 0) {
            double risingRatio = (double) dataDTO.getRisingLinks() / totalLinks;
            if (risingRatio > 0.7) {
                score += 20;
            } else if (risingRatio > 0.5) {
                score += 10;
            } else if (risingRatio < 0.3) {
                score -= 15;
            }
        }
        if (dataDTO.getNoOrderLinks() != null && dataDTO.getNoOrderLinks() > 0) {
            int penalty = Math.min(30, dataDTO.getNoOrderLinks() / 10 * 5);
            score -= penalty;
        }
        return Math.max(0, Math.min(100, score));
    }

    private String buildDailyReportPrompt(EcommerceDataDTO dataDTO, Double salesGrowthRate, Double revenueGrowthRate) {
        String growthSymbol1 = salesGrowthRate >= 0 ? "↑" : "↓";
        String growthSymbol2 = revenueGrowthRate >= 0 ? "↑" : "↓";

        BigDecimal todayUnitPrice = dataDTO.getTodaySales() != null && dataDTO.getTodaySales() > 0 ? dataDTO
                .getTodayRevenue()
                .divide(BigDecimal.valueOf(dataDTO.getTodaySales()), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal yesterdayUnitPrice = dataDTO.getYesterdaySales() != null && dataDTO.getYesterdaySales() > 0 ? dataDTO
                .getYesterdayRevenue()
                .divide(BigDecimal.valueOf(dataDTO.getYesterdaySales()), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        Double unitPriceGrowth = todayUnitPrice.subtract(yesterdayUnitPrice).multiply(BigDecimal.valueOf(100)).divide(
                yesterdayUnitPrice,
                2,
                RoundingMode.HALF_UP
        ).doubleValue();
        String unitPriceSymbol = unitPriceGrowth >= 0 ? "↑" : "↓";

        int totalLinks = dataDTO.getRisingLinks() + dataDTO.getFallingLinks();
        double risingRatio = totalLinks > 0 ? (double) dataDTO.getRisingLinks() / totalLinks * 100 : 0;
        int totalLinksAll = totalLinks + dataDTO.getNoOrderLinks();
        double invalidSkuRatio = totalLinksAll > 0 ? (double) dataDTO.getNoOrderLinks() / totalLinksAll * 100 : 0;

        return String.format(
                """
                        【电商日报数据】
                        你只能分析以下数据，不要编造任何此处没有的数据。
                        
                        报告日期：%s
                        
                        === 核心指标 ===
                        销量：今日 %d 件，昨日 %d 件，环比 %s%.2f%%
                        销售额：今日 %.2f 元，昨日 %.2f 元，环比 %s%.2f%%
                        客单价：今日 %.2f 元，昨日 %.2f 元，环比 %s%.2f%%
                        
                        === 商品结构 ===
                        上涨：%d 个（%.1f%%）
                        下跌：%d 个（%.1f%%）
                        7 天未出单：%d 个（%.1f%%）
                        总计 SKU：%d 个，有效 SKU（有销量）：%d 个
                        
                        **输出要求：有数据才写，没有就跳过。不要编造，不要推测。**
                        【业务本质】1-2 条
                        【多维度评估】0-2 条（增长质量/商品结构）
                        【亮点】✅开头，有就写
                        【风险】⚠️开头，有就写
                        【建议】仅当亮点或风险≥2 条时才写，1-2 条
                        """,
                dataDTO.getReportDate() != null ? dataDTO.getReportDate() : java.time.LocalDate.now(),
                dataDTO.getTodaySales(),
                dataDTO.getYesterdaySales(),
                growthSymbol1,
                Math.abs(salesGrowthRate),
                dataDTO.getTodayRevenue(),
                dataDTO.getYesterdayRevenue(),
                growthSymbol2,
                Math.abs(revenueGrowthRate),
                todayUnitPrice,
                yesterdayUnitPrice,
                unitPriceSymbol,
                Math.abs(unitPriceGrowth),
                dataDTO.getRisingLinks(),
                risingRatio,
                dataDTO.getFallingLinks(),
                100 - risingRatio,
                dataDTO.getNoOrderLinks(),
                invalidSkuRatio,
                totalLinksAll,
                totalLinks
        );
    }

    private void parseAiResponse(String aiResponse, DailyReportPushVO reportVO) {
        String[] lines = aiResponse.split("\n");
        String summary = "今日数据已分析";
        for (String line : lines) {
            String s = stripMarkdown(line).trim();
            if (!s.isEmpty() && s.length() >= 3) {
                summary = s;
                break;
            }
        }
        reportVO.setSummary(summary);

        List<String> highlights = new ArrayList<>();
        List<String> risks = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        int section = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (trimmed.startsWith("#")) {
                if (trimmed.contains("📊") || trimmed.contains("关键指标速览")) {
                    section = 0;
                    continue;
                }
                if (trimmed.contains("核心结论")) {
                    section = 1;
                    continue;
                }
                if (trimmed.contains("业务本质")) {
                    section = 2;
                    continue;
                }
                if (trimmed.contains("多维度评估")) {
                    section = 3;
                    continue;
                }
                if (trimmed.contains("亮点") && !trimmed.contains("风险")) {
                    section = 4;
                    continue;
                }
                if (trimmed.contains("风险预警") || trimmed.contains("风险")) {
                    section = 5;
                    continue;
                }
                if (trimmed.contains("建议") || trimmed.contains("运营建议")) {
                    section = 6;
                    continue;
                }
            }

            String content = extractContent(trimmed);
            if (content.isEmpty()) {
                continue;
            }
            if (content.startsWith("|---") || content.startsWith("|------")) {
                continue;
            }
            if (content.startsWith("| 销量") || content.startsWith("| 销售额") || content.startsWith("| 客单价")) {
                continue;
            }

            switch (section) {
                case 1 -> {
                    if (isNegative(content)) {
                        risks.add(content);
                    } else {
                        highlights.add(content);
                    }
                }
                case 2 -> highlights.add(content);
                case 3 -> {
                    if (isNegative(content)) {
                        risks.add(content);
                    } else {
                        highlights.add(content);
                    }
                }
                case 4 -> highlights.add(content);
                case 5 -> risks.add(content);
                case 6 -> suggestions.add(content);
            }
        }

        highlights = deduplicateAndFilter(highlights);
        risks = deduplicateAndFilter(risks);
        suggestions = deduplicateAndFilter(suggestions);

        if (highlights.isEmpty()) {
            highlights.add("数据已正常分析，请查看完整报告");
        }
        if (risks.isEmpty() && reportVO.getHealthScore() != null && reportVO.getHealthScore() < 60) {
            risks.add("健康度评分偏低，建议重点关注商品结构和运营策略");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("继续保持当前运营策略，关注数据变化趋势");
        }

        reportVO.setHighlights(highlights);
        reportVO.setRisks(risks);
        reportVO.setSuggestions(suggestions);
    }

    private String extractContent(String line) {
        String content = line.replaceAll("^#{1,6}\\s*", "");
        if (content.isEmpty()) {
            return "";
        }
        content = content.replaceAll("\\*\\*", "");
        content = content.replaceAll("^[•\\-]\\s*", "").trim();
        if (content.isEmpty() || content.length() < 3) {
            return "";
        }
        if (content.matches("^[-=*_#`|]+$")) {
            return "";
        }
        if (content.matches("^[✅⚠️💡🔴🟢][^\\s]{1,8}[：:]$") || content.matches("^[^\\s]{2,15}[：:]$") && !content.contains(
                "：") && !content.contains("。")) {
            if (content.matches("^[^\\s]{2,12}[：:]$")) {
                return "";
            }
        }
        return content;
    }

    private boolean isNegative(String content) {
        return content.contains("下降") || content.contains("下滑") || content.contains("风险") || content.contains("恶化") || content.contains(
                "浪费") || content.contains("低效") || content.contains("不足") || content.contains("失衡") || content.contains(
                "侵蚀") || content.contains("亏损") || content.contains("劣化") || content.contains("错配");
    }

    private String stripMarkdown(String text) {
        return text.replaceAll("[#*✅⚠️🟡🟢\\-•|`]", "").replaceAll("^[0-9.]+\\s*", "").trim();
    }

    private List<String> deduplicateAndFilter(List<String> items) {
        List<String> result = new ArrayList<>();
        for (String item : items) {
            if (item != null && !item.isEmpty() && !item.isBlank() && !result.contains(item)) {
                result.add(item);
            }
        }
        return result;
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) {
            return "-";
        }
        if (amount.compareTo(BigDecimal.valueOf(10000)) >= 0) {
            return String.format("%.2f 万", amount.divide(BigDecimal.valueOf(10000), 2, RoundingMode.HALF_UP));
        }
        return String.format("%.2f 元", amount);
    }
}
