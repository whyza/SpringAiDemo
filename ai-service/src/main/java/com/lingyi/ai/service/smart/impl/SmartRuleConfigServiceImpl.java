package com.lingyi.ai.service.smart.impl;

import com.lingyi.ai.dal.dataobject.SmartRuleConfigDO;
import com.lingyi.ai.dal.mapper.SmartRuleConfigMapper;
import com.lingyi.ai.service.smart.SmartRuleConfigService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 全局规则配置服务实现
 *
 * @author lingyi
 */
@Slf4j
@Service
public class SmartRuleConfigServiceImpl implements SmartRuleConfigService {

    @Resource
    private SmartRuleConfigMapper smartRuleConfigMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveConfig(SmartRuleConfigDO config) {
        smartRuleConfigMapper.delete(null);
        smartRuleConfigMapper.insert(config);
        log.info("全局规则配置已保存，id={}", config.getId());
    }

    @Override
    public SmartRuleConfigDO loadConfig() {
        return smartRuleConfigMapper.selectOne(null);
    }
}
