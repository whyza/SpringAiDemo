package com.lingyi.ai.model.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.DateTimeFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 分析历史导出 VO
 *
 * @author lingyi
 */
@Data
public class AnalysisHistoryExportVO {

    @ExcelProperty("分析日期")
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private Date analysisDate;

    @ExcelProperty("今日销量")
    private Integer todaySales;

    @ExcelProperty("昨日销量")
    private Integer yesterdaySales;

    @ExcelProperty("销量环比")
    private String salesGrowthRate;

    @ExcelProperty("今日销售额")
    private BigDecimal todayRevenue;

    @ExcelProperty("昨日销售额")
    private BigDecimal yesterdayRevenue;

    @ExcelProperty("销售额环比")
    private String revenueGrowthRate;

    @ExcelProperty("健康度评分")
    private Integer healthScore;

    @ExcelProperty("核心结论")
    private String conclusions;

    @ExcelProperty("风险预警")
    private String riskWarning;

}
