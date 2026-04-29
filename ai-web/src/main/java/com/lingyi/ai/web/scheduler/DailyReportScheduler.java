package com.lingyi.ai.web.scheduler;

import com.lingyi.ai.model.dto.EcommerceDataDTO;
import com.lingyi.ai.model.vo.DailyReportPushVO;
import com.lingyi.ai.service.ai.AiAnalysisService;
import com.lingyi.ai.web.push.FeishuPushService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 电商日报定时任务调度器
 *
 * @author lingyi
 */
@Slf4j
@Component
public class DailyReportScheduler {

    @Value("${task.daily-report.enabled:true}")
    private boolean enabled;

    private final AiAnalysisService aiAnalysisService;
    private final FeishuPushService feishuPushService;

    public DailyReportScheduler(AiAnalysisService aiAnalysisService,
                                FeishuPushService feishuPushService) {
        this.aiAnalysisService = aiAnalysisService;
        this.feishuPushService = feishuPushService;
    }

    /**
     * 每个工作日上午 9 点生成并推送日报
     * 注意：实际使用时需要配置第三方数据接口调用
     */
    @Scheduled(cron = "0 0 9 * * MON-FRI")
    public void generateAndPushDailyReport() {
        if (!enabled) {
            log.debug("日报定时任务未启用，跳过");
            return;
        }

        log.info("========== 开始生成电商日报 ==========");

        try {
            // 1. 从第三方接口获取昨日数据
            EcommerceDataDTO dataDTO = fetchThirdPartyData();

            // 2. 调用 AI 生成日报
            DailyReportPushVO report = aiAnalysisService.generateDailyReport(dataDTO);

            // 3. 推送到飞书
            feishuPushService.pushDailyReport(report);

            log.info("========== 日报推送完成 ==========");

        } catch (Exception e) {
            log.error("========== 日报生成失败 ==========", e);
            // TODO: 可以添加告警通知，如发送失败通知到管理员
        }
    }

    /**
     * 从第三方接口获取电商数据
     * TODO: 需要根据实际第三方 API 进行实现
     */
    private EcommerceDataDTO fetchThirdPartyData() {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        // 示例：调用第三方 API 获取数据
        // 这里需要根据实际的第三方 API 接口进行实现
        // 例如：restTemplate.getForObject(thirdPartyApiUrl, ThirdPartyResponse.class)

        // 临时示例数据（实际使用时删除）
        EcommerceDataDTO dto = new EcommerceDataDTO();
        dto.setReportDate(yesterday);
        dto.setTodaySales(12000);
        dto.setYesterdaySales(11234);
        dto.setTodayRevenue(java.math.BigDecimal.valueOf(987623));
        dto.setYesterdayRevenue(java.math.BigDecimal.valueOf(965443));
        dto.setRisingLinks(124);
        dto.setFallingLinks(45);
        dto.setNoOrderLinks(129);

        log.info("从第三方接口获取数据完成");
        return dto;
    }

    /**
     * 手动触发日报生成（用于测试或补发）
     * 可以通过调用此接口手动触发
     */
    public void manualTrigger(EcommerceDataDTO dataDTO) {
        log.info("手动触发日报生成");
        DailyReportPushVO report = aiAnalysisService.generateDailyReport(dataDTO);
        feishuPushService.pushDailyReport(report);
    }
}
