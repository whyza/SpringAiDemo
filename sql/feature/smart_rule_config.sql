CREATE TABLE IF NOT EXISTS `smart_rule_config` (
  `id`               BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
  `r1_threshold`     DECIMAL(5,2) DEFAULT 20.00             COMMENT 'R1: 销售额大幅下滑阈值(%)',
  `r2_threshold`     DECIMAL(5,2) DEFAULT 30.00             COMMENT 'R2: 大量链接下跌阈值(%)',
  `r3_threshold`     DECIMAL(5,2) DEFAULT 30.00             COMMENT 'R3: 大量链接未出单阈值(%)',
  `y1_threshold`     DECIMAL(5,2) DEFAULT 10.00             COMMENT 'Y1: 销售额小幅下滑阈值(%)',
  `g1_threshold`     DECIMAL(5,2) DEFAULT 10.00             COMMENT 'G1: 销售额增长阈值(%)',
  `g2_threshold`     DECIMAL(5,2) DEFAULT 40.00             COMMENT 'G2: 上涨链接占比阈值(%)',
  `create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  `update_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全局规则配置表（阈值），全表仅一条记录';
