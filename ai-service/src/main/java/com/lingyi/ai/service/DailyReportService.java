package com.lingyi.ai.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingyi.ai.model.dto.EcommerceDataDTO;
import com.lingyi.ai.model.vo.DailyReportDetailVO;
import com.lingyi.ai.model.vo.DailyReportListVO;
import com.lingyi.ai.model.vo.DailyReportPushVO;

import java.time.LocalDate;

/**
 * 日报服务接口
 *
 * @author lingyi
 */
public interface DailyReportService {

    /**
     * 保存日报
     *
     * @param report  报告数据
     * @param dataDTO 原始电商数据
     */
    void saveReport(DailyReportPushVO report, EcommerceDataDTO dataDTO);

    /**
     * 分页查询日报列表
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @param pageNum   页码
     * @param pageSize  每页数量
     * @return 分页结果
     */
    Page<DailyReportListVO> listReports(LocalDate startDate, LocalDate endDate, Integer pageNum, Integer pageSize);

    /**
     * 查询日报详情
     *
     * @param id 报告ID
     * @return 报告详情
     */
    DailyReportDetailVO getReportDetail(Long id);

    /**
     * 获取周期总结（AI 分析）
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 周期总结报告
     */
    String getPeriodSummary(LocalDate startDate, LocalDate endDate);

    /**
     * 获取周期总结（AI 分析）
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @param forceRefresh 是否强制重新生成
     * @return 周期总结报告
     */
    String getPeriodSummary(LocalDate startDate, LocalDate endDate, boolean forceRefresh);

    /**
     * 批量生成测试数据
     *
     * @param days 天数
     * @return 生成的记录数
     */
    int generateBatchData(int days);
}
