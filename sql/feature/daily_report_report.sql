-- 电商日报分析表
CREATE TABLE IF NOT EXISTS `daily_report` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `report_date` DATE NOT NULL COMMENT '报告日期',
  `today_sales` INT NOT NULL COMMENT '今日销量',
  `yesterday_sales` INT NOT NULL COMMENT '昨日销量',
  `sales_growth_rate` DECIMAL(10,2) COMMENT '销量环比增长率(%)',
  `today_revenue` DECIMAL(15,2) NOT NULL COMMENT '今日销售额',
  `yesterday_revenue` DECIMAL(15,2) NOT NULL COMMENT '昨日销售额',
  `revenue_growth_rate` DECIMAL(10,2) COMMENT '销售额环比增长率(%)',
  `rising_links` INT NOT NULL COMMENT '上涨链接数',
  `falling_links` INT NOT NULL COMMENT '下跌链接数',
  `no_order_links` INT NOT NULL COMMENT '7天未出单链接数',
  `summary` VARCHAR(500) COMMENT '摘要',
  `highlights` TEXT COMMENT '亮点分析(JSON数组)',
  `risks` TEXT COMMENT '风险预警(JSON数组)',
  `suggestions` TEXT COMMENT '运营建议(JSON数组)',
  `full_report` LONGTEXT COMMENT '完整报告(Markdown)',
  `health_score` INT COMMENT '健康度评分(0-100)',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report_date` (`report_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电商日报分析表';
