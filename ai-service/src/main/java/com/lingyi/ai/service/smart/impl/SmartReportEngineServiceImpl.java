package com.lingyi.ai.service.smart.impl;

import com.lingyi.ai.dal.dataobject.SmartRuleConfigDO;
import com.lingyi.ai.model.dto.SmartReportRequestDTO;
import com.lingyi.ai.model.vo.SmartReportResultVO;
import com.lingyi.ai.service.ai.AiAnalysisService;
import com.lingyi.ai.service.smart.SmartReportEngineService;
import com.lingyi.ai.service.smart.SmartReportProgress;
import com.lingyi.ai.service.smart.SmartRuleConfigService;
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

    private static final String MARKER_OP_DIAGNOSIS = "## 运营诊断报告";

    @Resource
    private AiAnalysisService aiAnalysisService;

    @Resource
    private SmartRuleConfigService smartRuleConfigService;

    @Override
    public SmartReportResultVO analyze(SmartReportRequestDTO request) {
        return analyze(request, SmartReportProgress.NOOP);
    }

    @Override
    public SmartReportResultVO analyze(SmartReportRequestDTO request, java.util.function.Consumer<String> progress) {
        loadGlobalThresholds(request);
        request.applyDefaults();

        progress.accept(SmartReportProgress.AI_ANALYSIS);
        String fullResponse = generateFullReport(request);

        progress.accept(SmartReportProgress.PARSING);
        AiResponseParts parts = splitAiResponse(fullResponse);

        SmartReportResultVO result = buildResult(parts);
        progress.accept(SmartReportProgress.COMPLETE);
        return result;
    }

    // ==================== 阈值加载 ====================

    /**
     * 优先从数据库全局规则配置加载阈值，前端未传值时使用
     */
    private void loadGlobalThresholds(SmartReportRequestDTO request) {
        SmartRuleConfigDO globalConfig = smartRuleConfigService.loadConfig();
        if (globalConfig == null) {
            return;
        }
        if (request.getR1Threshold() == null) request.setR1Threshold(globalConfig.getR1Threshold());
        if (request.getR2Threshold() == null) request.setR2Threshold(globalConfig.getR2Threshold());
        if (request.getR3Threshold() == null) request.setR3Threshold(globalConfig.getR3Threshold());
        if (request.getY1Threshold() == null) request.setY1Threshold(globalConfig.getY1Threshold());
        if (request.getG1Threshold() == null) request.setG1Threshold(globalConfig.getG1Threshold());
        if (request.getG2Threshold() == null) request.setG2Threshold(globalConfig.getG2Threshold());
    }

    // ==================== AI 调用 ====================

    private String generateFullReport(SmartReportRequestDTO req) {
        String systemPrompt = """
                你是一位亚马逊运营诊断顾问。请根据输入数据生成完整的店铺诊断报告。

                ## 第一部分：诊断结论 - 严格遵循以下格式

                规则要求：
                1. 只允许使用以下规则名称：
                红色：销售额大幅下滑、大量链接下跌、大量链接未出单
                黄色：销售额小幅下滑、部分链接下跌、部分链接未出单
                绿色：销售额稳步增长、上涨链接占比亮眼
                2. 红色文案固定为：🔴 **需立即关注：** {规则名称，逗号分隔}
                3. 黄色文案固定为：🟡 **值得关注：** {规则名称，逗号分隔}
                4. 绿色文案固定为：🟢 **本周运营状态良好！** {规则名称，逗号分隔}
                5. 如果多级别同时触发，必须按红色、黄色、绿色顺序逐行输出。
                6. 如果只有某一级触发，则只输出对应那一行。
                7. 如果没有任何规则触发，只输出：📋 本日数据平稳，暂无明显异常
                8. 只能依据输入数据和规则阈值判断，不能编造事实，不能补充解释。

                ## 第二部分：运营诊断报告 - 严格遵循以下规则

                1. 禁止编造任何输入中不存在的数据，只能使用明确提供的数据
                2. 禁止使用"系统检测"、"算法分析"、"AI 分析"等机械话术
                3. 必须引用输入中的具体数字进行分析，不得笼统描述
                4. 总字数控制在 500 字以内，简洁精炼
                5. 语气专业通俗，站在卖家视角，像资深运营在跟卖家沟通
                6. 数据不足以支撑某部分分析时，直接跳过，不要硬写
                7. 直接输出报告正文，不要加"根据提供的数据"、"基于以上数据"等开场白
                8. 每个指标须附带简明计算结果（另起一行展示），格式如：
                   - "销售额降幅 32.10%（昨日 ¥8,100 → 今日 ¥5,500）"
                   - "未出单占比 40.54%（未出单 75 / 总链接 185）"

                输出结构：
                ① 整体表现：对比昨日今日销售额、订单降幅，精准计算客单价；通过客单价判断下滑是否为降价导致，直白说明是流量/转化真实缩水，结合自定义规则判定行情预警等级。每个指标另起一行展示简明计算结果。
                ② 链接结构分析：用极简表格展示上涨、下跌、平稳、未出单链接 + 数量 + 占比；标注风险警告，对比昨日、今日未出单链接差值，算出新增哑火链接数量，判断店铺链接健康度、产品支撑能力。风险项用 ⚠️ 标记。
                ③ 异常逻辑判断：根据涨跌链接分布，判断是个别链接问题还是店铺整体性下跌；分析行情下跌底层原因，罗列需要排查的亚马逊常见诱因。风险项用 ⚠️ 标记。
                ④ 运营建议（必须有）：全部为低风险、可落地实操动作；区分排查优先级，区分平台原因/店铺原因，弱势行情禁止大幅改动，以排查、维稳、观察为主，语言直白通俗。正向建议用 ✅ 开头。

                ## 最终输出格式（严格按此顺序）
                ## 诊断结论
                🔴 **需立即关注：** ...
                🟡 **值得关注：** ...
                🟢 **本周运营状态良好！** ...

                ## 运营诊断报告
                ① ...
                ② ...
                ③ ...
                ④ ...
                """;

        String userPrompt = buildFullReportPrompt(req);
        try {
            return aiAnalysisService.callAiAnalysis(systemPrompt, userPrompt);
        } catch (Exception e) {
            log.warn("AI 生成完整报告失败，使用本地规则降级", e);
            String fallbackDC = buildFallbackDiagnosisConclusion(req);
            String fallbackOD = buildFallbackOperationDiagnosis(req, fallbackDC);
            return "## 诊断结论\n" + fallbackDC + "\n\n## 运营诊断报告\n" + fallbackOD;
        }
    }

    private String buildFullReportPrompt(SmartReportRequestDTO req) {
        int todayTotalLinks = calculateTodayTotalLinks(req);
        return String.format(
                """
                        ## 今日数据汇总
                        昨日销售额：%s 元
                        今日销售额：%s 元
                        昨日订单量：%d
                        今日订单量：%d
                        昨天销量上涨链接数：%d
                        当天销量上涨链接数：%d
                        昨天销量下跌链接数：%d
                        当天销量下跌链接数：%d
                        当天总链接数：%d
                        当天未出单链接数：%d
                        昨天未出单链接数：%d

                        ## 客户自定义规则（阈值百分比均为绝对值，AI 自行计算判断是否命中）
                        - 销售额大幅下滑：降幅 = (昨日 - 当天) ÷ 昨日 × 100%%，>= %s%% 触发
                        - 大量链接下跌：下跌占比 = 当天下跌 ÷ 总链接 × 100%%，>= %s%% 触发
                        - 大量链接未出单：未出单占比 = 当天未出单 ÷ 总链接 × 100%%，>= %s%% 触发
                        - 销售额小幅下滑：降幅 = (昨日 - 当天) ÷ 昨日 × 100%%，>= %s%% 且 < R1 时触发
                        - 部分链接下跌：下跌占比 = 当天下跌 ÷ 总链接 × 100%%，> 0 且 < %s%% 触发
                        - 部分链接未出单：未出单占比 = 当天未出单 ÷ 总链接 × 100%%，> 0 且 < %s%% 触发
                        - 销售额稳步增长：增幅 = (当天 - 昨日) ÷ 昨日 × 100%%，>= %s%% 触发
                        - 上涨链接占比亮眼：上涨占比 = 当天上涨 ÷ 总链接 × 100%%，>= %s%% 触发

                        请基于以上数据和规则，生成完整的店铺诊断报告。
                        """,
                formatAmount(req.getYesterdayRevenue()),
                formatAmount(req.getTodayRevenue()),
                safeInt(req.getYesterdayOrders()),
                safeInt(req.getTodayOrders()),
                safeInt(req.getYesterdayRisingLinks()),
                safeInt(req.getTodayRisingLinks()),
                safeInt(req.getYesterdayFallingLinks()),
                safeInt(req.getTodayFallingLinks()),
                todayTotalLinks,
                safeInt(req.getTodayNoOrderLinks()),
                safeInt(req.getYesterdayNoOrderLinks()),
                formatAmount(req.getR1Threshold()),
                formatAmount(req.getR2Threshold()),
                formatAmount(req.getR3Threshold()),
                formatAmount(req.getY1Threshold()),
                formatAmount(req.getR2Threshold()),
                formatAmount(req.getR3Threshold()),
                formatAmount(req.getG1Threshold()),
                formatAmount(req.getG2Threshold())
        );
    }

    // ==================== AI 响应解析 ====================

    @Data
    private static class AiResponseParts {
        private String diagnosisConclusionText;
        private String operationDiagnosisText;
    }

    private AiResponseParts splitAiResponse(String fullResponse) {
        AiResponseParts parts = new AiResponseParts();
        int idx = fullResponse.indexOf(MARKER_OP_DIAGNOSIS);
        if (idx >= 0) {
            parts.setDiagnosisConclusionText(
                    fullResponse.substring(0, idx).replace("## 诊断结论", "").trim());
            parts.setOperationDiagnosisText(
                    fullResponse.substring(idx + MARKER_OP_DIAGNOSIS.length()).trim());
        } else {
            parts.setDiagnosisConclusionText(fullResponse.trim());
            parts.setOperationDiagnosisText("");
        }
        return parts;
    }

    // ==================== 结果组装 ====================

    private SmartReportResultVO buildResult(AiResponseParts parts) {
        StructuredDiagnosis parsed = parseStructuredDiagnosis(
                parts.getOperationDiagnosisText(),
                parts.getDiagnosisConclusionText());

        SmartReportResultVO result = new SmartReportResultVO();
        result.setDiagnosisConclusionText(parts.getDiagnosisConclusionText());
        result.setOperationDiagnosisText(parts.getOperationDiagnosisText());

        SmartReportResultVO.DiagnosisConclusionVO dc = new SmartReportResultVO.DiagnosisConclusionVO();
        dc.setRedAlerts(parsed.getRedAlerts());
        dc.setYellowAlerts(parsed.getYellowAlerts());
        dc.setGreenHighlights(parsed.getGreenHighlights());
        result.setDiagnosisConclusions(dc);

        SmartReportResultVO.OperationDiagnosisVO od = new SmartReportResultVO.OperationDiagnosisVO();
        od.setOverallPerformance(parsed.getOverallPerformance());
        od.setLinkStructureAnalysis(parsed.getLinkStructureAnalysis());
        od.setAnomalyLogicJudgment(parsed.getAnomalyLogicJudgment());
        od.setOperationSuggestions(parsed.getOperationSuggestions());
        result.setOperationDiagnosis(od);

        return result;
    }

    private StructuredDiagnosis parseStructuredDiagnosis(String operationDiagnosisText,
                                                         String diagnosisConclusionText) {
        StructuredDiagnosis result = new StructuredDiagnosis();
        result.setRedAlerts(extractDiagnosisConclusionLines(diagnosisConclusionText, "🔴"));
        result.setYellowAlerts(extractDiagnosisConclusionLines(diagnosisConclusionText, "🟡"));
        result.setGreenHighlights(extractDiagnosisConclusionLines(diagnosisConclusionText, "🟢"));
        result.setOverallPerformance(extractSectionByNumber(operationDiagnosisText, "①", "②"));
        result.setLinkStructureAnalysis(extractSectionByNumber(operationDiagnosisText, "②", "③"));
        result.setAnomalyLogicJudgment(extractSectionByNumber(operationDiagnosisText, "③", "④"));
        result.setOperationSuggestions(extractSectionByNumber(operationDiagnosisText, "④", null));
        return result;
    }

    private List<String> extractSectionByNumber(String content, String currentNum, String nextNum) {
        if (content == null || content.trim().isEmpty()) {
            return Collections.emptyList();
        }
        int fromIdx = content.indexOf(currentNum);
        if (fromIdx < 0) {
            return Collections.emptyList();
        }
        int toIdx = (nextNum == null) ? content.length() : content.indexOf(nextNum, fromIdx + 1);
        if (toIdx < 0) {
            toIdx = content.length();
        }
        String section = content.substring(fromIdx + 1, toIdx).trim();
        // 跳过标签前缀（如 "整体表现：" 或换行后的标签行）
        int colonIdx = section.indexOf("：");
        if (colonIdx > 0 && colonIdx < 30) {
            section = section.substring(colonIdx + 1).trim();
        } else {
            int nl = section.indexOf("\n");
            if (nl > 0 && nl < 30) {
                section = section.substring(nl + 1).trim();
            }
        }
        if (section.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String line : section.split("\\r?\\n")) {
            String cleaned = line.replaceFirst("^[0-9]+\\.\\s*", "").replace("✅", "").replace("⚠️", "").trim();
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

    // ==================== 降级逻辑 ====================

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
        int noOrderDiff = safeInt(req.getTodayNoOrderLinks()) - safeInt(req.getYesterdayNoOrderLinks());

        StringBuilder sb = new StringBuilder();

        sb.append("① 整体表现：");
        sb.append("今日销售额 ").append(formatAmount(req.getTodayRevenue())).append(" 元，昨日销售额 ")
                .append(formatAmount(req.getYesterdayRevenue())).append(" 元，变化 ").append(revenueChange).append("。");
        sb.append("今日订单量 ").append(safeInt(req.getTodayOrders())).append(" 单，昨日订单量 ")
                .append(safeInt(req.getYesterdayOrders())).append(" 单。");
        sb.append("今日总链接数 ").append(todayTotalLinks).append("，昨日总链接数 ").append(yesterdayTotalLinks).append("。\n\n");

        sb.append("② 链接结构分析：");
        sb.append("上涨 ").append(safeInt(req.getTodayRisingLinks())).append(" 个，下跌 ")
                .append(safeInt(req.getTodayFallingLinks())).append(" 个，未出单 ")
                .append(safeInt(req.getTodayNoOrderLinks())).append(" 个，总链接 ").append(todayTotalLinks).append(" 个。");
        sb.append("昨日未出单 ").append(safeInt(req.getYesterdayNoOrderLinks())).append(" 个，未出单变化 ")
                .append(noOrderDiff).append(" 个。\n\n");

        sb.append("③ 异常逻辑判断：");
        boolean hasRed = diagnosisConclusion.contains("🔴");
        boolean hasYellow = diagnosisConclusion.contains("🟡");
        if (hasRed || hasYellow) {
            sb.append("触发预警规则，需关注异常链接。");
            if (hasRed) sb.append(" ⚠️ 存在红色预警，需立即排查。");
            if (hasYellow) sb.append(" ⚠️ 存在黄色预警，建议关注。");
        } else {
            sb.append("暂未发现明显异常，链接结构相对稳定。");
        }
        sb.append("\n\n");

        sb.append("④ 运营建议：\n");
        sb.append("✅ 优先排查销售额变化原因，检查客单价是否稳定。弱势行情以排查为主，避免大幅改动。\n");
        sb.append("✅ 重点跟进下跌链接和未出单链接，及时优化 listing、广告投放和竞价策略，区分平台原因还是店铺原因。\n");
        sb.append("✅ 对已表现较好的上涨链接，继续承接流量，维持广告预算，避免因结构失衡拖累整体表现。\n");

        return sb.toString();
    }

    private TriggeredRules evaluateFallbackRules(SmartReportRequestDTO req) {
        int todayTotalLinks = calculateTodayTotalLinks(req);
        BigDecimal revenueChange = calculateRevenueChange(req);

        TriggeredRules result = new TriggeredRules();
        result.setRedAlerts(new ArrayList<>());
        result.setYellowAlerts(new ArrayList<>());
        result.setGreenAlerts(new ArrayList<>());

        if (revenueChange.compareTo(BigDecimal.ZERO) < 0 && revenueChange.abs().compareTo(req.getR1Threshold()) >= 0) {
            result.getRedAlerts().add("销售额大幅下滑");
        }
        if (todayTotalLinks > 0) {
            if (calculateLinkRatio(req.getTodayFallingLinks(), todayTotalLinks).compareTo(req.getR2Threshold()) >= 0) {
                result.getRedAlerts().add("大量链接下跌");
            }
            if (calculateLinkRatio(req.getTodayNoOrderLinks(), todayTotalLinks).compareTo(req.getR3Threshold()) >= 0) {
                result.getRedAlerts().add("大量链接未出单");
            }
        }

        if (revenueChange.compareTo(BigDecimal.ZERO) < 0
                && revenueChange.abs().compareTo(req.getY1Threshold()) >= 0
                && revenueChange.abs().compareTo(req.getR1Threshold()) < 0) {
            result.getYellowAlerts().add("销售额小幅下滑");
        }
        if (todayTotalLinks > 0) {
            BigDecimal fallingRatio = calculateLinkRatio(req.getTodayFallingLinks(), todayTotalLinks);
            BigDecimal noOrderRatio = calculateLinkRatio(req.getTodayNoOrderLinks(), todayTotalLinks);
            if (safeInt(req.getTodayFallingLinks()) > 0 && fallingRatio.compareTo(req.getR2Threshold()) < 0) {
                result.getYellowAlerts().add("部分链接下跌");
            }
            if (safeInt(req.getTodayNoOrderLinks()) > 0 && noOrderRatio.compareTo(req.getR3Threshold()) < 0) {
                result.getYellowAlerts().add("部分链接未出单");
            }
        }

        if (revenueChange.compareTo(req.getG1Threshold()) >= 0) {
            result.getGreenAlerts().add("销售额稳步增长");
        }
        if (todayTotalLinks > 0
                && calculateLinkRatio(req.getTodayRisingLinks(), todayTotalLinks).compareTo(req.getG2Threshold()) >= 0) {
            result.getGreenAlerts().add("上涨链接占比亮眼");
        }

        return result;
    }

    // ==================== 计算工具 ====================

    private BigDecimal calculateRevenueChange(SmartReportRequestDTO req) {
        BigDecimal yesterdayRevenue = defaultDecimal(req.getYesterdayRevenue());
        BigDecimal todayRevenue = defaultDecimal(req.getTodayRevenue());
        if (yesterdayRevenue.compareTo(BigDecimal.ZERO) <= 0) {
            return todayRevenue.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        }
        return todayRevenue.subtract(yesterdayRevenue).multiply(BigDecimal.valueOf(100))
                .divide(yesterdayRevenue, 2, RoundingMode.HALF_UP);
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
        return BigDecimal.valueOf(safeInt(count))
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalLinks), 2, RoundingMode.HALF_UP);
    }

    // ==================== 格式化工具 ====================

    private String formatPercentTrend(BigDecimal value) {
        BigDecimal safe = defaultDecimal(value);
        String prefix = safe.compareTo(BigDecimal.ZERO) >= 0 ? "↑" : "↓";
        return prefix + safe.abs().setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
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

    // ==================== 内部模型 ====================

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
        private List<String> overallPerformance;
        private List<String> linkStructureAnalysis;
        private List<String> anomalyLogicJudgment;
        private List<String> operationSuggestions;
    }
}
