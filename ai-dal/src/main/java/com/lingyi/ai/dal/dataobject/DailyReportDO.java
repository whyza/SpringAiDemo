package com.lingyi.ai.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 电商日报分析表
 *
 * @author lingyi
 */
@Data
@TableName(value = "daily_report", autoResultMap = true)
public class DailyReportDO {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 报告日期
     */
    private LocalDate reportDate;

    /**
     * 今日销量
     */
    private Integer todaySales;

    /**
     * 昨日销量
     */
    private Integer yesterdaySales;

    /**
     * 销量环比增长率(%)
     */
    private BigDecimal salesGrowthRate;

    /**
     * 今日销售额
     */
    private BigDecimal todayRevenue;

    /**
     * 昨日销售额
     */
    private BigDecimal yesterdayRevenue;

    /**
     * 销售额环比增长率(%)
     */
    private BigDecimal revenueGrowthRate;

    /**
     * 上涨链接数
     */
    private Integer risingLinks;

    /**
     * 下跌链接数
     */
    private Integer fallingLinks;

    /**
     * 7天未出单链接数
     */
    private Integer noOrderLinks;

    /**
     * 摘要
     */
    private String summary;

    /**
     * 亮点分析(JSON数组)
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> highlights;

    /**
     * 风险预警(JSON数组)
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> risks;

    /**
     * 运营建议(JSON数组)
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> suggestions;

    /**
     * 完整报告(Markdown)
     */
    private String fullReport;
    /**
     * 关键报告摘要 (Markdown)
     */
    private String keyReport;
    /**
     * 周期总结报告 (Markdown)
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
     * 健康度评分(0-100)
     */
    private Integer healthScore;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
