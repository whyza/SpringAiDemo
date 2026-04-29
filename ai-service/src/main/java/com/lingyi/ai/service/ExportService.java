package com.lingyi.ai.service;

import com.lingyi.ai.model.vo.AnalysisHistoryExportVO;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * 报表导出服务
 *
 * @author lingyi
 */
public interface ExportService {

    /**
     * 导出分析历史 Excel
     *
     * @param response HTTP 响应
     * @param dataList 数据列表
     */
    void exportAnalysisHistory(HttpServletResponse response, List<AnalysisHistoryExportVO> dataList);

}
