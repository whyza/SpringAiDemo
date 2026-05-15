package com.lingyi.ai.service.smart;

/**
 * 智能报告分析进度步骤常量
 *
 * @author lingyi
 */
public final class SmartReportProgress {

    public static final java.util.function.Consumer<String> NOOP = step -> {};

    private SmartReportProgress() {}

    /** AI 分析诊断 */
    public static final String AI_ANALYSIS = "{\"step\":\"ai_analysis\",\"message\":\"AI 正在分析诊断数据...\"}";

    /** 解析分析结果 */
    public static final String PARSING = "{\"step\":\"parsing\",\"message\":\"正在提取诊断结论和运营建议...\"}";

    /** 分析完成 */
    public static final String COMPLETE = "{\"step\":\"complete\",\"message\":\"分析完成\"}";
}
