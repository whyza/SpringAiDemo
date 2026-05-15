package com.lingyi.ai.service.smart;

import com.lingyi.ai.dal.dataobject.SmartReportConfigDO;

/**
 * 智能报告配置服务
 *
 * @author lingyi
 */
public interface SmartReportConfigService {

    /**
     * 保存配置（插入新记录）
     */
    void saveConfig(SmartReportConfigDO config);

    /**
     * 加载最新配置
     */
    SmartReportConfigDO loadLatest();
}
