package com.lingyi.ai.model.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 日报列表项 VO
 *
 * @author lingyi
 */
@Data
public class DailyReportListVO {

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
     * 今日销量
     */
    private Integer todaySales;

    /**
     * 昨日销量
     */
    private Integer yesterdaySales;

    /**
     * 今日销售额
     */
    private String todayRevenue;

    /**
     * 昨日销售额
     */
    private String yesterdayRevenue;

    /**
     * 销量环比增长率(%)
     */
    private Double salesGrowthRate;

    /**
     * 销售额环比增长率(%)
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

    /**
     * 健康度评分
     */
    private Integer healthScore;
}
