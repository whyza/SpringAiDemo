package com.lingyi.ai.service.smart.impl;

import com.lingyi.ai.dal.dataobject.DailyReportDO;
import com.lingyi.ai.dal.dataobject.SmartReportConfigDO;
import com.lingyi.ai.dal.mapper.DailyReportMapper;
import com.lingyi.ai.model.dto.SmartReportRequestDTO;
import com.lingyi.ai.model.vo.SmartReportResultVO;
import com.lingyi.ai.service.ai.AiAnalysisService;
import com.lingyi.ai.service.smart.SmartReportConfigService;
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

    @Resource
    private SmartReportConfigService smartReportConfigService;

    @Resource
    private DailyReportMapper dailyReportMapper;

    @Override
    public SmartReportResultVO analyze(SmartReportRequestDTO request) {
        // 前端未传业务数据时，从数据库加载
        if (request.isDataMissing()) {
            SmartReportConfigDO config = smartReportConfigService.loadLatest();
            if (config != null && config.getTodayRevenue() != null) {
                log.info("从 smart_report_config 加载业务数据，id={}", config.getId());
                fillFromConfig(request, config);
            } else {
                // 降级：从 daily_report 加载最新记录
                DailyReportDO daily = dailyReportMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DailyReportDO>()
                                .orderByDesc(DailyReportDO::getReportDate)
                                .last("LIMIT 1"));
                if (daily != null) {
                    log.info("从 daily_report 加载业务数据，date={}", daily.getReportDate());
                    request.setTodayRevenue(daily.getTodayRevenue());
                    request.setYesterdayRevenue(daily.getYesterdayRevenue());
                    request.setTodayOrders(daily.getTodaySales());
                    request.setYesterdayOrders(daily.getYesterdaySales());
                    request.setTodayRisingLinks(daily.getRisingLinks());
                    request.setTodayFallingLinks(daily.getFallingLinks());
                    request.setTodayNoOrderLinks(daily.getNoOrderLinks());
                    // 昨日链接数从更早的记录获取
                    DailyReportDO prevDaily = dailyReportMapper.selectOne(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DailyReportDO>()
                                    .lt(DailyReportDO::getReportDate, daily.getReportDate())
                                    .orderByDesc(DailyReportDO::getReportDate)
                                    .last("LIMIT 1"));
                    if (prevDaily != null) {
                        request.setYesterdayRisingLinks(prevDaily.getRisingLinks());
                        request.setYesterdayFallingLinks(prevDaily.getFallingLinks());
                        request.setYesterdayNoOrderLinks(prevDaily.getNoOrderLinks());
                    }
                } else {
                    log.warn("daily_report 也无数据，将使用零值");
                }
            }
        }
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
//        result.setFullModelResponse(diagnosisConclusionText + "\n" + operationDiagnosisText);

        SmartReportResultVO.DiagnosisConclusionVO diagnosisConclusion = new SmartReportResultVO.DiagnosisConclusionVO();
        diagnosisConclusion.setRedAlerts(structuredDiagnosis.getRedAlerts());
        diagnosisConclusion.setYellowAlerts(structuredDiagnosis.getYellowAlerts());
        diagnosisConclusion.setGreenHighlights(structuredDiagnosis.getGreenHighlights());
        result.setDiagnosisConclusions(diagnosisConclusion);

        SmartReportResultVO.OperationDiagnosisVO operationDiagnosis = new SmartReportResultVO.OperationDiagnosisVO();
        operationDiagnosis.setOverallPerformance(structuredDiagnosis.getOverallPerformance());
        operationDiagnosis.setLinkStructureAnalysis(structuredDiagnosis.getLinkStructureAnalysis());
        operationDiagnosis.setAnomalyLogicJudgment(structuredDiagnosis.getAnomalyLogicJudgment());
        operationDiagnosis.setOperationSuggestions(structuredDiagnosis.getOperationSuggestions());
        result.setOperationDiagnosis(operationDiagnosis);
        return result;
    }

    private String generateDiagnosisConclusion(SmartReportRequestDTO req) {
        String systemPrompt = """
                你是一位亚马逊运营诊断助手。
                你需要严格根据输入数据和给定规则，输出固定格式的诊断结论。
                
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
                你是一位资深亚马逊电商运营顾问，请根据以下数据为卖家生成今日店铺健康诊断日报。

                ## 严格规则
                1. 禁止编造任何输入中不存在的数据，只能使用明确提供的数据
                2. 禁止使用"系统检测"、"算法分析"、"AI 分析"等机械话术
                3. 必须引用输入中的具体数字进行分析，不得笼统描述
                4. 总字数控制在 500 字以内，简洁精炼
                5. 语气专业通俗，站在卖家视角，像资深运营在跟卖家沟通
                6. 数据不足以支撑某部分分析时，直接跳过，不要硬写
                7. 直接输出报告正文，不要加"根据提供的数据"、"基于以上数据"等开场白

                ## 输出结构（严格按此顺序）

                ① 整体表现：对比昨日今日销售额、订单降幅，精准计算客单价；通过客单价判断下滑是否为降价导致，直白说明是流量 / 转化真实缩水，结合自定义规则判定行情预警等级

                ② 链接结构分析：用极简表格展示上涨、下跌、平稳、未出单链接 + 数量 + 占比；标注风险警告，对比昨日、今日未出单链接差值，算出新增哑火链接数量，判断店铺链接健康度、产品支撑能力。风险项用 ⚠️ 标记。

                ③ 异常逻辑判断：根据涨跌链接分布，判断是个别链接问题还是店铺整体性下跌；分析行情下跌底层原因，罗列需要排查的亚马逊常见诱因。风险项用 ⚠️ 标记。

                ④ 运营建议（必须有）：全部为低风险、可落地实操动作；区分排查优先级，区分平台原因 / 店铺原因，弱势行情禁止大幅改动，以排查、维稳、观察为主，语言直白通俗。正向建议用 ✅ 开头。
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
        int todayTotalLinks = calculateTodayTotalLinks(req);
        return String.format("""
                请基于以下数据和规则判断诊断结论。
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

                ## 客户自定义规则
                红色规则：
                1. 销售额大幅下滑：当天销售额环比昨日下降幅度 >= %s%% 时命中。
                2. 大量链接下跌：当天下跌链接数 ≥ 当天总链接数 × %s%% 时命中。
                3. 大量链接未出单：当天未出单链接数 ≥ 当天总链接数 × %s%% 时命中。
                黄色规则：
                1. 销售额小幅下滑：当天销售额环比昨日下降幅度 >= %s%%，但未达到 %s%% 时命中。
                2. 部分链接下跌：当天存在下跌链接，且下跌链接占比未达红色阈值时命中。（与大量链接下跌互斥）
                3. 部分链接未出单：当天存在未出单链接，且未出单占比未达红色阈值时命中。（与大量链接未出单互斥）
                绿色规则：
                1. 销售额稳步增长：当天销售额相较昨日增长幅度 >= %s%% 时命中。
                2. 上涨链接占比亮眼：当天上涨链接数 ≥ 当天总链接数 × %s%% 时命中。
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
                formatAmount(req.getR1Threshold()),
                formatAmount(req.getG1Threshold()),
                formatAmount(req.getG2Threshold())
        );
    }

    private String buildOperationDiagnosisPrompt(SmartReportRequestDTO req, String diagnosisConclusion) {
        int todayTotalLinks = calculateTodayTotalLinks(req);
        return String.format("""
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

                ## 客户自定义规则
                - 销售额大幅下滑——当天销售额相较昨日下降幅度达到 %s%%
                - 大量链接下跌——当天下跌链接数占当天总链接数的比例达到 %s%%
                - 大量链接未出单——当天未出单链接占总链接数的比例达到 %s%%
                - 销售额小幅下滑——当天销售额下降幅度达到 %s%%，但未达到 %s%%
                - 部分链接下跌——当天未出单链接占当天总链接数的比例未达到 %s%%
                - 部分链接未出单——当天未出单链接占总链接数的比例未达到 %s%%
                - 销售额稳步增长——当天销售额相较昨日增长幅度达到 %s%%
                - 上涨链接占比亮眼——当天上涨链接数占当天总链接数比例达到 %s%%

                ## 诊断结论
                %s

                请基于以上数据和规则，输出完整的运营诊断正文。
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
                formatAmount(req.getR1Threshold()),
                formatAmount(req.getR2Threshold()),
                formatAmount(req.getR3Threshold()),
                formatAmount(req.getG1Threshold()),
                formatAmount(req.getG2Threshold()),
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

        // ① 整体表现
        content.append("① 整体表现：");
        content.append("今日销售额 ").append(formatAmount(req.getTodayRevenue())).append(" 元，昨日销售额 ").append(
                formatAmount(req.getYesterdayRevenue())).append(" 元，变化 ").append(revenueChange).append("。");
        content.append("今日订单量 ").append(safeInt(req.getTodayOrders())).append(" 单，昨日订单量 ").append(safeInt(
                req.getYesterdayOrders())).append(" 单。");
        content.append("今日总链接数 ").append(todayTotalLinks).append("，昨日总链接数 ").append(yesterdayTotalLinks).append("。\n\n");

        // ② 链接结构分析
        content.append("② 链接结构分析：");
        content.append("上涨 ").append(safeInt(req.getTodayRisingLinks())).append(" 个，下跌 ").append(safeInt(
                req.getTodayFallingLinks())).append(" 个，未出单 ").append(safeInt(req.getTodayNoOrderLinks())).append(" 个，总链接 ")
                .append(todayTotalLinks).append(" 个。");
        content.append("昨日未出单 ").append(safeInt(req.getYesterdayNoOrderLinks())).append(" 个，未出单变化 ")
                .append(safeInt(req.getTodayNoOrderLinks()) - safeInt(req.getYesterdayNoOrderLinks())).append(" 个。\n\n");

        // ③ 异常逻辑判断
        content.append("③ 异常逻辑判断：");
        boolean hasRisk = diagnosisConclusion.contains("🔴") || diagnosisConclusion.contains("🟡");
        if (hasRisk) {
            content.append("触发预警规则，需关注异常链接。");
            if (diagnosisConclusion.contains("🔴")) {
                content.append(" ⚠️ 存在红色预警，需立即排查。");
            }
            if (diagnosisConclusion.contains("🟡")) {
                content.append(" ⚠️ 存在黄色预警，建议关注。");
            }
        } else {
            content.append("暂未发现明显异常，链接结构相对稳定。");
        }
        content.append("\n\n");

        // ④ 运营建议
        content.append("④ 运营建议：\n");
        content.append("✅ 优先排查销售额变化原因，检查客单价是否稳定。弱势行情以排查为主，避免大幅改动。\n");
        content.append("✅ 重点跟进下跌链接和未出单链接，及时优化 listing、广告投放和竞价策略，区分平台原因还是店铺原因。\n");
        content.append("✅ 对已表现较好的上涨链接，继续承接流量，维持广告预算，避免因结构失衡拖累整体表现。\n");

        return content.toString();
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
        int toIdx = nextNum == null ? content.length() : content.indexOf(nextNum, fromIdx + 1);
        if (toIdx < 0) {
            toIdx = content.length();
        }
        // Extract content between current and next marker
        String section = content.substring(fromIdx + 1, toIdx).trim();
        // Remove the label prefix up to the first colon (e.g., "整体表现：")
        int labelEnd = section.indexOf("：");
        if (labelEnd > 0 && labelEnd < 30) {
            section = section.substring(labelEnd + 1).trim();
        } else {
            // No colon found, skip the first line (likely a label line)
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

    private TriggeredRules evaluateFallbackRules(SmartReportRequestDTO req) {
        int todayTotalLinks = calculateTodayTotalLinks(req);
        BigDecimal revenueChange = calculateRevenueChange(req);

        TriggeredRules result = new TriggeredRules();
        result.setRedAlerts(new ArrayList<>());
        result.setYellowAlerts(new ArrayList<>());
        result.setGreenAlerts(new ArrayList<>());

        // R1: 销售额大幅下滑
        if (revenueChange.compareTo(BigDecimal.ZERO) < 0 && revenueChange.abs().compareTo(req.getR1Threshold()) >= 0) {
            result.getRedAlerts().add("销售额大幅下滑");
        }

        // R2: 大量链接下跌（下跌链接占比 >= R2 阈值）
        if (todayTotalLinks > 0) {
            BigDecimal fallingRatio = calculateLinkRatio(req.getTodayFallingLinks(), todayTotalLinks);
            if (fallingRatio.compareTo(req.getR2Threshold()) >= 0) {
                result.getRedAlerts().add("大量链接下跌");
            }
        }

        // R3: 大量链接未出单（未出单链接占比 >= R3 阈值）
        if (todayTotalLinks > 0) {
            BigDecimal noOrderRatio = calculateLinkRatio(req.getTodayNoOrderLinks(), todayTotalLinks);
            if (noOrderRatio.compareTo(req.getR3Threshold()) >= 0) {
                result.getRedAlerts().add("大量链接未出单");
            }
        }

        // Y1: 销售额小幅下滑
        if (revenueChange.compareTo(BigDecimal.ZERO) < 0 && revenueChange
                .abs()
                .compareTo(req.getY1Threshold()) >= 0 && revenueChange.abs().compareTo(req.getR1Threshold()) < 0) {
            result.getYellowAlerts().add("销售额小幅下滑");
        }

        // Y2: 部分链接下跌（当天未出单链接占比未达 R2 阈值，与 R2 互斥）
        if (todayTotalLinks > 0) {
            BigDecimal noOrderRatio = calculateLinkRatio(req.getTodayNoOrderLinks(), todayTotalLinks);
            if (safeInt(req.getTodayNoOrderLinks()) > 0 && noOrderRatio.compareTo(req.getR2Threshold()) < 0) {
                result.getYellowAlerts().add("部分链接下跌");
            }
        }

        // Y3: 部分链接未出单（存在未出单链接，但占比未达 R3 阈值，与 R3 互斥）
        if (todayTotalLinks > 0) {
            BigDecimal noOrderRatio = calculateLinkRatio(req.getTodayNoOrderLinks(), todayTotalLinks);
            if (safeInt(req.getTodayNoOrderLinks()) > 0 && noOrderRatio.compareTo(req.getR3Threshold()) < 0) {
                result.getYellowAlerts().add("部分链接未出单");
            }
        }

        // G1: 销售额稳步增长
        if (revenueChange.compareTo(req.getG1Threshold()) >= 0) {
            result.getGreenAlerts().add("销售额稳步增长");
        }

        // G2: 上涨链接占比亮眼
        if (todayTotalLinks > 0) {
            BigDecimal risingRatio = calculateLinkRatio(req.getTodayRisingLinks(), todayTotalLinks);
            if (risingRatio.compareTo(req.getG2Threshold()) >= 0) {
                result.getGreenAlerts().add("上涨链接占比亮眼");
            }
        }

        return result;
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

    private void fillFromConfig(SmartReportRequestDTO request, SmartReportConfigDO config) {
        request.setTodayRevenue(config.getTodayRevenue());
        request.setYesterdayRevenue(config.getYesterdayRevenue());
        request.setTodayOrders(config.getTodayOrders());
        request.setYesterdayOrders(config.getYesterdayOrders());
        request.setTodayRisingLinks(config.getTodayRisingLinks());
        request.setYesterdayRisingLinks(config.getYesterdayRisingLinks());
        request.setTodayFallingLinks(config.getTodayFallingLinks());
        request.setYesterdayFallingLinks(config.getYesterdayFallingLinks());
        request.setTodayNoOrderLinks(config.getTodayNoOrderLinks());
        request.setYesterdayNoOrderLinks(config.getYesterdayNoOrderLinks());
        if (config.getR1Threshold() != null) request.setR1Threshold(config.getR1Threshold());
        if (config.getR2Threshold() != null) request.setR2Threshold(config.getR2Threshold());
        if (config.getR3Threshold() != null) request.setR3Threshold(config.getR3Threshold());
        if (config.getY1Threshold() != null) request.setY1Threshold(config.getY1Threshold());
        if (config.getG1Threshold() != null) request.setG1Threshold(config.getG1Threshold());
        if (config.getG2Threshold() != null) request.setG2Threshold(config.getG2Threshold());
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
        private List<String> overallPerformance;
        private List<String> linkStructureAnalysis;
        private List<String> anomalyLogicJudgment;
        private List<String> operationSuggestions;
    }
}
