package com.lingyi.ai.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
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

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate reportDate = LocalDate.now();

    @NotNull(message = "当天销售额不能为空")
    @DecimalMin(value = "0", message = "当天销售额不能为负数")
    private BigDecimal todayRevenue;

    @NotNull(message = "昨日销售额不能为空")
    @DecimalMin(value = "0", message = "昨日销售额不能为负数")
    private BigDecimal yesterdayRevenue;

    @NotNull(message = "当天订单量不能为空")
    @Min(value = 0, message = "当天订单量不能为负数")
    private Integer todayOrders;

    @NotNull(message = "昨日订单量不能为空")
    @Min(value = 0, message = "昨日订单量不能为负数")
    private Integer yesterdayOrders;

    @NotNull(message = "今日销量上涨链接数不能为空")
    @Min(value = 0, message = "链接数不能为负数")
    private Integer todayRisingLinks;

    @NotNull(message = "昨日销量上涨链接数不能为空")
    @Min(value = 0, message = "链接数不能为负数")
    private Integer yesterdayRisingLinks;

    @NotNull(message = "今日销量下跌链接数不能为空")
    @Min(value = 0, message = "链接数不能为负数")
    private Integer todayFallingLinks;

    @NotNull(message = "昨日销量下跌链接数不能为空")
    @Min(value = 0, message = "链接数不能为负数")
    private Integer yesterdayFallingLinks;

    @NotNull(message = "今日未出单链接数不能为空")
    @Min(value = 0, message = "链接数不能为负数")
    private Integer todayNoOrderLinks;

    @NotNull(message = "昨日未出单链接数不能为空")
    @Min(value = 0, message = "链接数不能为负数")
    private Integer yesterdayNoOrderLinks;

    /**
     * R1: 销售额大幅下滑阈值（%），默认 20
     */
    private BigDecimal r1Threshold;

    /**
     * R2: 大量链接下跌阈值（%），默认 30
     */
    private BigDecimal r2Threshold;

    /**
     * R3: 大量链接未出单阈值（%），默认 30
     */
    private BigDecimal r3Threshold;

    /**
     * Y1: 销售额小幅下滑阈值（%），默认 10
     */
    private BigDecimal y1Threshold;

    /**
     * G1: 销售额增长阈值（%），默认 10
     */
    private BigDecimal g1Threshold;

    /**
     * G2: 上涨链接占比阈值（%），默认 40
     */
    private BigDecimal g2Threshold;


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
        if (r3Threshold == null) {
            r3Threshold = BigDecimal.valueOf(30);
        }
        if (y1Threshold == null) {
            y1Threshold = BigDecimal.valueOf(10);
        }
        if (g1Threshold == null) {
            g1Threshold = BigDecimal.valueOf(10);
        }
        if (g2Threshold == null) {
            g2Threshold = BigDecimal.valueOf(40);
        }
    }
}
