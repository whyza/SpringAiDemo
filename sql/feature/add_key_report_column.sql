-- 添加关键报告摘要字段
ALTER TABLE `daily_report`
ADD COLUMN `key_report` LONGTEXT COMMENT '关键报告摘要 (Markdown，不包含亮点/风险/建议)' AFTER `full_report`;

-- 更新现有数据（可选，将 full_report 复制到 key_report）
-- UPDATE `daily_report` SET `key_report` = `full_report` WHERE `key_report` IS NULL;
