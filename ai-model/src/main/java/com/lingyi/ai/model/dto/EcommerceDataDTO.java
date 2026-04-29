package com.lingyi.ai.model.dto;

import lombok.Data;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 电商数据请求 DTO
 *
 * @author lingyi
 */
@Data
public class EcommerceDataDTO {

    /**
     * 报告日期（可选，默认为今天）
     */
    private LocalDate reportDate;

    /**
     * 今日销量
     */
    @NotNull(message = "今日销量不能为空")
    @Min(value = 0, message = "销量不能为负数")
    private Integer todaySales;

    /**
     * 昨日销量
     */
    @NotNull(message = "昨日销量不能为空")
    @Min(value = 0, message = "销量不能为负数")
    private Integer yesterdaySales;

    /**
     * 今日销售额
     */
    @NotNull(message = "今日销售额不能为空")
    @Min(value = 0, message = "销售额不能为负数")
    private BigDecimal todayRevenue;

    /**
     * 昨日销售额
     */
    @NotNull(message = "昨日销售额不能为空")
    @Min(value = 0, message = "销售额不能为负数")
    private BigDecimal yesterdayRevenue;

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
     * 近 7 天未出单链接数
     */
    @NotNull(message = "近 7 天未出单链接数不能为空")
    @Min(value = 0, message = "链接数不能为负数")
    private Integer noOrderLinks;

}
