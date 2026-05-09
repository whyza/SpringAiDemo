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
import java.util.List;

/**
 * 智能报告引擎服务实现
 *
 * @author lingyi
 */
@Slf4j
@Service
public class SmartReportEngineServiceImpl implements SmartReportEngineService {

    private static final String DEFAULT_CONCLUSION = "无明显异常";
    private static final String RED_PREFIX = "🔴 红色：立即关注";
    private static final String YELLOW_PREFIX = "🟡 黄色：值得注意";
    private static final String GREEN_PREFIX = "🟢 绿色：运营亮点";

    @Resource
    private AiAnalysisService aiAnalysisService;

    @Override
    public SmartReportResultVO analyze(SmartReportRequestDTO request) {
        log.info("智能报告分析开始，日期={}", request.getReportDate());
        request.applyDefaults();

        TriggeredRules triggeredRules = evaluateRules(request);
        String diagnosisConclusion = buildDiagnosisConclusion(triggeredRules);
        AiContentResult aiContentResult = generateOperationDiagnosis(request, triggeredRules, diagnosisConclusion);

        SmartReportResultVO result = new SmartReportResultVO();
        result.setOperationDiagnosis(aiContentResult.getContent());
        result.setDiagnosisConclusion(diagnosisConclusion);

        log.info(
                "智能报告分析完成，红色{}条，黄色{}条，绿色{}条，fallbackUsed={}",
                triggeredRules.getRedAlerts().size(),
                triggeredRules.getYellowAlerts().size(),
                triggeredRules.getGreenAlerts().size(),
                aiContentResult.getFallbackUsed()
        );
        return result;
    }

    private TriggeredRules evaluateRules(SmartReportRequestDTO req) {
        int totalLinks = safeInt(req.getTotalLinks());
        BigDecimal revenueChange = defaultDecimal(req.getTodayRevenueChange());

        TriggeredRules result = new TriggeredRules();
        result.setRedAlerts(new ArrayList<>());
        result.setYellowAlerts(new ArrayList<>());
        result.setGreenAlerts(new ArrayList<>());

        if (revenueChange.compareTo(BigDecimal.ZERO) < 0
                && revenueChange.abs().compareTo(req.getR1Threshold()) >= 0) {
            result.getRedAlerts().add("销售额大幅下滑预警");
        }

        if (totalLinks > 0) {
            BigDecimal noOrderRatio = calculateLinkRatio(req.getNoOrderLinks(), totalLinks);
            if (noOrderRatio.compareTo(req.getR2Threshold()) >= 0) {
                result.getRedAlerts().add("大量链接滞销预警");
            }
        }

        if (defaultDecimal(req.getProfitRate()).compareTo(req.getR3ProfitMin()) < 0
                && defaultDecimal(req.getProfitRateChange()).compareTo(BigDecimal.ZERO) < 0
                && defaultDecimal(req.getProfitRateChange()).abs().compareTo(req.getR3ProfitDrop()) >= 0) {
            result.getRedAlerts().add("利润率严重恶化预警");
        }

        if (revenueChange.compareTo(BigDecimal.ZERO) < 0
                && revenueChange.abs().compareTo(req.getY1Threshold()) >= 0
                && revenueChange.abs().compareTo(req.getR1Threshold()) < 0) {
            result.getYellowAlerts().add("销售额下滑关注");
        }

        if (safeInt(req.getFallingLinks()) > safeInt(req.getRisingLinks()) * defaultDecimal(req.getY2Ratio()).intValue()) {
            result.getYellowAlerts().add("链接下跌趋势明显");
        }

        if (safeInt(req.getNoOrderLinks()) >= 1 && totalLinks > 0) {
            BigDecimal noOrderRatio = calculateLinkRatio(req.getNoOrderLinks(), totalLinks);
            if (noOrderRatio.compareTo(req.getR2Threshold()) < 0) {
                result.getYellowAlerts().add("存在滞销链接");
            }
        }

        if (revenueChange.compareTo(req.getG1Threshold()) >= 0) {
            result.getGreenAlerts().add("销售额稳步增长");
        }

        if (totalLinks > 0) {
            BigDecimal risingRatio = calculateLinkRatio(req.getRisingLinks(), totalLinks);
            if (risingRatio.compareTo(req.getG2Threshold()) >= 0) {
                result.getGreenAlerts().add("多链接表现亮眼");
            }
        }

        if (defaultDecimal(req.getProfitRate()).compareTo(req.getG3Threshold()) >= 0
                && defaultDecimal(req.getProfitRateChange()).compareTo(BigDecimal.ZERO) > 0) {
            result.getGreenAlerts().add("利润率表现优秀");
        }

        return result;
    }

    private String buildDiagnosisConclusion(TriggeredRules triggeredRules) {
        List<String> lines = new ArrayList<>();
        lines.add("### 诊断结论");
        if (!triggeredRules.getRedAlerts().isEmpty()) {
            lines.add(RED_PREFIX + "：" + String.join("、", triggeredRules.getRedAlerts()));
        }
        if (!triggeredRules.getYellowAlerts().isEmpty()) {
            lines.add(YELLOW_PREFIX + "：" + String.join("、", triggeredRules.getYellowAlerts()));
        }
        if (!triggeredRules.getGreenAlerts().isEmpty()) {
            lines.add(GREEN_PREFIX + "：" + String.join("、", triggeredRules.getGreenAlerts()));
        }
        if (lines.size() == 1) {
            lines.add(DEFAULT_CONCLUSION);
        }
        return String.join("\n", lines);
    }

    private AiContentResult generateOperationDiagnosis(SmartReportRequestDTO req,
                                                       TriggeredRules triggeredRules,
                                                       String diagnosisConclusion) {
        String systemPrompt = """
                你是一位专业的电商运营顾问，正在为卖家生成当天店铺运营诊断。

                1. 禁止编造任何数据中不存在的数字，只能使用输入中提供的数据
                2. 禁止凭空推测，不要写“可能”“或许”“预计”等推测性内容
                3. 禁止自行衍生新的业务数据，只能解读现有输入
                4. 数据不足以支撑分析的内容，直接跳过，不要硬写

                ## 输出要求
                - 分析口径必须聚焦当天表现，不要写成周报、阶段复盘或长期总结
                - 使用简体中文输出，语气专业但不生硬
                - 直接输出“运营诊断”正文，不要加额外开场白
                - 必须覆盖以下结构，且每个区块要有内容：
                ### 核心结论
                用 3 到 5 条要点总结今天表现，每条 1 到 2 句，并引用输入中的具体数字
                ### 业务本质
                用 2 到 3 条说明销售额、订单量、利润率、链接表现之间的直接关系
                ### 多维度评估
                至少覆盖增长质量、商品结构、SKU 健康度三个维度
                ### 亮点
                有亮点就写 2 到 4 条，没有则简要说明暂无明显亮点
                ### 风险预警
                有风险就写 2 到 4 条，没有则简要说明暂无明显风险
                ### 风险预警部分
                必须给出 3 到 5 条行动建议，按优先级表达
                - 使用“✅”表示亮点
                - 使用“⚠️”表示风险
                - 所有数字必须直接来自输入
                - 深入分析是说明数据之间的关系，不是发明新数字
                """;

        String userPrompt = buildUserPrompt(req, triggeredRules, diagnosisConclusion);
        try {
            return new AiContentResult(aiAnalysisService.callAiAnalysis(systemPrompt, userPrompt), Boolean.FALSE);
        } catch (Exception e) {
            log.warn("AI 调用失败，降级使用结构化模板", e);
            return new AiContentResult(buildFallbackOperationDiagnosis(req, triggeredRules), Boolean.TRUE);
        }
    }

    private String buildUserPrompt(SmartReportRequestDTO req,
                                   TriggeredRules triggeredRules,
                                   String diagnosisConclusion) {
        return String.format(
                """
                        ## 当天数据
                        当天销售额：%.2f USD
                        当天销售额变化：%s
                        当天订单量：%d 单
                        总链接数：%d 条
                        销量上涨链接数：%d 条
                        销量下跌链接数：%d 条
                        未出单链接数：%d 条
                        利润率：%.2f%%
                        利润率变化：%s

                        ## 规则结果
                        红色预警：%s
                        黄色关注：%s
                        绿色亮点：%s

                        ## 诊断结论
                        %s

                        请基于以上内容输出完整的运营诊断正文。
                        """,
                defaultDecimal(req.getTodayRevenue()).doubleValue(),
                formatPercentTrend(req.getTodayRevenueChange()),
                safeInt(req.getTodayOrders()),
                safeInt(req.getTotalLinks()),
                safeInt(req.getRisingLinks()),
                safeInt(req.getFallingLinks()),
                safeInt(req.getNoOrderLinks()),
                defaultDecimal(req.getProfitRate()).doubleValue(),
                formatPointTrend(req.getProfitRateChange()),
                formatRuleList(triggeredRules.getRedAlerts()),
                formatRuleList(triggeredRules.getYellowAlerts()),
                formatRuleList(triggeredRules.getGreenAlerts()),
                diagnosisConclusion
        );
    }

    private String buildFallbackOperationDiagnosis(SmartReportRequestDTO req, TriggeredRules triggeredRules) {
        String todayRevenue = defaultDecimal(req.getTodayRevenue()).setScale(2, RoundingMode.HALF_UP).toPlainString();
        String revenueChange = formatPercentTrend(req.getTodayRevenueChange());
        String profitChange = formatPointTrend(req.getProfitRateChange());

        StringBuilder content = new StringBuilder();
        content.append("### 核心结论\n");
        content.append("1. 今天销售额为 ").append(todayRevenue).append(" USD，销售额变化 ").append(revenueChange)
                .append("，订单量 ").append(safeInt(req.getTodayOrders())).append(" 单。\n");
        content.append("2. 总链接数 ").append(safeInt(req.getTotalLinks())).append(" 条，其中上涨链接 ")
                .append(safeInt(req.getRisingLinks())).append(" 条，下跌链接 ")
                .append(safeInt(req.getFallingLinks())).append(" 条，未出单链接 ")
                .append(safeInt(req.getNoOrderLinks())).append(" 条。\n");
        content.append("3. 利润率 ").append(defaultDecimal(req.getProfitRate()).setScale(2, RoundingMode.HALF_UP).toPlainString())
                .append("%，利润率变化 ").append(profitChange).append("。\n\n");

        content.append("### 业务本质\n");
        content.append("1. 销售额变化与订单量表现需要结合看，当前订单量为 ").append(safeInt(req.getTodayOrders()))
                .append(" 单，直接反映今天的成交结果。\n");
        content.append("2. 上涨链接 ").append(safeInt(req.getRisingLinks())).append(" 条、下跌链接 ")
                .append(safeInt(req.getFallingLinks())).append(" 条，说明今天链接结构存在明显分化。\n");
        content.append("3. 未出单链接 ").append(safeInt(req.getNoOrderLinks())).append(" 条，体现当前低效链接规模。\n\n");

        content.append("### 多维度评估\n");
        content.append("- 增长质量：销售额变化 ").append(revenueChange).append("，订单量 ").append(safeInt(req.getTodayOrders())).append(" 单。\n");
        content.append("- 商品结构：上涨链接 ").append(safeInt(req.getRisingLinks())).append(" 条，下跌链接 ")
                .append(safeInt(req.getFallingLinks())).append(" 条。\n");
        content.append("- SKU 健康度：未出单链接 ").append(safeInt(req.getNoOrderLinks())).append(" 条，总链接数 ")
                .append(safeInt(req.getTotalLinks())).append(" 条。\n\n");

        content.append("### 亮点\n");
        if (!triggeredRules.getGreenAlerts().isEmpty()) {
            for (String rule : triggeredRules.getGreenAlerts()) {
                content.append("✅ ").append(rule).append("\n");
            }
        } else {
            content.append("✅ 暂无明显亮点，今天更偏向结构性观察。\n");
        }
        content.append("\n");

        content.append("### 风险预警\n");
        if (!triggeredRules.getRedAlerts().isEmpty()) {
            for (String rule : triggeredRules.getRedAlerts()) {
                content.append("⚠️ ").append(rule).append("\n");
            }
        }
        if (!triggeredRules.getYellowAlerts().isEmpty()) {
            for (String rule : triggeredRules.getYellowAlerts()) {
                content.append("⚠️ ").append(rule).append("\n");
            }
        }
        if (triggeredRules.getRedAlerts().isEmpty() && triggeredRules.getYellowAlerts().isEmpty()) {
            content.append("⚠️ 暂无明显风险，但仍需持续观察销售额、利润率和链接表现变化。\n");
        }
        content.append("\n");

        content.append("### 风险预警部分\n");
        content.append("1. 最高优先级：优先处理销售额下滑、滞销链接和利润率恶化对应商品，先做针对性排查。\n");
        content.append("2. 次优先级：重点盯下跌链接与未出单链接，及时调整 listing、价格和投放。\n");
        content.append("3. 可同步推进：把上涨链接继续放大流量承接，避免整体结构继续失衡。\n");
        content.append("4. 经营动作：持续跟踪利润率 ").append(defaultDecimal(req.getProfitRate()).setScale(2, RoundingMode.HALF_UP).toPlainString())
                .append("% 与变化 ").append(profitChange).append("，避免后续进一步恶化。\n");

        return content.toString();
    }

    private BigDecimal calculateLinkRatio(Integer count, Integer totalLinks) {
        if (totalLinks == null || totalLinks <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(safeInt(count))
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalLinks), 2, RoundingMode.HALF_UP);
    }

    private String formatRuleList(List<String> rules) {
        return rules == null || rules.isEmpty() ? "无" : String.join("、", rules);
    }

    private String formatPercentTrend(BigDecimal value) {
        BigDecimal safeValue = defaultDecimal(value).abs().setScale(2, RoundingMode.HALF_UP);
        return (defaultDecimal(value).compareTo(BigDecimal.ZERO) >= 0 ? "↑" : "↓") + safeValue.toPlainString() + "%";
    }

    private String formatPointTrend(BigDecimal value) {
        BigDecimal safeValue = defaultDecimal(value).abs().setScale(2, RoundingMode.HALF_UP);
        return (defaultDecimal(value).compareTo(BigDecimal.ZERO) >= 0 ? "↑" : "↓") + safeValue.toPlainString() + "pt";
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
    private static class AiContentResult {
        private final String content;
        private final Boolean fallbackUsed;
    }
}
