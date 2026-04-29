package com.lingyi.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingyi.ai.dal.dataobject.DailyReportDO;
import com.lingyi.ai.dal.mapper.DailyReportMapper;
import com.lingyi.ai.model.dto.EcommerceDataDTO;
import com.lingyi.ai.model.vo.DailyReportDetailVO;
import com.lingyi.ai.model.vo.DailyReportListVO;
import com.lingyi.ai.model.vo.DailyReportPushVO;
import com.lingyi.ai.service.DailyReportService;
import com.lingyi.ai.service.ai.AiAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 日报服务实现
 *
 * @author lingyi
 */
@Slf4j
@Service
public class DailyReportServiceImpl implements DailyReportService {

    @Resource
    private DailyReportMapper dailyReportMapper;

    @Resource
    @Lazy
    private AiAnalysisService aiAnalysisService;

    @Override
    public void saveReport(DailyReportPushVO report, EcommerceDataDTO dataDTO) {
        log.info("保存日报，日期：{}", report.getReportDate());

        DailyReportDO existing = dailyReportMapper.selectOne(
                new LambdaQueryWrapper<DailyReportDO>()
                        .eq(DailyReportDO::getReportDate, report.getReportDate())
        );

        DailyReportDO reportDO = convertToDO(report, dataDTO);

        if (existing != null) {
            reportDO.setId(existing.getId());
            dailyReportMapper.updateById(reportDO);
            log.info("更新日报成功，ID：{}", existing.getId());
        } else {
            dailyReportMapper.insert(reportDO);
            log.info("新增日报成功");
        }
    }

    @Override
    public Page<DailyReportListVO> listReports(LocalDate startDate, LocalDate endDate, Integer pageNum, Integer pageSize) {
        pageNum = pageNum == null ? 1 : pageNum;
        pageSize = pageSize == null ? 10 : pageSize;

        LambdaQueryWrapper<DailyReportDO> wrapper = new LambdaQueryWrapper<>();
        if (startDate != null) {
            wrapper.ge(DailyReportDO::getReportDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(DailyReportDO::getReportDate, endDate);
        }
        wrapper.orderByDesc(DailyReportDO::getReportDate);

        Page<DailyReportDO> page = dailyReportMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        Page<DailyReportListVO> result = new Page<>();
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setTotal(page.getTotal());
        result.setPages(page.getPages());
        result.setRecords(page.getRecords().stream().map(this::convertToListVO).toList());

        return result;
    }

    @Override
    public DailyReportDetailVO getReportDetail(Long id) {
        DailyReportDO reportDO = dailyReportMapper.selectById(id);
        if (reportDO == null) {
            throw new RuntimeException("报告不存在，ID：" + id);
        }
        return convertToDetailVO(reportDO);
    }

    /**
     * 转换为 DO
     */
    private DailyReportDO convertToDO(DailyReportPushVO report, EcommerceDataDTO dataDTO) {
        DailyReportDO reportDO = new DailyReportDO();
        reportDO.setReportDate(dataDTO.getReportDate() != null ? dataDTO.getReportDate() : LocalDate.now());
        reportDO.setTodaySales(dataDTO.getTodaySales());
        reportDO.setYesterdaySales(dataDTO.getYesterdaySales());
        reportDO.setRisingLinks(dataDTO.getRisingLinks());
        reportDO.setFallingLinks(dataDTO.getFallingLinks());
        reportDO.setNoOrderLinks(dataDTO.getNoOrderLinks());
        reportDO.setTodayRevenue(dataDTO.getTodayRevenue());
        reportDO.setYesterdayRevenue(dataDTO.getYesterdayRevenue());

        // 计算增长率
        reportDO.setSalesGrowthRate(toBigDecimal(calculateGrowthRate(dataDTO.getYesterdaySales(), dataDTO.getTodaySales())));
        reportDO.setRevenueGrowthRate(toBigDecimal(calculateGrowthRate(dataDTO.getYesterdayRevenue(), dataDTO.getTodayRevenue())));

        // 报告内容
        reportDO.setSummary(report.getSummary());
        reportDO.setHighlights(report.getHighlights());
        reportDO.setRisks(report.getRisks());
        reportDO.setSuggestions(report.getSuggestions());
        reportDO.setFullReport(report.getFullReport());
        reportDO.setKeyReport(report.getKeyReport());
        reportDO.setHealthScore(report.getHealthScore());

        return reportDO;
    }

    /**
     * 转换为列表 VO
     */
    private DailyReportListVO convertToListVO(DailyReportDO reportDO) {
        DailyReportListVO vo = new DailyReportListVO();
        vo.setId(reportDO.getId());
        vo.setReportDate(reportDO.getReportDate());
        vo.setSummary(reportDO.getSummary());
        vo.setTodaySales(reportDO.getTodaySales());
        vo.setYesterdaySales(reportDO.getYesterdaySales());
        vo.setTodayRevenue(formatRevenue(reportDO.getTodayRevenue()));
        vo.setYesterdayRevenue(formatRevenue(reportDO.getYesterdayRevenue()));
        vo.setSalesGrowthRate(toDouble(reportDO.getSalesGrowthRate()));
        vo.setRevenueGrowthRate(toDouble(reportDO.getRevenueGrowthRate()));
        vo.setRisingLinks(reportDO.getRisingLinks());
        vo.setFallingLinks(reportDO.getFallingLinks());
        vo.setNoOrderLinks(reportDO.getNoOrderLinks());
        vo.setHealthScore(reportDO.getHealthScore());
        return vo;
    }

    /**
     * 转换为详情 VO
     */
    private DailyReportDetailVO convertToDetailVO(DailyReportDO reportDO) {
        DailyReportDetailVO vo = new DailyReportDetailVO();
        vo.setId(reportDO.getId());
        vo.setReportDate(reportDO.getReportDate());
        vo.setSummary(reportDO.getSummary());
        vo.setHighlights(reportDO.getHighlights());
        vo.setRisks(reportDO.getRisks());
        vo.setSuggestions(reportDO.getSuggestions());
        vo.setFullReport(reportDO.getFullReport());
        vo.setKeyReport(reportDO.getKeyReport());
        vo.setHealthScore(reportDO.getHealthScore());

        // 数据概览
        DailyReportDetailVO.DataVO dataVO = new DailyReportDetailVO.DataVO();
        dataVO.setTodaySales(reportDO.getTodaySales());
        dataVO.setYesterdaySales(reportDO.getYesterdaySales());
        dataVO.setSalesGrowthRate(toDouble(reportDO.getSalesGrowthRate()));
        dataVO.setTodayRevenue(formatRevenue(reportDO.getTodayRevenue()));
        dataVO.setYesterdayRevenue(formatRevenue(reportDO.getYesterdayRevenue()));
        dataVO.setRevenueGrowthRate(toDouble(reportDO.getRevenueGrowthRate()));
        dataVO.setRisingLinks(reportDO.getRisingLinks());
        dataVO.setFallingLinks(reportDO.getFallingLinks());
        dataVO.setNoOrderLinks(reportDO.getNoOrderLinks());
        vo.setData(dataVO);

        return vo;
    }

    /**
     * 计算增长率
     */
    private double calculateGrowthRate(BigDecimal yesterday, BigDecimal today) {
        if (yesterday == null || yesterday.compareTo(BigDecimal.ZERO) == 0) {
            return today == null || today.compareTo(BigDecimal.ZERO) == 0 ? 0.0 : 100.0;
        }
        return today.subtract(yesterday).multiply(BigDecimal.valueOf(100)).divide(yesterday, 2, BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    private double calculateGrowthRate(Integer yesterday, Integer today) {
        if (yesterday == null || yesterday == 0) {
            return today == null || today == 0 ? 0.0 : 100.0;
        }
        return (today - yesterday) * 100.0 / yesterday;
    }

    /**
     * 格式化销售额
     */
    private String formatRevenue(BigDecimal amount) {
        if (amount == null) {
            return "-";
        }
        if (amount.compareTo(BigDecimal.valueOf(10000)) >= 0) {
            return amount.divide(BigDecimal.valueOf(10000), 2, BigDecimal.ROUND_HALF_UP) + " 万";
        }
        return amount.toPlainString();
    }

    /**
     * BigDecimal → Double
     */
    private Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    /**
     * double → BigDecimal
     */
    private BigDecimal toBigDecimal(double value) {
        return BigDecimal.valueOf(value);
    }

    @Override
    public String getPeriodSummary(LocalDate startDate, LocalDate endDate) {
        return getPeriodSummary(startDate, endDate, false);
    }

    @Override
    public String getPeriodSummary(LocalDate startDate, LocalDate endDate, boolean forceRefresh) {
        log.info("生成周期总结，startDate：{}，endDate：{}，forceRefresh：{}", startDate, endDate, forceRefresh);

        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("开始日期和结束日期不能为空");
        }

        // 非强制刷新时，先查询是否已有相同日期范围的总结
        if (!forceRefresh) {
            LambdaQueryWrapper<DailyReportDO> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(DailyReportDO::getPeriodStartDate, startDate)
                        .eq(DailyReportDO::getPeriodEndDate, endDate)
                        .isNotNull(DailyReportDO::getPeriodSummary)
                        .last("LIMIT 1");

            DailyReportDO existingSummary = dailyReportMapper.selectOne(queryWrapper);
            if (existingSummary != null && existingSummary.getPeriodSummary() != null) {
                log.info("使用缓存的周期总结，ID：{}", existingSummary.getId());
                return existingSummary.getPeriodSummary();
            }
        } else {
            log.info("强制刷新周期总结，忽略缓存");
        }

        // 查询日期范围内的所有报告
        LambdaQueryWrapper<DailyReportDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(DailyReportDO::getReportDate, startDate)
               .le(DailyReportDO::getReportDate, endDate)
               .orderByAsc(DailyReportDO::getReportDate);

        List<DailyReportDO> reports = dailyReportMapper.selectList(wrapper);

        if (reports.isEmpty()) {
            throw new RuntimeException("选定日期范围内没有报告数据");
        }

        // 构建数据摘要
        StringBuilder dataSummary = new StringBuilder();
        dataSummary.append("【周期总结数据】\n");
        dataSummary.append("统计周期：").append(startDate).append(" 至 ").append(endDate).append("\n\n");

        double totalSales = 0;
        double totalRevenue = 0;
        int totalRising = 0;
        int totalFalling = 0;
        int totalNoOrder = 0;
        int count = reports.size();

        for (DailyReportDO report : reports) {
            dataSummary.append(report.getReportDate())
                .append("：销量").append(report.getTodaySales())
                .append("件，销售额").append(formatRevenue(report.getTodayRevenue()))
                .append("，上涨/下跌").append(report.getRisingLinks()).append("/").append(report.getFallingLinks())
                .append("，健康度").append(report.getHealthScore())
                .append("\n");

            totalSales += report.getTodaySales() != null ? report.getTodaySales() : 0;
            totalRevenue += report.getTodayRevenue() != null ? report.getTodayRevenue().doubleValue() : 0;
            totalRising += report.getRisingLinks() != null ? report.getRisingLinks() : 0;
            totalFalling += report.getFallingLinks() != null ? report.getFallingLinks() : 0;
            totalNoOrder += report.getNoOrderLinks() != null ? report.getNoOrderLinks() : 0;
        }

        dataSummary.append("\n【周期汇总】\n");
        dataSummary.append("总天数：").append(count).append("天\n");
        dataSummary.append("总销量：").append(String.format("%.0f", totalSales)).append("件\n");
        dataSummary.append("总销售额：").append(String.format("%.2f 万", totalRevenue / 10000)).append("\n");
        dataSummary.append("日均销量：").append(String.format("%.0f", totalSales / count)).append("件\n");
        dataSummary.append("日均销售额：").append(String.format("%.2f 元", totalRevenue / count)).append("\n");
        dataSummary.append("上涨商品总数：").append(totalRising).append("个\n");
        dataSummary.append("下跌商品总数：").append(totalFalling).append("个\n");
        dataSummary.append("滞销商品总数：").append(totalNoOrder).append("个\n");

        // 调用 AI 分析
        String systemPrompt = """
            你是一名电商运营分析师，需要对给定周期内的日报数据进行深度总结分析。

            ## 重要规则
            1. **只分析周期级别的数据**，不要重复每日报告的亮点/风险/建议
            2. **专注趋势和模式**，不要罗列每日数据（已在输入中提供）
            3. **不要输出"周期汇总"等汇总数据**（已在输入中提供）
            4. **所有内容必须基于输入数据**，禁止编造数字

            ## 分析重点
            1. **周期趋势**：整体走势（上升/下降/波动），哪几天是高峰/低谷
            2. **周期亮点**：整个周期内表现最好的 1-2 天及其特点
            3. **周期风险**：整个周期内表现最差的 1-2 天及其问题
            4. **商品结构**：上涨/下跌商品的整体趋势
            5. **运营建议**：针对下一周期的 2-3 条具体建议

            ## 输出格式
            ### 📈 周期趋势
            ### 🌟 周期亮点
            ### ⚠️ 周期风险
            ### 📦 商品结构分析
            ### 💡 运营建议

            请简洁明了，每个区块 2-4 句话即可。
            """;

        String summary = aiAnalysisService.callAiAnalysis(systemPrompt, dataSummary.toString());

        // 保存周期总结到数据库
        savePeriodSummary(startDate, endDate, summary);

        return summary;
    }

    /**
     * 保存周期总结到数据库
     */
    private void savePeriodSummary(LocalDate startDate, LocalDate endDate, String summary) {
        try {
            // 查找结束日期为 endDate 的报告，更新其周期总结字段
            LambdaQueryWrapper<DailyReportDO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(DailyReportDO::getReportDate, endDate)
                   .orderByDesc(DailyReportDO::getReportDate)
                   .last("LIMIT 1");

            DailyReportDO report = dailyReportMapper.selectOne(wrapper);
            if (report != null) {
                report.setPeriodSummary(summary);
                report.setPeriodStartDate(startDate);
                report.setPeriodEndDate(endDate);
                dailyReportMapper.updateById(report);
                log.info("保存周期总结成功，ID：{}", report.getId());
            }
        } catch (Exception e) {
            log.warn("保存周期总结失败", e);
            // 不抛出异常，避免影响返回结果
        }
    }

    @Override
    public int generateBatchData(int days) {
        log.info("批量生成测试数据，天数：{}", days);

        java.util.Random random = new java.util.Random();
        int count = 0;

        // 基础值
        int baseSales = 8000 + random.nextInt(4000); // 基础销量 8000-12000
        BigDecimal baseRevenue = BigDecimal.valueOf(600000 + random.nextInt(400000)); // 基础销售额 60-100 万

        // 先生成所有日期的今日数据（从旧到新）
        // k=0 对应最早日期 (now - days + 1)，k=days-1 对应最近日期 (now)
        int[] todaySalesArray = new int[days];
        BigDecimal[] todayRevenueArray = new BigDecimal[days];
        for (int k = 0; k < days; k++) {
            double fluctuation = 0.85 + random.nextDouble() * 0.3; // 0.85 ~ 1.15
            todaySalesArray[k] = (int) (baseSales * fluctuation);
            todayRevenueArray[k] = baseRevenue.multiply(BigDecimal.valueOf(fluctuation));
        }

        for (int k = 0; k < days; k++) {
            try {
                // 从最早日期开始，按时间正序生成
                LocalDate date = LocalDate.now().minusDays(days - 1 - k);

                // 检查是否已存在
                LambdaQueryWrapper<DailyReportDO> checkWrapper = new LambdaQueryWrapper<>();
                checkWrapper.eq(DailyReportDO::getReportDate, date);
                if (dailyReportMapper.selectCount(checkWrapper) > 0) {
                    log.info("日期 {} 已存在，跳过", date);
                    continue;
                }

                int todaySales = todaySalesArray[k];
                BigDecimal todayRevenue = todayRevenueArray[k];

                // 昨日数据：前一天的今日数据即为今天的昨日数据
                int yesterdaySales;
                BigDecimal yesterdayRevenue;
                if (k > 0) {
                    // 前一天（k-1）的今日数据作为本天的昨日数据
                    yesterdaySales = todaySalesArray[k - 1];
                    yesterdayRevenue = todayRevenueArray[k - 1];
                } else {
                    // 最老的日期（没有前一天数据），随机生成昨日数据
                    double growthRate = -0.15 + random.nextDouble() * 0.35;
                    yesterdaySales = (int) Math.round(todaySales / (1 + growthRate));
                    yesterdayRevenue = todayRevenue.divide(
                            BigDecimal.valueOf(1 + growthRate), 2, BigDecimal.ROUND_HALF_UP);
                }

                // 上涨/下跌/未出单链接数
                int totalLinks = 150 + random.nextInt(50); // 150-200 个链接
                int risingRatio = 40 + random.nextInt(40); // 40%-80% 上涨
                int risingLinks = (int) (totalLinks * risingRatio / 100);
                int fallingLinks = totalLinks - risingLinks - random.nextInt(20); // 剩余部分减去未出单
                int noOrderLinks = random.nextInt(30) + 10; // 10-40 个未出单

                // 确保 fallingLinks 不为负
                if (fallingLinks < 0) fallingLinks = random.nextInt(20) + 5;

                // 构建 DTO
                EcommerceDataDTO dto = new EcommerceDataDTO();
                dto.setReportDate(date);
                dto.setTodaySales(todaySales);
                dto.setYesterdaySales(yesterdaySales);
                dto.setTodayRevenue(todayRevenue.setScale(2, BigDecimal.ROUND_HALF_UP));
                dto.setYesterdayRevenue(yesterdayRevenue.setScale(2, BigDecimal.ROUND_HALF_UP));
                dto.setRisingLinks(risingLinks);
                dto.setFallingLinks(fallingLinks);
                dto.setNoOrderLinks(noOrderLinks);

                // 调用 AI 生成报告
                DailyReportPushVO reportVO = aiAnalysisService.generateDailyReport(dto);

                count++;
                log.info("生成日期 {} 的数据成功，今日销量：{}，昨日销量：{}，销售额：{}", date, todaySales, yesterdaySales, todayRevenue);

            } catch (Exception e) {
                log.error("生成第 {} 天数据失败", k, e);
            }
        }

        log.info("批量生成完成，成功生成 {} 条记录", count);
        return count;
    }
}
