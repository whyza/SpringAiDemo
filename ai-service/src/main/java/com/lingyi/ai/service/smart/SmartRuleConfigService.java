package com.lingyi.ai.service.smart;

import com.lingyi.ai.dal.dataobject.SmartRuleConfigDO;

/**
 * 全局规则配置服务
 *
 * @author lingyi
 */
public interface SmartRuleConfigService {

    /**
     * 保存全局规则阈值（覆盖写入，仅保留一条）
     */
    void saveConfig(SmartRuleConfigDO config);

    /**
     * 加载全局规则阈值
     */
    SmartRuleConfigDO loadConfig();
}
