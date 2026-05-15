package com.lingyi.ai.web.scheduler;

import com.lingyi.ai.model.dto.SmartReportRequestDTO;
import com.lingyi.ai.model.vo.SmartReportResultVO;
import com.lingyi.ai.service.smart.SmartReportEngineService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 智能报告 mock 数据定时任务
 * 定时生成随机销售数据调用分析接口，用于规则验证和效果观察
 *
 * @author lingyi
 */
@Slf4j
@Component
public class SmartReportMockScheduler {

    @Value("${task.smart-report-mock.enabled:false}")
    private boolean enabled;

    private final SmartReportEngineService smartReportEngineService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "mock-schd"));
    private final ExecutorService worker = Executors.newCachedThreadPool(r -> new Thread(r, "mock-worker"));

    public SmartReportMockScheduler(SmartReportEngineService smartReportEngineService) {
        this.smartReportEngineService = smartReportEngineService;
    }

    @PostConstruct
    public void init() {
        if (enabled) {
            log.info("智能报告 Mock 定时任务已开启，每次执行后随机等待 10~30s");
            scheduleNext();
        } else {
            log.debug("智能报告 Mock 定时任务未启用（task.smart-report-mock.enabled=true 开启）");
        }
    }

    @PreDestroy
    public void destroy() {
        scheduler.shutdown();
        worker.shutdown();
    }

    /**
     * 调度下一次执行，随机等待 10~30 秒
     */
    private void scheduleNext() {
        long delay = ThreadLocalRandom.current().nextInt(10000, 30001);
        scheduler.schedule(() -> {
            if (enabled) {
                scheduleNext();
                worker.submit(SmartReportMockScheduler.this::executeMockAnalysis);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void executeMockAnalysis() {
        log.info("========== 开始智能报告 Mock 分析 ==========");
        long startTime = System.currentTimeMillis();

        try {
            SmartReportRequestDTO request = generateMockData();
            SmartReportResultVO result = smartReportEngineService.analyze(request);

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("诊断结论 - 立即关注: {}, 值得注意: {}, 运营亮点: {}",
                    result.getDiagnosisConclusions().getRedAlerts(),
                    result.getDiagnosisConclusions().getYellowAlerts(),
                    result.getDiagnosisConclusions().getGreenHighlights());

            if (result.getOperationDiagnosis() != null) {
                log.info("运营诊断 - 整体表现: {} 条, 链接结构: {} 条, 异常判断: {} 条, 运营建议: {} 条",
                        result.getOperationDiagnosis().getOverallPerformance(),
                        result.getOperationDiagnosis().getLinkStructureAnalysis(),
                        result.getOperationDiagnosis().getAnomalyLogicJudgment(),
                        result.getOperationDiagnosis().getOperationSuggestions());
            }

            log.info("========== 智能报告 Mock 分析完成, 耗时: {}ms ==========", elapsed);

        } catch (Exception e) {
            log.error("========== 智能报告 Mock 分析失败 ==========", e);
        }
    }

    /**
     * 生成随机 mock 销售数据
     */
    private SmartReportRequestDTO generateMockData() {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        SmartReportRequestDTO dto = new SmartReportRequestDTO();
        dto.setReportDate(LocalDate.now());

        double todayRev = random.nextDouble(8000, 15000);
        double yesterdayRev = random.nextDouble(8000, 15000);
        dto.setTodayRevenue(BigDecimal.valueOf(todayRev).setScale(2, BigDecimal.ROUND_HALF_UP));
        dto.setYesterdayRevenue(BigDecimal.valueOf(yesterdayRev).setScale(2, BigDecimal.ROUND_HALF_UP));

        dto.setTodayOrders(random.nextInt(200, 500));
        dto.setYesterdayOrders(random.nextInt(200, 500));

        dto.setTodayRisingLinks(random.nextInt(5, 25));
        dto.setYesterdayRisingLinks(random.nextInt(5, 25));

        dto.setTodayFallingLinks(random.nextInt(5, 30));
        dto.setYesterdayFallingLinks(random.nextInt(5, 30));

        dto.setTodayNoOrderLinks(random.nextInt(10, 40));
        dto.setYesterdayNoOrderLinks(random.nextInt(10, 40));

        log.debug("生成 mock 数据: todayRevenue={}, yesterdayRevenue={}, todayOrders={}, yesterdayOrders={}, "
                        + "todayRising={}, yesterdayRising={}, todayFalling={}, yesterdayFalling={}, "
                        + "todayNoOrder={}, yesterdayNoOrder={}",
                dto.getTodayRevenue(), dto.getYesterdayRevenue(),
                dto.getTodayOrders(), dto.getYesterdayOrders(),
                dto.getTodayRisingLinks(), dto.getYesterdayRisingLinks(),
                dto.getTodayFallingLinks(), dto.getYesterdayFallingLinks(),
                dto.getTodayNoOrderLinks(), dto.getYesterdayNoOrderLinks());

        return dto;
    }
}
