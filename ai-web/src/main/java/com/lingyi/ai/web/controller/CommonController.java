package com.lingyi.ai.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingyi.ai.model.dto.DailyReportQueryDTO;
import com.lingyi.ai.model.dto.EcommerceDataDTO;
import com.lingyi.ai.model.dto.SmartReportRequestDTO;
import com.lingyi.ai.model.vo.DailyReportDetailVO;
import com.lingyi.ai.model.vo.DailyReportListVO;
import com.lingyi.ai.model.vo.DailyReportPushVO;
import com.lingyi.ai.model.vo.SmartReportResultVO;
import com.lingyi.ai.service.DailyReportService;
import com.lingyi.ai.service.ai.AiAnalysisService;
import com.lingyi.ai.service.ai.impl.AiAnalysisServiceImpl;
import com.lingyi.ai.service.smart.SmartReportEngineService;
import com.lingyi.ai.web.scheduler.DailyReportScheduler;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;

/**
 * 通用结果封装
 *
 * @author lingyi
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class CommonController {

    @Resource
    private AiAnalysisService aiAnalysisService;

    @Resource
    private DailyReportScheduler dailyReportScheduler;

    @Resource
    private DailyReportService dailyReportService;

    @Resource
    private SmartReportEngineService smartReportEngineService;

    /**
     * 批量生成测试数据（近 N 天）
     */
    @PostMapping("/daily-report/generate-batch")
    public Result<Integer> generateBatchData(@RequestParam(defaultValue = "10", name = "days") int days) {
        log.info("批量生成测试数据，天数：{}", days);
        try {
            int count = dailyReportService.generateBatchData(days);
            return Result.success(count);
        } catch (Exception e) {
            log.error("批量生成失败", e);
            return Result.error("批量生成失败：" + e.getMessage());
        }
    }

    /**
     * 生成电商日报（AI 分析 + 推送格式）
     *
     * @param dataDTO 电商数据
     * @return 日报推送数据
     */
    @PostMapping("/daily-report/generate")
    public Result<DailyReportPushVO> generateDailyReport(@RequestBody @Validated EcommerceDataDTO dataDTO) {
        log.info("收到日报生成请求");
        DailyReportPushVO report = aiAnalysisService.generateDailyReport(dataDTO);
        return Result.success(report);
    }

    /**
     * 流式生成电商日报（SSE 实时推送 AI 分析内容）
     *
     * @param dataDTO 电商数据
     * @return SSE Emitter
     */
    @PostMapping(value = "/daily-report/generate-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateDailyReportStream(@RequestBody @Validated EcommerceDataDTO dataDTO) {
        log.info("收到流式日报生成请求");
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L); // 5 分钟超时
        StringBuilder fullText = new StringBuilder();

        AiAnalysisServiceImpl serviceImpl = (AiAnalysisServiceImpl) aiAnalysisService;
        serviceImpl.streamAiAnalysis(AiAnalysisServiceImpl.SYSTEM_PROMPT, serviceImpl.buildPrompt(dataDTO)).doOnNext(
                chunk -> {
                    fullText.append(chunk);
                    try {
                        emitter.send(SseEmitter.event().name("content").data(chunk));
                    } catch (Exception e) {
                        log.warn("推送 SSE chunk 失败", e);
                    }
                }).doOnComplete(() -> {
            try {
                String aiResponse = fullText.toString();
                DailyReportPushVO reportVO = serviceImpl.buildAndSaveReport(dataDTO, aiResponse);
                emitter.send(SseEmitter.event().name("done").data(reportVO));
                emitter.complete();
                log.info("流式日报生成完成，健康度评分：{}", reportVO.getHealthScore());
            } catch (Exception e) {
                log.error("流式日报构建失败", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data("日报生成失败：" + e.getMessage()));
                    emitter.complete();
                } catch (Exception ex) {
                    log.warn("推送 SSE 错误事件失败", ex);
                }
            }
        }).doOnError(e -> {
            log.error("AI 流式分析失败", e);
            try {
                emitter.send(SseEmitter.event().name("error").data("AI 调用失败：" + e.getMessage()));
                emitter.complete();
            } catch (Exception ex) {
                log.warn("推送 SSE 错误事件失败", ex);
            }
        }).subscribe();

        return emitter;
    }

    /**
     * 手动触发日报推送（测试用）
     *
     * @param dataDTO 电商数据（可选，不提供则使用示例数据）
     * @return 执行结果
     */
    @PostMapping("/daily-report/push")
    public Result<String> pushDailyReport(@RequestBody(required = false) EcommerceDataDTO dataDTO) {
        log.info("收到日报推送请求");
        try {
            if (dataDTO != null) {
                dailyReportScheduler.manualTrigger(dataDTO);
            } else {
                dailyReportScheduler.generateAndPushDailyReport();
            }
            return Result.success("日报推送成功");
        } catch (Exception e) {
            log.error("日报推送失败", e);
            return Result.error("日报推送失败：" + e.getMessage());
        }
    }

    /**
     * 分页查询日报列表
     *
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    @PostMapping("/daily-report/list")
    public Result<Page<DailyReportListVO>> listDailyReports(@RequestBody DailyReportQueryDTO queryDTO) {
        log.info(
                "查询日报列表，startDate：{}，endDate：{}，pageNum：{}，pageSize：{}",
                queryDTO.getStartDate(),
                queryDTO.getEndDate(),
                queryDTO.getPageNum(),
                queryDTO.getPageSize()
        );
        Page<DailyReportListVO> page = dailyReportService.listReports(
                queryDTO.getStartDate(),
                queryDTO.getEndDate(),
                queryDTO.getPageNum(),
                queryDTO.getPageSize()
        );
        return Result.success(page);
    }

    /**
     * 查询日报详情
     *
     * @param id 报告 ID
     * @return 报告详情
     */
    @GetMapping("/daily-report/{id}")
    public Result<DailyReportDetailVO> getDailyReportDetail(@PathVariable("id") Long id) {
        log.info("查询日报详情，id：{}", id);
        DailyReportDetailVO detail = dailyReportService.getReportDetail(id);
        return Result.success(detail);
    }

    /**
     * 智能报告分析（规则引擎 + AI 运营建议）
     *
     * @param request 包含销售指标和规则阈值的请求
     * @return 触发规则、诊断摘要和 AI 建议
     */
    @PostMapping("/smart-report/analyze")
    public Result<SmartReportResultVO> analyzeSmartReport(@RequestBody @Validated SmartReportRequestDTO request) {
        log.info("收到智能报告分析请求，日期：{}", request.getReportDate());
        try {
            SmartReportResultVO result = smartReportEngineService.analyze(request);
            return Result.success(result);
        } catch (Exception e) {
            log.error("智能报告分析失败", e);
            return Result.error("智能报告分析失败：" + e.getMessage());
        }
    }

    /**
     * 流式智能报告分析（规则引擎同步 + AI 内容流式输出）
     *
     * @param request 包含销售指标和规则阈值的请求
     * @return SSE Emitter
     */
    @PostMapping(value = "/smart-report/analyze-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter analyzeSmartReportStream(@RequestBody @Validated SmartReportRequestDTO request) {
        log.info("收到智能报告流式分析请求，日期：{}", request.getReportDate());
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
        StringBuilder fullAiText = new StringBuilder();

        smartReportEngineService.streamAnalyze(request).doOnNext(chunk -> {
            if (chunk.startsWith("[RULES]")) {
                try {
                    emitter.send(SseEmitter.event().name("rules").data(chunk.substring(7)));
                } catch (Exception e) {
                    log.warn("推送 rules 事件失败", e);
                }
            } else if (chunk.startsWith("[SUMMARY]")) {
                try {
                    emitter.send(SseEmitter.event().name("summary").data(chunk.substring(9)));
                } catch (Exception e) {
                    log.warn("推送 summary 事件失败", e);
                }
            } else if (chunk.startsWith("[METRICS]")) {
                try {
                    String metricsData = chunk.substring(9);
                    SmartReportResultVO vo = new SmartReportResultVO();
                    parseMetricsToVO(metricsData, fullAiText.toString(), vo);
                    emitter.send(SseEmitter.event().name("done").data(vo));
                    log.info("智能报告流式分析完成");
                } catch (Exception e) {
                    log.error("推送 done 事件失败", e);
                }
            } else {
                fullAiText.append(chunk);
                try {
                    emitter.send(SseEmitter.event().name("content").data(chunk));
                } catch (Exception e) {
                    log.warn("推送 content chunk 失败", e);
                }
            }
        }).doOnComplete(() -> {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.warn("完成流式响应失败", e);
            }
        }).doOnError(e -> {
            log.error("AI 流式分析失败", e);
            try {
                emitter.send(SseEmitter.event().name("error").data("AI 调用失败：" + e.getMessage()));
                emitter.complete();
            } catch (Exception ex) {
                log.warn("推送 SSE 错误事件失败", ex);
            }
        }).subscribe();

        return emitter;
    }

    private void parseMetricsToVO(String metricsData, String aiContent, SmartReportResultVO vo) {
        SmartReportResultVO.MetricsVO metrics = new SmartReportResultVO.MetricsVO();
        for (String pair : metricsData.split(";")) {
            String[] kv = pair.split(":", 2);
            if (kv.length != 2) continue;
            String val = kv[1];
            switch (kv[0]) {
                case "todayRevenue": metrics.setTodayRevenue(parseBigDecimal(val)); break;
                case "todayOrders": metrics.setTodayOrders(parseInteger(val)); break;
                case "weeklyRevenue": metrics.setWeeklyRevenue(parseBigDecimal(val)); break;
                case "lastWeekRevenue": metrics.setLastWeekRevenue(parseBigDecimal(val)); break;
                case "risingLinks": metrics.setRisingLinks(parseInteger(val)); break;
                case "fallingLinks": metrics.setFallingLinks(parseInteger(val)); break;
                case "noOrderLinks": metrics.setNoOrderLinks(parseInteger(val)); break;
                case "profitRate": metrics.setProfitRate(parseBigDecimal(val)); break;
                case "profitRateChange": metrics.setProfitRateChange(parseBigDecimal(val)); break;
                case "revenueChangeRate": metrics.setRevenueChangeRate(parseBigDecimal(val)); break;
            }
        }
        vo.setAiContent(aiContent);
        vo.setMetrics(metrics);
    }

    private BigDecimal parseBigDecimal(String val) {
        try {
            return val == null || val.isEmpty() ? null : new BigDecimal(val);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseInteger(String val) {
        try {
            return val == null || val.isEmpty() ? null : Integer.parseInt(val);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 健康检查
     *
     * @return 健康状态
     */
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("OK");
    }

    /**
     * 周期总结 - 对指定日期范围内的日报数据进行 AI 总结
     *
     * @param queryDTO 日期范围查询条件
     * @return 周期总结报告
     */
    @PostMapping("/daily-report/summary")
    public Result<String> getPeriodSummary(@RequestBody DailyReportQueryDTO queryDTO) {
        log.info(
                "请求周期总结，startDate：{}，endDate：{}，forceRefresh：{}",
                queryDTO.getStartDate(),
                queryDTO.getEndDate(),
                queryDTO.isForceRefresh()
        );
        try {
            String summary = dailyReportService.getPeriodSummary(
                    queryDTO.getStartDate(),
                    queryDTO.getEndDate(),
                    queryDTO.isForceRefresh()
            );
            return Result.success(summary);
        } catch (Exception e) {
            log.error("周期总结失败", e);
            return Result.error("周期总结失败：" + e.getMessage());
        }
    }

    /**
     * 通用结果封装类
     */
    @lombok.Data
    public static class Result<T> {
        private Integer code;
        private String message;
        private T data;

        public static <T> Result<T> success(T data) {
            Result<T> result = new Result<>();
            result.setCode(200);
            result.setMessage("success");
            result.setData(data);
            return result;
        }

        public static <T> Result<T> error(String message) {
            Result<T> result = new Result<>();
            result.setCode(500);
            result.setMessage(message);
            return result;
        }
    }

}
