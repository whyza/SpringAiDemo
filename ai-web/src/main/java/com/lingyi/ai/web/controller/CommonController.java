package com.lingyi.ai.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingyi.ai.dal.dataobject.SmartReportConfigDO;
import com.lingyi.ai.model.dto.DailyReportQueryDTO;
import com.lingyi.ai.model.dto.EcommerceDataDTO;
import com.lingyi.ai.model.dto.SmartReportRequestDTO;
import com.lingyi.ai.model.vo.DailyReportDetailVO;
import com.lingyi.ai.model.vo.DailyReportListVO;
import com.lingyi.ai.model.vo.DailyReportPushVO;
import com.lingyi.ai.model.vo.SmartReportResultVO;
import com.lingyi.ai.service.DailyReportService;
import com.lingyi.ai.service.ai.AiAnalysisService;
import com.lingyi.ai.service.smart.SmartReportConfigService;
import com.lingyi.ai.service.smart.SmartReportEngineService;
import com.lingyi.ai.web.scheduler.DailyReportScheduler;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

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

    @Resource
    private SmartReportConfigService smartReportConfigService;

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
     * <p>未传业务数据时自动从数据库加载最新配置</p>
     *
     * @param request 包含销售指标和规则阈值的请求（可选）
     * @return 触发规则、诊断摘要和 AI 建议
     */
    @PostMapping("/smart-report/analyze")
    public Result<SmartReportResultVO> analyzeSmartReport(@RequestBody(required = false) SmartReportRequestDTO request) {
        if (request == null) {
            request = new SmartReportRequestDTO();
        }
        log.info("收到智能报告分析请求，日期：{}，是否有业务数据：{}", request.getReportDate(), !request.isDataMissing());
        try {
            SmartReportResultVO result = smartReportEngineService.analyze(request);
            // 分析成功后自动保存配置（含阈值和已加载的业务数据）
            smartReportConfigService.saveConfig(toConfigDO(request));
            return Result.success(result);
        } catch (Exception e) {
            log.error("智能报告分析失败", e);
            return Result.error("智能报告分析失败：" + e.getMessage());
        }
    }

    /**
     * 智能报告分析（流式进度），通过 SSE 实时推送进度
     */
    @PostMapping("/smart-report/analyze-stream")
    public SseEmitter analyzeSmartReportStream(@RequestBody(required = false) SmartReportRequestDTO request) {
        SmartReportRequestDTO req = request != null ? request : new SmartReportRequestDTO();
        log.info("收到智能报告流式分析请求，日期：{}", req.getReportDate());
        SseEmitter emitter = new SseEmitter(120_000L);

        Thread.ofVirtual().start(() -> {
            try {
                SmartReportResultVO result = smartReportEngineService.analyze(req, step -> {
                    try {
                        emitter.send(SseEmitter.event().name("progress").data(step));
                    } catch (IOException e) {
                        // client disconnected
                    }
                });
                // 分析成功后自动保存配置并推送结果
                emitter.send(SseEmitter.event().name("progress")
                    .data("{\"step\":\"saving\",\"message\":\"正在保存分析配置到数据库...\"}"));
                smartReportConfigService.saveConfig(toConfigDO(req));
                emitter.send(SseEmitter.event().name("result").data(result));
                emitter.complete();
            } catch (Exception e) {
                log.error("流式分析失败", e);
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    /**
     * 保存智能报告配置（规则阈值 + 业务数据）
     */
    @PostMapping("/smart-report/config/save")
    public Result<Void> saveSmartReportConfig(@RequestBody SmartReportRequestDTO request) {
        log.info("收到保存智能报告配置请求");
        try {
            smartReportConfigService.saveConfig(toConfigDO(request));
            return Result.success(null);
        } catch (Exception e) {
            log.error("保存智能报告配置失败", e);
            return Result.error("保存失败：" + e.getMessage());
        }
    }

    /**
     * 加载最新智能报告配置（仅返回阈值字段供前端展示）
     */
    @GetMapping("/smart-report/config/load")
    public Result<SmartReportConfigDO> loadSmartReportConfig() {
        log.info("收到加载智能报告配置请求");
        try {
            SmartReportConfigDO config = smartReportConfigService.loadLatest();
            return Result.success(config);
        } catch (Exception e) {
            log.error("加载智能报告配置失败", e);
            return Result.error("加载失败：" + e.getMessage());
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

    private SmartReportConfigDO toConfigDO(SmartReportRequestDTO request) {
        SmartReportConfigDO config = new SmartReportConfigDO();
        config.setTodayRevenue(request.getTodayRevenue());
        config.setYesterdayRevenue(request.getYesterdayRevenue());
        config.setTodayOrders(request.getTodayOrders());
        config.setYesterdayOrders(request.getYesterdayOrders());
        config.setTodayRisingLinks(request.getTodayRisingLinks());
        config.setYesterdayRisingLinks(request.getYesterdayRisingLinks());
        config.setTodayFallingLinks(request.getTodayFallingLinks());
        config.setYesterdayFallingLinks(request.getYesterdayFallingLinks());
        config.setTodayNoOrderLinks(request.getTodayNoOrderLinks());
        config.setYesterdayNoOrderLinks(request.getYesterdayNoOrderLinks());
        config.setR1Threshold(request.getR1Threshold());
        config.setR2Threshold(request.getR2Threshold());
        config.setR3Threshold(request.getR3Threshold());
        config.setY1Threshold(request.getY1Threshold());
        config.setG1Threshold(request.getG1Threshold());
        config.setG2Threshold(request.getG2Threshold());
        return config;
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
