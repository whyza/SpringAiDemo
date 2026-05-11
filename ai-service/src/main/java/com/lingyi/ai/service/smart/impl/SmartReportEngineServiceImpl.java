package com.lingyi.ai.service.smart.impl;

import com.lingyi.ai.model.dto.SmartReportRequestDTO;
import com.lingyi.ai.model.vo.SmartReportResultVO;
import com.lingyi.ai.service.ai.AiAnalysisService;
import com.lingyi.ai.service.smart.SmartReportEngineService;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 智能报告引擎服务实现
 *
 * @author lingyi
 */
@Slf4j
@Service
public class SmartReportEngineServiceImpl implements SmartReportEngineService {

    private static final String DEFAULT_CONCLUSION = "📋 本日数据平稳，暂无明显异常";

    @Resource
    private AiAnalysisService aiAnalysisService;

    @Override
    public SmartReportResultVO analyze(SmartReportRequestDTO request) {
        request.applyDefaults();

        String diagnosisConclusionText = generateDiagnosisConclusion(request);
        String operationDiagnosisText = generateOperationDiagnosis(request, diagnosisConclusionText);
        StructuredDiagnosis structuredDiagnosis = parseStructuredDiagnosis(
                operationDiagnosisText,
                diagnosisConclusionText
        );

        SmartReportResultVO result = new SmartReportResultVO();
        result.setDiagnosisConclusionText(diagnosisConclusionText);
        result.setOperationDiagnosisText(operationDiagnosisText);

        SmartReportResultVO.DiagnosisConclusionVO diagnosisConclusion = new SmartReportResultVO.DiagnosisConclusionVO();
        diagnosisConclusion.setRedAlerts(structuredDiagnosis.getRedAlerts());
        diagnosisConclusion.setYellowAlerts(structuredDiagnosis.getYellowAlerts());
        diagnosisConclusion.setGreenHighlights(structuredDiagnosis.getGreenHighlights());
        result.setDiagnosisConclusions(diagnosisConclusion);

        SmartReportResultVO.OperationDiagnosisVO operationDiagnosis = new SmartReportResultVO.OperationDiagnosisVO();
        operationDiagnosis.setCoreConclusions(structuredDiagnosis.getCoreConclusions());
        operationDiagnosis.setBusinessEssence(structuredDiagnosis.getBusinessEssence());
        operationDiagnosis.setMultiDimensionalEvaluations(structuredDiagnosis.getMultiDimensionalEvaluations());
        operationDiagnosis.setHighlights(structuredDiagnosis.getHighlights());
        operationDiagnosis.setRiskWarnings(structuredDiagnosis.getRiskWarnings());
        operationDiagnosis.setOperationSuggestions(structuredDiagnosis.getOperationSuggestions());
        result.setOperationDiagnosis(operationDiagnosis);
        return result;
    }

    private String generateDiagnosisConclusion(SmartReportRequestDTO req) {
        String systemPrompt = """
                你是一位电商运营诊断助手。
                你需要严格根据输入数据和给定规则，输出固定格式的诊断结论。
                
                规则要求：
                1. 只允许使用以下规则名称：
                红色：销售额大幅下滑预警、大量链接滞销预警
                黄色：销售额下滑关注、链接下跌趋势明显、存在滞销链接
                绿色：销售额稳步增长、多链接表现亮眼、利润率表现优秀
                2. 红色文案固定为：🔴 **需立即关注：** {规则名称，逗号分隔}
                3. 黄色文案固定为：🟡 **值得关注：** {规则名称，逗号分隔}
                4. 绿色文案固定为：🟢 **本周运营状态良好！** {规则名称，逗号分隔}
                5. 如果多级别同时触发，必须按红色、黄色、绿色顺序逐行输出。
                6. 如果只有某一级触发，则只输出对应那一行。
                7. 如果没有任何规则触发，只输出：📋 本日数据平稳，暂无明显异常
                8. 只能依据输入数据和规则阈值判断，不能编造事实，不能补充解释。
                
                除规定格式外，不要输出任何其他内容。
                """;

        String userPrompt = buildDiagnosisConclusionPrompt(req);
        try {
            return aiAnalysisService.callAiAnalysis(systemPrompt, userPrompt);
        } catch (Exception e) {
            log.warn("AI 生成诊断结论失败，使用本地规则降级", e);
            return buildFallbackDiagnosisConclusion(req);
        }
    }

    private String generateOperationDiagnosis(SmartReportRequestDTO req, String diagnosisConclusion) {
        String systemPrompt = """
                你是一位专业的电商运营顾问，正在基于输入数据生成当天运营诊断。
                你只能依据提供的数据和诊断结论输出内容，不允许编造任何未提供的数据，不允许新增业务事实。
                
                输出要求如下：
                1. 使用简体中文，语气专业、直接。
                2. 只输出“运营诊断”正文，不要输出开场白。
                3. 必须包含以下六部分：
                核心结论、业务本质、多维度评估、亮点、风险预警、运营建议。
                
                ### 核心结论
                用 3-5 条“✅/⚠️”开头的要点总结今日表现，每条 1-2 句话，必须引用输入中的具体数字。
                
                ### 业务本质
                2-3 条，基于数据解读：
                销量和销售额的涨跌情况
                客单价变化情况
                量价关系（只描述数据，不推测原因）
                
                ### 多维度评估（基于输入数据）
                增长质量：销量增速 vs 销售额增速的差距
                商品结构：上涨和下跌商品的数量和比例
                SKU 健康度：未出单 SKU 的数量和占比
                
                ### 亮点（有就写，2-4 条）
                ✅ [简短标题]：引用输入中的具体数字
                
                ### 风险预警（有就写，2-4 条）
                ⚠️ [简短标题]：引用输入中的具体数字
                
                ### 运营建议（必须写，3-5 条，分优先级）
                最紧急要处理的 1 条建议
                次重要的 1 条建议
                可择机推进的 1 条建议
                
                4. “亮点”部分使用“✅”开头逐条输出。
                5. “风险预警”部分使用“⚠️”开头逐条输出。
                6. 如果某部分没有充分数据支撑，可以基于现有输入简洁表达，不要扩写推测内容。
                7. 所有数字必须直接来自输入数据或诊断结论。
                """;

        String userPrompt = buildOperationDiagnosisPrompt(req, diagnosisConclusion);
        try {
            return aiAnalysisService.callAiAnalysis(systemPrompt, userPrompt);
        } catch (Exception e) {
            log.warn("AI 生成运营诊断失败，使用结构化模板降级", e);
            return buildFallbackOperationDiagnosis(req, diagnosisConclusion);
        }
    }

    private String buildDiagnosisConclusionPrompt(SmartReportRequestDTO req) {
        return """
                请基于以下数据和规则判断诊断结论。
                ## 原始数据
                当天销售额：%s USD
                昨日销售额：%s USD
                销售额变化：%s
                当天订单量：%d 单
                昨日订单量：%d 单
                今日上涨链接数：%d
                昨日上涨链接数：%d
                今日下跌链接数：%d
                昨日下跌链接数：%d
                今日未出单链接数：%d
                昨日未出单链接数：%d
                今日总链接数：%d
                昨日总链接数：%d
                
                ## 判断规则
                红色规则：
                1. 销售额大幅下滑预警：当销售额变化为负，且下滑幅度大于等于 %s%% 时命中。
                2. 大量链接滞销预警：当今日未出单链接数占今日总链接数比例大于等于 %s%% 时命中。
                黄色规则：
                1. 销售额下滑关注：当销售额变化为负，且下滑幅度大于等于 %s%%，同时小于红色销售额阈值时命中。
                2. 链接下跌趋势明显：当今日下跌链接数大于今日上涨链接数的 %s 倍时命中。
                3. 存在滞销链接：当今日未出单链接数大于等于 1，且未达到红色未出单占比阈值时命中。
                绿色规则：
                1. 销售额稳步增长：当销售额变化大于等于 %s%% 时命中。
                2. 多链接表现亮眼：当今日上涨链接数占今日总链接数比例大于等于 %s%% 时命中。
                """.formatted(
                formatAmount(req.getTodayRevenue()),
                formatAmount(req.getYesterdayRevenue()),
                formatPercentTrend(calculateRevenueChange(req)),
                safeInt(req.getTodayOrders()),
                safeInt(req.getYesterdayOrders()),
                safeInt(req.getTodayRisingLinks()),
                safeInt(req.getYesterdayRisingLinks()),
                safeInt(req.getTodayFallingLinks()),
                safeInt(req.getYesterdayFallingLinks()),
                safeInt(req.getTodayNoOrderLinks()),
                safeInt(req.getYesterdayNoOrderLinks()),
                calculateTodayTotalLinks(req),
                calculateYesterdayTotalLinks(req),
                formatAmount(req.getR1Threshold()),
                formatAmount(req.getR2Threshold()),
                formatAmount(req.getY1Threshold()),
                formatAmount(req.getY2Ratio()),
                formatAmount(req.getG1Threshold()),
                formatAmount(req.getG2Threshold())
        );
    }

    private String buildOperationDiagnosisPrompt(SmartReportRequestDTO req, String diagnosisConclusion) {
        return """
                ## 原始数据
                当天销售额：%s USD
                昨日销售额：%s USD
                销售额变化：%s
                当天订单量：%d 单
                昨日订单量：%d 单
                今日上涨链接数：%d
                昨日上涨链接数：%d
                今日下跌链接数：%d
                昨日下跌链接数：%d
                今日未出单链接数：%d
                昨日未出单链接数：%d
                今日总链接数：%d
                昨日总链接数：%d
                
                ## 诊断结论
                %s
                
                请基于以上内容输出完整的运营诊断正文。
                """.formatted(
                formatAmount(req.getTodayRevenue()),
                formatAmount(req.getYesterdayRevenue()),
                formatPercentTrend(calculateRevenueChange(req)),
                safeInt(req.getTodayOrders()),
                safeInt(req.getYesterdayOrders()),
                safeInt(req.getTodayRisingLinks()),
                safeInt(req.getYesterdayRisingLinks()),
                safeInt(req.getTodayFallingLinks()),
                safeInt(req.getYesterdayFallingLinks()),
                safeInt(req.getTodayNoOrderLinks()),
                safeInt(req.getYesterdayNoOrderLinks()),
                calculateTodayTotalLinks(req),
                calculateYesterdayTotalLinks(req),
                diagnosisConclusion
        );
    }

    private String buildFallbackDiagnosisConclusion(SmartReportRequestDTO req) {
        TriggeredRules triggeredRules = evaluateFallbackRules(req);
        List<String> lines = new ArrayList<>();
        if (!triggeredRules.getRedAlerts().isEmpty()) {
            lines.add("🔴 **需立即关注：** " + String.join("，", triggeredRules.getRedAlerts()));
        }
        if (!triggeredRules.getYellowAlerts().isEmpty()) {
            lines.add("🟡 **值得关注：** " + String.join("，", triggeredRules.getYellowAlerts()));
        }
        if (!triggeredRules.getGreenAlerts().isEmpty()) {
            lines.add("🟢 **本周运营状态良好！** " + String.join("，", triggeredRules.getGreenAlerts()));
        }
        return lines.isEmpty() ? DEFAULT_CONCLUSION : String.join("\n", lines);
    }

    private String buildFallbackOperationDiagnosis(SmartReportRequestDTO req, String diagnosisConclusion) {
        int todayTotalLinks = calculateTodayTotalLinks(req);
        int yesterdayTotalLinks = calculateYesterdayTotalLinks(req);
        String revenueChange = formatPercentTrend(calculateRevenueChange(req));

        StringBuilder content = new StringBuilder();
        content.append("### 核心结论\n");
        content.append("1. 当天销售额 ").append(formatAmount(req.getTodayRevenue())).append(" USD，昨日销售额 ").append(
                formatAmount(req.getYesterdayRevenue())).append(" USD，销售额变化 ").append(revenueChange).append("。\n");
        content.append("2. 当天订单量 ").append(safeInt(req.getTodayOrders())).append(" 单，昨日订单量 ").append(safeInt(
                req.getYesterdayOrders())).append(" 单。\n");
        content
                .append("3. 今日总链接数 ")
                .append(todayTotalLinks)
                .append("，昨日总链接数 ")
                .append(yesterdayTotalLinks)
                .append("。\n\n");

        content.append("### 业务本质\n");
        content.append("1. 当前经营表现主要由销售额、订单量和链接结构共同驱动。\n");
        content.append("2. 今日上涨链接 ").append(safeInt(req.getTodayRisingLinks())).append("，今日下跌链接 ").append(
                safeInt(req.getTodayFallingLinks())).append("，说明链接表现存在分化。\n");
        content
                .append("3. 今日未出单链接 ")
                .append(safeInt(req.getTodayNoOrderLinks()))
                .append("，直接反映低效链接压力。\n\n");

        content.append("### 多维度评估\n");
        content
                .append("1. 销售维度：当天销售额 ")
                .append(formatAmount(req.getTodayRevenue()))
                .append(" USD，昨日销售额 ")
                .append(formatAmount(req.getYesterdayRevenue()))
                .append(" USD，变化 ")
                .append(revenueChange)
                .append("。\n");
        content
                .append("2. 订单维度：当天订单量 ")
                .append(safeInt(req.getTodayOrders()))
                .append(" 单，昨日订单量 ")
                .append(safeInt(req.getYesterdayOrders()))
                .append(" 单。\n");
        content
                .append("3. 链接维度：今日上涨 ")
                .append(safeInt(req.getTodayRisingLinks()))
                .append("，今日下跌 ")
                .append(safeInt(req.getTodayFallingLinks()))
                .append("，今日未出单 ")
                .append(safeInt(req.getTodayNoOrderLinks()))
                .append("。\n");
        content
                .append("4. 对比维度：昨日上涨 ")
                .append(safeInt(req.getYesterdayRisingLinks()))
                .append("，昨日下跌 ")
                .append(safeInt(req.getYesterdayFallingLinks()))
                .append("，昨日未出单 ")
                .append(safeInt(req.getYesterdayNoOrderLinks()))
                .append("。\n\n");

        content.append("### 亮点\n");
        if (diagnosisConclusion.contains("🟢")) {
            appendLabeledLines(content, diagnosisConclusion, "🟢", "✅ ");
        } else {
            content.append("✅ 当前未触发明显运营亮点规则。\n");
        }
        content.append("\n");

        content.append("### 风险预警\n");
        boolean hasRisk = false;
        if (diagnosisConclusion.contains("🔴")) {
            appendLabeledLines(content, diagnosisConclusion, "🔴", "⚠️ ");
            hasRisk = true;
        }
        if (diagnosisConclusion.contains("🟡")) {
            appendLabeledLines(content, diagnosisConclusion, "🟡", "⚠️ ");
            hasRisk = true;
        }
        if (!hasRisk) {
            content.append("⚠️ 当前未触发明显风险预警规则。\n");
        }
        content.append("\n");

        content.append("### 运营建议\n");
        content.append("1. 优先处理销售额下滑与未出单链接占比较高的问题。\n");
        content.append("2. 重点跟进下跌链接和未出单链接，及时调整 listing、价格和投放策略。\n");
        content.append("3. 对已表现较好的上涨链接，继续承接流量，避免结构继续失衡。\n");

        return content.toString();
    }

    private StructuredDiagnosis parseStructuredDiagnosis(String operationDiagnosisText,
                                                         String diagnosisConclusionText) {
        StructuredDiagnosis result = new StructuredDiagnosis();
        result.setRedAlerts(extractDiagnosisConclusionLines(diagnosisConclusionText, "🔴"));
        result.setYellowAlerts(extractDiagnosisConclusionLines(diagnosisConclusionText, "🟡"));
        result.setGreenHighlights(extractDiagnosisConclusionLines(diagnosisConclusionText, "🟢"));
        result.setCoreConclusions(extractSectionLines(operationDiagnosisText, "### 核心结论", "### 业务本质"));
        result.setBusinessEssence(extractSectionLines(operationDiagnosisText, "### 业务本质", "### 多维度评估"));
        result.setMultiDimensionalEvaluations(extractSectionLines(
                operationDiagnosisText,
                "### 多维度评估",
                "### 亮点"
        ));
        result.setHighlights(extractSectionLines(operationDiagnosisText, "### 亮点", "### 风险预警"));
        result.setRiskWarnings(extractSectionLines(operationDiagnosisText, "### 风险预警", "### 运营建议"));
        result.setOperationSuggestions(extractSectionLines(operationDiagnosisText, "### 运营建议", null));
        return result;
    }

    private List<String> extractSectionLines(String content, String startMarker, String endMarker) {
        if (content == null || content.trim().isEmpty()) {
            return Collections.emptyList();
        }
        int start = content.indexOf(startMarker);
        if (start < 0) {
            return Collections.emptyList();
        }
        start += startMarker.length();
        int end = endMarker == null ? content.length() : content.indexOf(endMarker, start);
        if (end < 0) {
            end = content.length();
        }

        String section = content.substring(start, end).trim();
        if (section.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        for (String line : section.split("\\r?\\n")) {
            String cleaned = line.replaceFirst("^[0-9]+\\.", "").replace("✅", "").replace("⚠️", "").trim();
            if (!cleaned.isEmpty()) {
                result.add(cleaned);
            }
        }
        return result;
    }

    private List<String> extractDiagnosisConclusionLines(String diagnosisConclusion, String tag) {
        if (diagnosisConclusion == null || diagnosisConclusion.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String line : diagnosisConclusion.split("\\r?\\n")) {
            if (!line.startsWith(tag)) {
                continue;
            }
            String cleaned = line
                    .replace(tag, "")
                    .replace("**", "")
                    .replace("需立即关注：", "")
                    .replace("值得关注：", "")
                    .replace("本周运营状态良好！", "")
                    .trim();
            if (cleaned.isEmpty()) {
                continue;
            }
            for (String item : cleaned.split("[，,]")) {
                String value = item.trim();
                if (!value.isEmpty()) {
                    result.add(value);
                }
            }
        }
        return result;
    }

    private TriggeredRules evaluateFallbackRules(SmartReportRequestDTO req) {
        int todayTotalLinks = calculateTodayTotalLinks(req);
        BigDecimal revenueChange = calculateRevenueChange(req);

        TriggeredRules result = new TriggeredRules();
        result.setRedAlerts(new ArrayList<>());
        result.setYellowAlerts(new ArrayList<>());
        result.setGreenAlerts(new ArrayList<>());

        if (revenueChange.compareTo(BigDecimal.ZERO) < 0 && revenueChange.abs().compareTo(req.getR1Threshold()) >= 0) {
            result.getRedAlerts().add("销售额暴跌");
        }

        if (todayTotalLinks > 0) {
            BigDecimal noOrderRatio = calculateLinkRatio(req.getTodayNoOrderLinks(), todayTotalLinks);
            if (noOrderRatio.compareTo(req.getR2Threshold()) >= 0) {
                result.getRedAlerts().add("大量链接滞销");
            }
        }

        if (revenueChange.compareTo(BigDecimal.ZERO) < 0 && revenueChange
                .abs()
                .compareTo(req.getY1Threshold()) >= 0 && revenueChange.abs().compareTo(req.getR1Threshold()) < 0) {
            result.getYellowAlerts().add("销售额小幅下滑");
        }

        if (BigDecimal.valueOf(safeInt(req.getTodayFallingLinks())).compareTo(BigDecimal
                .valueOf(safeInt(req.getTodayRisingLinks()))
                .multiply(defaultDecimal(req.getY2Ratio()))) > 0) {
            result.getYellowAlerts().add("下跌链接偏多");
        }

        if (safeInt(req.getTodayNoOrderLinks()) >= 1 && todayTotalLinks > 0) {
            BigDecimal noOrderRatio = calculateLinkRatio(req.getTodayNoOrderLinks(), todayTotalLinks);
            if (noOrderRatio.compareTo(req.getR2Threshold()) < 0) {
                result.getYellowAlerts().add("存在滞销链接");
            }
        }

        if (revenueChange.compareTo(req.getG1Threshold()) >= 0) {
            result.getGreenAlerts().add("销售额增长");
        }

        if (todayTotalLinks > 0) {
            BigDecimal risingRatio = calculateLinkRatio(req.getTodayRisingLinks(), todayTotalLinks);
            if (risingRatio.compareTo(req.getG2Threshold()) >= 0) {
                result.getGreenAlerts().add("爆款涌现");
            }
        }

        return result;
    }

    private void appendLabeledLines(StringBuilder builder, String diagnosisConclusion, String colorTag, String prefix) {
        String[] lines = diagnosisConclusion.split("\\r?\\n");
        for (String line : lines) {
            if (!line.startsWith(colorTag)) {
                continue;
            }
            int index = line.lastIndexOf("：");
            String content = index >= 0 ? line.substring(index + 1).trim() : line.trim();
            if (content.isEmpty()) {
                continue;
            }
            String[] items = content.split("[，,]");
            for (String item : items) {
                if (!item.trim().isEmpty()) {
                    builder.append(prefix).append(item.trim()).append("\n");
                }
            }
        }
    }

    private BigDecimal calculateRevenueChange(SmartReportRequestDTO req) {
        BigDecimal yesterdayRevenue = defaultDecimal(req.getYesterdayRevenue());
        BigDecimal todayRevenue = defaultDecimal(req.getTodayRevenue());
        if (yesterdayRevenue.compareTo(BigDecimal.ZERO) <= 0) {
            return todayRevenue.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        }
        return todayRevenue.subtract(yesterdayRevenue).multiply(BigDecimal.valueOf(100)).divide(
                yesterdayRevenue,
                2,
                RoundingMode.HALF_UP
        );
    }

    private int calculateTodayTotalLinks(SmartReportRequestDTO req) {
        return safeInt(req.getTodayRisingLinks()) + safeInt(req.getTodayFallingLinks()) + safeInt(req.getTodayNoOrderLinks());
    }

    private int calculateYesterdayTotalLinks(SmartReportRequestDTO req) {
        return safeInt(req.getYesterdayRisingLinks()) + safeInt(req.getYesterdayFallingLinks()) + safeInt(req.getYesterdayNoOrderLinks());
    }

    private BigDecimal calculateLinkRatio(Integer count, Integer totalLinks) {
        if (totalLinks == null || totalLinks <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal
                .valueOf(safeInt(count))
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalLinks), 2, RoundingMode.HALF_UP);
    }

    private String formatPercentTrend(BigDecimal value) {
        BigDecimal safeValue = defaultDecimal(value).abs().setScale(2, RoundingMode.HALF_UP);
        return (defaultDecimal(value).compareTo(BigDecimal.ZERO) >= 0 ? "↑" : "↓") + safeValue.toPlainString() + "%";
    }

    private String formatAmount(BigDecimal value) {
        return defaultDecimal(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    @Data
    private static class TriggeredRules {
        private List<String> redAlerts;
        private List<String> yellowAlerts;
        private List<String> greenAlerts;
    }

    @Data
    private static class StructuredDiagnosis {
        private List<String> redAlerts;
        private List<String> yellowAlerts;
        private List<String> greenHighlights;
        private List<String> coreConclusions;
        private List<String> businessEssence;
        private List<String> multiDimensionalEvaluations;
        private List<String> highlights;
        private List<String> riskWarnings;
        private List<String> operationSuggestions;
    }
}
