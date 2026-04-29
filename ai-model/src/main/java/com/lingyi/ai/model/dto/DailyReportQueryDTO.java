package com.lingyi.ai.model.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 日报查询请求 DTO
 *
 * @author lingyi
 */
@Data
public class DailyReportQueryDTO {

    /**
     * 开始日期
     */
    private LocalDate startDate;

    /**
     * 结束日期
     */
    private LocalDate endDate;

    /**
     * 页码（默认 1）
     */
    private Integer pageNum;

    /**
     * 每页数量（默认 10）
     */
    private Integer pageSize;

    /**
     * 是否强制刷新（默认 false）
     */
    private boolean forceRefresh;
}
