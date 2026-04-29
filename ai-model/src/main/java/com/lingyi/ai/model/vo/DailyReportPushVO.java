package com.lingyi.ai.model.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 日报推送 VO（用于推送给运营人员）
 *
 * @author lingyi
 */
@Data
public class DailyReportPushVO {

    /**
     * 报告日期
     */
    private LocalDate reportDate;

    /**
     * 核心结论（用于消息摘要）
     */
    private String summary;

    /**
     * 数据概览
     */
    private ReportDataVO data;

    /**
     * 亮点分析
     */
    private List<String> highlights;

    /**
     * 风险预警
     */
    private List<String> risks;

    /**
     * 运营建议
     */
    private List<String> suggestions;

    /**
     * 完整报告文本（可直接推送）
     */
    private String fullReport;

    /**
     * 关键报告摘要（用于完整报告展示，不重复亮点/风险/建议）
     */
    private String keyReport;

    /**
     * 周期总结报告
     */
    private String periodSummary;

    /**
     * 周期总结开始日期
     */
    private LocalDate periodStartDate;

    /**
     * 周期总结结束日期
     */
    private LocalDate periodEndDate;

    /**
     * 健康度评分 (0-100)
     */
    private Integer healthScore;

    /**
     * 数据概览 VO
     */
    @Data
    public static class ReportDataVO {
        /**
         * 今日销量
         */
        private Integer todaySales;

        /**
         * 昨日销量
         */
        private Integer yesterdaySales;

        /**
         * 销量环比增长率 (%)
         */
        private Double salesGrowthRate;

        /**
         * 今日销售额
         */
        private String todayRevenue;

        /**
         * 昨日销售额
         */
        private String yesterdayRevenue;

        /**
         * 销售额环比增长率 (%)
         */
        private Double revenueGrowthRate;

        /**
         * 上涨链接数
         */
        private Integer risingLinks;

        /**
         * 下跌链接数
         */
        private Integer fallingLinks;

        /**
         * 未出单链接数
         */
        private Integer noOrderLinks;
    }
}
