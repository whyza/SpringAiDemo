package com.lingyi.ai.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingyi.ai.dal.dataobject.DailyReportDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 日报 Mapper
 *
 * @author lingyi
 */
@Mapper
public interface DailyReportMapper extends BaseMapper<DailyReportDO> {
}
