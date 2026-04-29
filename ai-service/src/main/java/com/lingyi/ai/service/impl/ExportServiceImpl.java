package com.lingyi.ai.service.impl;

import com.alibaba.excel.EasyExcel;
import com.lingyi.ai.model.vo.AnalysisHistoryExportVO;
import com.lingyi.ai.service.ExportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;

/**
 * 报表导出服务实现
 *
 * @author lingyi
 */
@Slf4j
@Service
public class ExportServiceImpl implements ExportService {

    @Override
    public void exportAnalysisHistory(HttpServletResponse response, List<AnalysisHistoryExportVO> dataList) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");

            String fileName = URLEncoder.encode("电商数据分析报告", "UTF-8").replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".xlsx");

            EasyExcel.write(response.getOutputStream(), AnalysisHistoryExportVO.class)
                    .sheet("分析历史")
                    .doWrite(dataList);

            log.info("Excel 导出成功，记录数：{}", dataList.size());

        } catch (IOException e) {
            log.error("Excel 导出失败", e);
            throw new RuntimeException("Excel 导出失败：" + e.getMessage(), e);
        }
    }

}
