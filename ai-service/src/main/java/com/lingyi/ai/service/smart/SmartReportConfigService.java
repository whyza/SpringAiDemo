package com.lingyi.ai.service.smart;

import com.lingyi.ai.dal.dataobject.SmartReportConfigDO;

import java.time.LocalDate;

/**
 * 智能报告配置服务
 *
 * @author lingyi
 */
public interface SmartReportConfigService {

    /**
     * 保存配置（按日期覆盖）
     */
    void saveConfig(SmartReportConfigDO config);

    /**
     * 加载指定日期的配置
     */
    SmartReportConfigDO loadByDate(LocalDate date);
}
