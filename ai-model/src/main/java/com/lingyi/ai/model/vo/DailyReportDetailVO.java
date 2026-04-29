package com.lingyi.ai.model.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 日报详情 VO（完整报告数据）
 *
 * @author lingyi
 */
@Data
public class DailyReportDetailVO {

    /**
     * 报告ID
     */
    private Long id;

    /**
     * 报告日期
     */
    private LocalDate reportDate;

    /**
     * 摘要
     */
    private String summary;

    /**
     * 数据概览
     */
    private DataVO data;

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
     * 完整报告
     */
    private String fullReport;

    /**
     * 关键报告摘要
     */
    private String keyReport;

    /**
     * 健康度评分
     */
    private Integer healthScore;

    /**
     * 数据概览
     */
    @Data
    public static class DataVO {
        private Integer todaySales;
        private Integer yesterdaySales;
        private Double salesGrowthRate;
        private String todayRevenue;
        private String yesterdayRevenue;
        private Double revenueGrowthRate;
        private Integer risingLinks;
        private Integer fallingLinks;
        private Integer noOrderLinks;
    }
}
