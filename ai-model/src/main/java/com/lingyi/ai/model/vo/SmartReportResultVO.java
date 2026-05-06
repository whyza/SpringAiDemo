package com.lingyi.ai.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 智能报告引擎结果 VO
 *
 * @author lingyi
 */
@Data
public class SmartReportResultVO {

    /**
     * 触发的规则（按红/黄/绿分组）
     */
    private TriggeredRulesVO triggeredRules;

    /**
     * 诊断结论摘要（如 "🔴 需立即关注：销售额大幅下滑预警"）
     */
    private String alertSummary;

    /**
     * AI 生成的运营建议正文
     */
    private String aiContent;

    /**
     * 核心指标数据
     */
    private MetricsVO metrics;

    /**
     * 触发的规则分组 VO
     */
    @Data
    public static class TriggeredRulesVO {
        /**
         * 红色预警规则名称列表
         */
        private List<String> redAlerts;

        /**
         * 黄色关注规则名称列表
         */
        private List<String> yellowAlerts;

        /**
         * 绿色亮点规则名称列表
         */
        private List<String> greenAlerts;
    }

    /**
     * 核心指标 VO
     */
    @Data
    public static class MetricsVO {
        /**
         * 当天销售额
         */
        private BigDecimal todayRevenue;

        /**
         * 当天订单量
         */
        private Integer todayOrders;

        /**
         * 销量上涨链接数
         */
        private Integer risingLinks;

        /**
         * 销量下跌链接数
         */
        private Integer fallingLinks;

        /**
         * 未出单链接数
         */
        private Integer noOrderLinks;

        /**
         * 整体利润率（%）
         */
        private BigDecimal profitRate;

        /**
         * 利润率环比变化（pt）
         */
        private BigDecimal profitRateChange;

        /**
         * 近 7 日销售额
         */
        private BigDecimal weeklyRevenue;

        /**
         * 上周同期销售额
         */
        private BigDecimal lastWeekRevenue;

        /**
         * 销售额环比变化（%）
         */
        private BigDecimal revenueChangeRate;
    }
}
