package com.lingyi.ai.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 全局规则配置表（阈值），全表仅一条记录
 *
 * @author lingyi
 */
@Data
@TableName("smart_rule_config")
public class SmartRuleConfigDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private BigDecimal r1Threshold;
    private BigDecimal r2Threshold;
    private BigDecimal r3Threshold;
    private BigDecimal y1Threshold;
    private BigDecimal g1Threshold;
    private BigDecimal g2Threshold;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
