package com.lingyi.ai.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
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

    private BigDecimal todayRevenue;
    private BigDecimal yesterdayRevenue;
    private Integer todayOrders;
    private Integer yesterdayOrders;
    private Integer todayRisingLinks;
    private Integer yesterdayRisingLinks;
    private Integer todayFallingLinks;
    private Integer yesterdayFallingLinks;
    private Integer todayNoOrderLinks;
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

    /**
     * 判断是否缺少业务数据（前端没传值）
     */
    public boolean isDataMissing() {
        return todayRevenue == null && yesterdayRevenue == null
                && todayOrders == null && yesterdayOrders == null
                && todayRisingLinks == null && yesterdayRisingLinks == null
                && todayFallingLinks == null && yesterdayFallingLinks == null
                && todayNoOrderLinks == null && yesterdayNoOrderLinks == null;
    }

}
