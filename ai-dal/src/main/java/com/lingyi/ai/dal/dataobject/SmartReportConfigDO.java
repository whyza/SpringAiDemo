package com.lingyi.ai.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 智能报告配置表（规则阈值+业务数据）
 *
 * @author lingyi
 */
@Data
@TableName("smart_report_config")
public class SmartReportConfigDO {

    @TableId(type = IdType.AUTO)
    private Long id;

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

    private BigDecimal r1Threshold;
    private BigDecimal r2Threshold;
    private BigDecimal r3Threshold;
    private BigDecimal y1Threshold;
    private BigDecimal g1Threshold;
    private BigDecimal g2Threshold;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
