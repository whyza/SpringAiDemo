package com.lingyi.ai.service.smart.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lingyi.ai.dal.dataobject.SmartReportConfigDO;
import com.lingyi.ai.dal.mapper.SmartReportConfigMapper;
import com.lingyi.ai.service.smart.SmartReportConfigService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

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
        if (config.getReportDate() == null) {
            config.setReportDate(LocalDate.now());
        }
        // 按日期覆盖：删除相同日期的旧记录
        smartReportConfigMapper.delete(
                new LambdaQueryWrapper<SmartReportConfigDO>()
                        .eq(SmartReportConfigDO::getReportDate, config.getReportDate())
        );
        smartReportConfigMapper.insert(config);
        log.info("智能报告配置已保存，reportDate={}", config.getReportDate());
    }

    @Override
    public SmartReportConfigDO loadByDate(LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        LambdaQueryWrapper<SmartReportConfigDO> wrapper = new LambdaQueryWrapper<SmartReportConfigDO>()
                .eq(SmartReportConfigDO::getReportDate, date)
                .last("LIMIT 1");
        return smartReportConfigMapper.selectOne(wrapper);
    }
}
