package com.lingyi.ai.service.smart;

import com.lingyi.ai.model.dto.SmartReportRequestDTO;
import com.lingyi.ai.model.vo.SmartReportResultVO;

/**
 * 智能报告引擎服务
 *
 * @author lingyi
 */
public interface SmartReportEngineService {

    /**
     * 分析销售数据并生成智能报告
     *
     * @param request 包含销售指标和规则阈值的请求
     * @return 包含触发规则、诊断摘要和 AI 建议的报告结果
     */
    SmartReportResultVO analyze(SmartReportRequestDTO request);
}
