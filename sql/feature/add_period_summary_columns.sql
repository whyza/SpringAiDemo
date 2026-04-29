-- 添加周期总结相关字段
ALTER TABLE `daily_report`
ADD COLUMN `period_summary` LONGTEXT COMMENT '周期总结报告 (Markdown)' AFTER `key_report`,
ADD COLUMN `period_start_date` DATE COMMENT '周期总结开始日期' AFTER `period_summary`,
ADD COLUMN `period_end_date` DATE COMMENT '周期总结结束日期' AFTER `period_start_date`;

-- 添加索引加速查询
CREATE INDEX `idx_period_date` ON `daily_report` (`period_start_date`, `period_end_date`) WHERE `period_summary` IS NOT NULL;
