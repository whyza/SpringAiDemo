package com.lingyi.ai.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 智能报告结果 VO
 *
 * @author lingyi
 */
@Data
public class SmartReportResultVO {

    /**
     * 诊断结论
     */
    @JsonProperty("诊断结论")
    private DiagnosisConclusionVO diagnosisConclusions;

    /**
     * 运营诊断
     */
    @JsonProperty("运营诊断")
    private OperationDiagnosisVO operationDiagnosis;

    /**
     * 原始运营诊断内容，兼容服务内赋值
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String operationDiagnosisText;

    /**
     * 原始诊断结论内容，兼容服务内赋值
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String diagnosisConclusionText;

    @Data
    public static class DiagnosisConclusionVO {

        @JsonProperty("立即关注")
        private List<String> redAlerts;

        @JsonProperty("值得注意")
        private List<String> yellowAlerts;

        @JsonProperty("运营亮点")
        private List<String> greenHighlights;
    }

    @Data
    public static class OperationDiagnosisVO {

        @JsonProperty("整体表现")
        private List<String> overallPerformance;

        @JsonProperty("链接结构分析")
        private List<String> linkStructureAnalysis;

        @JsonProperty("异常逻辑判断")
        private List<String> anomalyLogicJudgment;

        @JsonProperty("运营建议")
        private List<String> operationSuggestions;
    }
}
