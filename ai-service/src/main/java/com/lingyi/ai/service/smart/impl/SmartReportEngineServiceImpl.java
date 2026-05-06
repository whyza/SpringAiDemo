package com.lingyi.ai.service.smart.impl;

import com.lingyi.ai.model.dto.SmartReportRequestDTO;
import com.lingyi.ai.model.vo.SmartReportResultVO;
import com.lingyi.ai.service.ai.AiAnalysisService;
import com.lingyi.ai.service.smart.SmartReportEngineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 智能报告引擎服务实现
 *
 * @author lingyi
 */
@Slf4j
@Service
public class SmartReportEngineServiceImpl implements SmartReportEngineService {

    @Resource
    private AiAnalysisService aiAnalysisService;

    @Override
    public SmartReportResultVO analyze(SmartReportRequestDTO request) {
        log.info("智能报告分析开始，日期：{}", request.getReportDate());
        request.applyDefaults();

        SmartReportResultVO.TriggeredRulesVO triggered = evaluateRules(request);
        String alertSummary = buildAlertSummary(triggered);
        String aiContent = generateAiContent(request, triggered);

        SmartReportResultVO result = new SmartReportResultVO();
        result.setTriggeredRules(triggered);
        result.setAlertSummary(alertSummary);
        result.setAiContent(aiContent);
        result.setMetrics(buildMetrics(request));

        log.info("智能报告分析完成，红色{}条，黄色{}条，绿色{}条",
                triggered.getRedAlerts().size(),
                triggered.getYellowAlerts().size(),
                triggered.getGreenAlerts().size());
        return result;
    }

    @Override
    public Flux<String> streamAnalyze(SmartReportRequestDTO request) {
        log.info("智能报告流式分析开始，日期：{}", request.getReportDate());
        request.applyDefaults();

        SmartReportResultVO.TriggeredRulesVO triggered = evaluateRules(request);
        String alertSummary = buildAlertSummary(triggered);

        // 推送规则结果和摘要作为第一个事件
        StringBuilder prefix = new StringBuilder();
        prefix.append("[RULES]");
        prefix.append(serializeRules(triggered));
        prefix.append("[SUMMARY]");
        prefix.append(alertSummary);

        // AI 内容流式输出，完成后推送最终结果
        return buildAiStream(request, triggered)
                .concatWith(Flux.just("[METRICS]" + serializeMetrics(buildMetrics(request))));
    }

    /**
     * 规则引擎评估
     */
    private SmartReportResultVO.TriggeredRulesVO evaluateRules(SmartReportRequestDTO req) {
        int totalLinks = safeInt(req.getRisingLinks()) + safeInt(req.getFallingLinks()) + safeInt(req.getNoOrderLinks());
        BigDecimal revenueChangeRate = calculateRevenueChange(req.getWeeklyRevenue(), req.getLastWeekRevenue());

        SmartReportResultVO.TriggeredRulesVO result = new SmartReportResultVO.TriggeredRulesVO();
        result.setRedAlerts(new ArrayList<>());
        result.setYellowAlerts(new ArrayList<>());
        result.setGreenAlerts(new ArrayList<>());

        // ═══ 红色预警 ═══

        // R1: 销售额暴跌
        if (revenueChangeRate.compareTo(BigDecimal.ZERO) < 0
                && revenueChangeRate.abs().compareTo(req.getR1Threshold()) >= 0) {
            result.getRedAlerts().add("销售额大幅下滑预警");
        }

        // R2: 大量链接滞销
        if (totalLinks > 0) {
            BigDecimal noOrderRatio = BigDecimal.valueOf(safeInt(req.getNoOrderLinks()))
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalLinks), 2, RoundingMode.HALF_UP);
            if (noOrderRatio.compareTo(req.getR2Threshold()) >= 0) {
                result.getRedAlerts().add("大量链接滞销预警");
            }
        }

        // R3: 利润率恶化
        if (req.getProfitRate().compareTo(req.getR3ProfitMin()) < 0
                && req.getProfitRateChange().abs().compareTo(req.getR3ProfitDrop()) >= 0
                && req.getProfitRateChange().compareTo(BigDecimal.ZERO) < 0) {
            result.getRedAlerts().add("利润率严重恶化预警");
        }

        // ═══ 黄色关注 ═══

        // Y1: 销售额小幅下滑（与 R1 互斥）
        if (revenueChangeRate.compareTo(BigDecimal.ZERO) < 0
                && revenueChangeRate.abs().compareTo(req.getY1Threshold()) >= 0
                && revenueChangeRate.abs().compareTo(req.getR1Threshold()) < 0) {
            result.getYellowAlerts().add("销售额下滑关注");
        }

        // Y2: 下跌链接偏多
        if (safeInt(req.getFallingLinks()) > safeInt(req.getRisingLinks()) * req.getY2Ratio().intValue()) {
            result.getYellowAlerts().add("链接下跌趋势明显");
        }

        // Y3: 零星滞销（与 R2 互斥）
        if (safeInt(req.getNoOrderLinks()) >= 1 && totalLinks > 0) {
            BigDecimal noOrderRatio = BigDecimal.valueOf(safeInt(req.getNoOrderLinks()))
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalLinks), 2, RoundingMode.HALF_UP);
            if (noOrderRatio.compareTo(req.getR2Threshold()) < 0) {
                result.getYellowAlerts().add("存在滞销链接");
            }
        }

        // ═══ 绿色亮点 ═══

        // G1: 销售额增长
        if (revenueChangeRate.compareTo(req.getG1Threshold()) >= 0) {
            result.getGreenAlerts().add("销售额稳步增长");
        }

        // G2: 爆款涌现
        if (totalLinks > 0) {
            BigDecimal risingRatio = BigDecimal.valueOf(safeInt(req.getRisingLinks()))
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalLinks), 2, RoundingMode.HALF_UP);
            if (risingRatio.compareTo(req.getG2Threshold()) >= 0) {
                result.getGreenAlerts().add("多链接表现亮眼");
            }
        }

        // G3: 利润率健康
        if (req.getProfitRate().compareTo(req.getG3Threshold()) >= 0
                && req.getProfitRateChange().compareTo(BigDecimal.ZERO) > 0) {
            result.getGreenAlerts().add("利润率表现优秀");
        }

        return result;
    }

    /**
     * 构建诊断结论摘要
     */
    private String buildAlertSummary(SmartReportResultVO.TriggeredRulesVO triggered) {
        List<String> red = triggered.getRedAlerts();
        List<String> yellow = triggered.getYellowAlerts();
        List<String> green = triggered.getGreenAlerts();

        if (!red.isEmpty()) {
            return "🔴 需立即关注：" + String.join("、", red);
        }
        if (!yellow.isEmpty()) {
            return "🟡 值得关注：" + String.join("、", yellow);
        }
        if (!green.isEmpty()) {
            return "🟢 本周运营状态良好！" + String.join("、", green);
        }
        return "📊 本日数据平稳，暂无明显异常";
    }

    /**
     * 调用 AI 生成运营建议
     */
    private String generateAiContent(SmartReportRequestDTO req, SmartReportResultVO.TriggeredRulesVO triggered) {
        StringBuilder fullText = new StringBuilder();
        try {
            Flux<String> aiStream = buildAiStream(req, triggered);
            aiStream.doOnNext(fullText::append).blockLast();
        } catch (Exception e) {
            log.warn("AI 调用失败，降级使用结构化模板", e);
            return buildFallbackContent(req, triggered,
                    formatRevenueChange(req), formatProfitChange(req));
        }
        return fullText.toString();
    }

    /**
     * 流式生成 AI 内容
     */
    private Flux<String> buildAiStream(SmartReportRequestDTO req, SmartReportResultVO.TriggeredRulesVO triggered) {
        String systemPrompt = """
                你是一位专业的亚马逊电商运营顾问，正在为卖家生成每日店铺健康诊断报告。

                ## 你的任务
                根据我提供的【结构化数据】和【已触发的规则】，生成一段自然、专业、有温度的运营日报文字。

                ## 输出要求
                - 语气：专业但不生硬，像一位有经验的运营顾问在和卖家说话
                - 长度：300字以内
                - 结构：整体表现（1-2句）→ 核心问题或亮点（2-3句）→ 行动建议（2-3条）
                - 禁止：不要出现「AI」「系统检测」「根据数据显示」等机械表达
                - 禁止：不要编造数据中没有的内容
                """;

        String revenueChangeStr = formatRevenueChange(req);
        String profitChangeStr = formatProfitChange(req);

        String userPrompt = String.format("""
                ## 今日数据汇总
                近7天销售额：%.2f USD，环比 %s
                近7天订单量：%d 单
                销量上涨链接数：%d 条
                销量下跌链接数：%d 条
                未出单链接数：%d 条
                整体利润率：%.2f%%，环比 %s

                ## 触发的规则
                🔴 红色预警：%s
                🟡 黄色关注：%s
                🟢 绿色亮点：%s

                请直接输出报告正文，不要加任何标题或前缀。
                """,
                req.getWeeklyRevenue() != null ? req.getWeeklyRevenue().doubleValue() : 0,
                revenueChangeStr,
                safeInt(req.getTodayOrders()),
                safeInt(req.getRisingLinks()),
                safeInt(req.getFallingLinks()),
                safeInt(req.getNoOrderLinks()),
                req.getProfitRate() != null ? req.getProfitRate().doubleValue() : 0,
                profitChangeStr,
                triggered.getRedAlerts().isEmpty() ? "无" : String.join("、", triggered.getRedAlerts()),
                triggered.getYellowAlerts().isEmpty() ? "无" : String.join("、", triggered.getYellowAlerts()),
                triggered.getGreenAlerts().isEmpty() ? "无" : String.join("、", triggered.getGreenAlerts()));

        return aiAnalysisService.streamAiAnalysis(systemPrompt, userPrompt);
    }

    private String formatRevenueChange(SmartReportRequestDTO req) {
        BigDecimal revenueChange = calculateRevenueChange(req.getWeeklyRevenue(), req.getLastWeekRevenue());
        String changeSymbol = revenueChange.compareTo(BigDecimal.ZERO) >= 0 ? "↑" : "↓";
        return changeSymbol + revenueChange.abs() + "%";
    }

    private String formatProfitChange(SmartReportRequestDTO req) {
        String profitChangeSymbol = req.getProfitRateChange().compareTo(BigDecimal.ZERO) >= 0 ? "↑" : "↓";
        return profitChangeSymbol + req.getProfitRateChange().abs() + "pt";
    }

    /**
     * 降级模板（AI 调用失败时使用）
     */
    private String buildFallbackContent(SmartReportRequestDTO req,
                                        SmartReportResultVO.TriggeredRulesVO triggered,
                                        String revenueChangeStr, String profitChangeStr) {
        StringBuilder sb = new StringBuilder();
        sb.append("【销售健康度日报】\n\n");
        sb.append("近7天销售额：").append(req.getWeeklyRevenue()).append(" USD，环比 ").append(revenueChangeStr).append("\n");
        sb.append("近7天订单量：").append(safeInt(req.getTodayOrders())).append(" 单\n");
        sb.append("销量上涨链接：").append(safeInt(req.getRisingLinks())).append(" 条");
        sb.append("，下跌链接：").append(safeInt(req.getFallingLinks())).append(" 条");
        sb.append("，未出单：").append(safeInt(req.getNoOrderLinks())).append(" 条\n");
        sb.append("整体利润率：").append(req.getProfitRate()).append("%，环比 ").append(profitChangeStr).append("\n\n");

        if (!triggered.getRedAlerts().isEmpty()) {
            sb.append("🔴 预警：").append(String.join("、", triggered.getRedAlerts())).append("\n");
        }
        if (!triggered.getYellowAlerts().isEmpty()) {
            sb.append("🟡 关注：").append(String.join("、", triggered.getYellowAlerts())).append("\n");
        }
        if (!triggered.getGreenAlerts().isEmpty()) {
            sb.append("🟢 亮点：").append(String.join("、", triggered.getGreenAlerts())).append("\n");
        }
        if (triggered.getRedAlerts().isEmpty() && triggered.getYellowAlerts().isEmpty() && triggered.getGreenAlerts().isEmpty()) {
            sb.append("本日数据平稳，暂无明显异常。\n");
        }

        sb.append("\n建议关注上述指标变化，及时调整运营策略。");
        return sb.toString();
    }

    /**
     * 构建指标返回数据
     */
    private SmartReportResultVO.MetricsVO buildMetrics(SmartReportRequestDTO req) {
        SmartReportResultVO.MetricsVO metrics = new SmartReportResultVO.MetricsVO();
        metrics.setTodayRevenue(req.getTodayRevenue());
        metrics.setTodayOrders(req.getTodayOrders());
        metrics.setRisingLinks(req.getRisingLinks());
        metrics.setFallingLinks(req.getFallingLinks());
        metrics.setNoOrderLinks(req.getNoOrderLinks());
        metrics.setProfitRate(req.getProfitRate());
        metrics.setProfitRateChange(req.getProfitRateChange());
        metrics.setWeeklyRevenue(req.getWeeklyRevenue());
        metrics.setLastWeekRevenue(req.getLastWeekRevenue());
        metrics.setRevenueChangeRate(calculateRevenueChange(req.getWeeklyRevenue(), req.getLastWeekRevenue()));
        return metrics;
    }

    /**
     * 计算销售额环比变化（%）
     */
    private BigDecimal calculateRevenueChange(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return current != null && current.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        }
        if (current == null) {
            return BigDecimal.valueOf(-100);
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 2, RoundingMode.HALF_UP);
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    /**
     * 序列化规则为前端可解析的字符串
     */
    private String serializeRules(SmartReportResultVO.TriggeredRulesVO triggered) {
        StringBuilder sb = new StringBuilder();
        sb.append("red:").append(String.join(",", triggered.getRedAlerts())).append(";");
        sb.append("yellow:").append(String.join(",", triggered.getYellowAlerts())).append(";");
        sb.append("green:").append(String.join(",", triggered.getGreenAlerts())).append(";");
        return sb.toString();
    }

    /**
     * 序列化指标为前端可解析的字符串
     */
    private String serializeMetrics(SmartReportResultVO.MetricsVO m) {
        StringBuilder sb = new StringBuilder();
        sb.append("todayRevenue:").append(m.getTodayRevenue()).append(";");
        sb.append("todayOrders:").append(m.getTodayOrders()).append(";");
        sb.append("weeklyRevenue:").append(m.getWeeklyRevenue()).append(";");
        sb.append("lastWeekRevenue:").append(m.getLastWeekRevenue()).append(";");
        sb.append("risingLinks:").append(m.getRisingLinks()).append(";");
        sb.append("fallingLinks:").append(m.getFallingLinks()).append(";");
        sb.append("noOrderLinks:").append(m.getNoOrderLinks()).append(";");
        sb.append("profitRate:").append(m.getProfitRate()).append(";");
        sb.append("profitRateChange:").append(m.getProfitRateChange()).append(";");
        sb.append("revenueChangeRate:").append(m.getRevenueChangeRate()).append(";");
        return sb.toString();
    }
}
