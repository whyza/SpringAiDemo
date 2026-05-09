package com.lingyi.ai.service.ai;

import com.lingyi.ai.model.dto.EcommerceDataDTO;
import com.lingyi.ai.model.vo.DailyReportPushVO;

/**
 * AI 分析服务接口
 *
 * @author lingyi
 */
public interface AiAnalysisService {

    /**
     * 调用 AI 进行电商数据分析（带系统提示词）
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return AI 分析结果
     */
    String callAiAnalysis(String systemPrompt, String userPrompt);

    /**
     * 生成电商日报（包含 AI 分析和建议）
     *
     * @param dataDTO 电商数据
     * @return 日报推送数据
     */
    DailyReportPushVO generateDailyReport(EcommerceDataDTO dataDTO);

}
