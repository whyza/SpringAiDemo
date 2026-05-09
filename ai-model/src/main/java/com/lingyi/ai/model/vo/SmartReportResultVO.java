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
    private List<String> diagnosisConclusions;

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
    public static class OperationDiagnosisVO {

        @JsonProperty("核心结论")
        private List<String> coreConclusions;

        @JsonProperty("业务本质")
        private List<String> businessEssence;

        @JsonProperty("多维度评估")
        private List<String> multiDimensionalEvaluations;

        @JsonProperty("亮点")
        private List<String> highlights;

        @JsonProperty("风险预警")
        private List<String> riskWarnings;

        @JsonProperty("运营建议")
        private List<String> operationSuggestions;
    }
}
