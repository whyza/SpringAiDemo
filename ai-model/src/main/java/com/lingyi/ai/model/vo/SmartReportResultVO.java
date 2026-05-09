package com.lingyi.ai.model.vo;

import lombok.Data;

/**
 * 智能报告结果 VO
 *
 * @author lingyi
 */
@Data
public class SmartReportResultVO {

    /**
     * 运营诊断内容
     */
    private String operationDiagnosis;

    /**
     * 诊断结论内容
     */
    private String diagnosisConclusion;
}
