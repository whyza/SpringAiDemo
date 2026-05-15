package com.lingyi.ai.service.smart.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lingyi.ai.dal.dataobject.SmartReportConfigDO;
import com.lingyi.ai.dal.mapper.SmartReportConfigMapper;
import com.lingyi.ai.service.smart.SmartReportConfigService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 智能报告配置服务实现
 *
 * @author lingyi
 */
@Slf4j
@Service
public class SmartReportConfigServiceImpl implements SmartReportConfigService {

    @Resource
    private SmartReportConfigMapper smartReportConfigMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveConfig(SmartReportConfigDO config) {
        // 清理旧记录，只保留最新一条
        smartReportConfigMapper.delete(null);
        smartReportConfigMapper.insert(config);
        log.info("智能报告配置已保存，id={}", config.getId());
    }

    @Override
    public SmartReportConfigDO loadLatest() {
        LambdaQueryWrapper<SmartReportConfigDO> wrapper = new LambdaQueryWrapper<SmartReportConfigDO>()
                .orderByDesc(SmartReportConfigDO::getId)
                .last("LIMIT 1");
        return smartReportConfigMapper.selectOne(wrapper);
    }
}
