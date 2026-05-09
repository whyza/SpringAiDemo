package com.lingyi.ai.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 智能报告引擎请求 DTO
 *
 * @author lingyi
 */
@Data
public class SmartReportRequestDTO {

    /**
     * 报告日期（可选，默认今天）
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate reportDate;

    /**
     * 当天销售额（USD）
     */
    @NotNull(message = "当天销售额不能为空")
    @Min(value = 0, message = "销售额不能为负数")
    private BigDecimal todayRevenue;

    /**
     * 当天销售额环比变化（%）
     */
    @NotNull(message = "当天销售额环比变化不能为空")
    private BigDecimal todayRevenueChange;

    /**
     * 当天订单量
     */
    @NotNull(message = "当天订单量不能为空")
    @Min(value = 0, message = "订单量不能为负数")
    private Integer todayOrders;

    /**
     * 总链接数
     */
    @NotNull(message = "总链接数不能为空")
    @Min(value = 0, message = "总链接数不能为负数")
    private Integer totalLinks;

    /**
     * 销量上涨链接数
     */
    @NotNull(message = "销量上涨链接数不能为空")
    @Min(value = 0, message = "链接数不能为负数")
    private Integer risingLinks;

    /**
     * 销量下跌链接数
     */
    @NotNull(message = "销量下跌链接数不能为空")
    @Min(value = 0, message = "链接数不能为负数")
    private Integer fallingLinks;

    /**
     * 未出单链接数
     */
    @NotNull(message = "未出单链接数不能为空")
    @Min(value = 0, message = "链接数不能为负数")
    private Integer noOrderLinks;

    /**
     * 整体利润率（%）
     */
    @NotNull(message = "整体利润率不能为空")
    private BigDecimal profitRate;

    /**
     * 利润率环比变化（pt）
     */
    @NotNull(message = "利润率环比变化不能为空")
    private BigDecimal profitRateChange;

    /**
     * R1: 销售额暴跌阈值（%），默认 20
     */
    private BigDecimal r1Threshold;

    /**
     * R2: 大量链接滞销占比（%），默认 30
     */
    private BigDecimal r2Threshold;

    /**
     * R3: 利润率下限（%），默认 5
     */
    private BigDecimal r3ProfitMin;

    /**
     * R3: 利润率环比降幅（pt），默认 5
     */
    private BigDecimal r3ProfitDrop;

    /**
     * Y1: 销售额小幅下滑阈值（%），默认 10
     */
    private BigDecimal y1Threshold;

    /**
     * Y2: 下跌/上涨倍数，默认 2
     */
    private BigDecimal y2Ratio;

    /**
     * G1: 销售额增长阈值（%），默认 10
     */
    private BigDecimal g1Threshold;

    /**
     * G2: 上涨链接占比阈值（%），默认 40
     */
    private BigDecimal g2Threshold;

    /**
     * G3: 利润率健康阈值（%），默认 15
     */
    private BigDecimal g3Threshold;

    /**
     * 应用默认阈值
     */
    public void applyDefaults() {
        if (r1Threshold == null) {
            r1Threshold = BigDecimal.valueOf(20);
        }
        if (r2Threshold == null) {
            r2Threshold = BigDecimal.valueOf(30);
        }
        if (r3ProfitMin == null) {
            r3ProfitMin = BigDecimal.valueOf(5);
        }
        if (r3ProfitDrop == null) {
            r3ProfitDrop = BigDecimal.valueOf(5);
        }
        if (y1Threshold == null) {
            y1Threshold = BigDecimal.valueOf(10);
        }
        if (y2Ratio == null) {
            y2Ratio = BigDecimal.valueOf(2);
        }
        if (g1Threshold == null) {
            g1Threshold = BigDecimal.valueOf(10);
        }
        if (g2Threshold == null) {
            g2Threshold = BigDecimal.valueOf(40);
        }
        if (g3Threshold == null) {
            g3Threshold = BigDecimal.valueOf(15);
        }
    }
}
