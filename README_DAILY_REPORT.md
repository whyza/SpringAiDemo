# 电商 AI 日报功能使用指南

## 功能概述

本系统每日自动分析电商数据，通过 AI 生成运营建议，并推送到飞书。

### 核心功能

1. **自动定时任务** - 每个工作日上午 9 点自动生成日报
2. **AI 智能分析** - 基于通义千问大模型分析数据并给出建议
3. **飞书推送** - 自动推送到指定的飞书群（交互式卡片消息）
4. **手动触发接口** - 支持测试和补发

---

## 配置说明

### 1. 环境变量配置

复制 `.env.example` 为 `.env` 并配置：

```bash
# 通义千问 API Key
DASHSCOPE_API_KEY=sk-xxxxxxxx

# 飞书推送配置
FEISHU_WEBHOOK_ENABLED=true
FEISHU_WEBHOOK_URL=https://open.feishu.cn/open-apis/bot/v2/hook/YOUR_HOOK

# 定时任务开关
DAILY_REPORT_ENABLED=true
```

### 2. 飞书机器人配置

1. 打开飞书群聊
2. 点击右上角 `...` -> `群机器人` -> `添加机器人`
3. 选择 `自定义机器人` 或通过飞书开放平台创建
4. 获取 Webhook URL（格式：`https://open.feishu.cn/open-apis/bot/v2/hook/xxx`）
5. 将 URL 填入 `FEISHU_WEBHOOK_URL` 环境变量

---

## API 接口

### 1. 生成日报（不推送）

```bash
POST /api/daily-report/generate
Content-Type: application/json

{
  "reportDate": "2026-04-24",
  "todaySales": 12000,
  "yesterdaySales": 11234,
  "todayRevenue": 987623,
  "yesterdayRevenue": 965443,
  "risingLinks": 124,
  "fallingLinks": 45,
  "noOrderLinks": 129
}
```

响应：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "reportDate": "2026-04-24",
    "summary": "今日整体表现良好，销量和销售额双增长",
    "data": {
      "todaySales": 12000,
      "yesterdaySales": 11234,
      "salesGrowthRate": 6.82,
      "todayRevenue": "98.76 万",
      "yesterdayRevenue": "96.54 万",
      "revenueGrowthRate": 2.30,
      "risingLinks": 124,
      "fallingLinks": 45,
      "noOrderLinks": 129
    },
    "highlights": ["销量环比增长 6.82%", "上涨商品数是下跌的 2.75 倍"],
    "risks": ["129 个链接 7 天未出单，需重点关注"],
    "suggestions": ["建议对 129 个未出单商品进行促销或下架处理", ...],
    "healthScore": 75,
    "fullReport": "..."
  }
}
```

### 2. 生成并推送日报

```bash
POST /api/daily-report/push
Content-Type: application/json

{
  "todaySales": 12000,
  "yesterdaySales": 11234,
  ...
}
```

或不传参数使用示例数据：
```bash
POST /api/daily-report/push
```

---

## 第三方数据接入

### 修改 `DailyReportScheduler.java`

找到 `fetchThirdPartyData()` 方法，替换为实际的第三方 API 调用：

```java
private EcommerceDataDTO fetchThirdPartyData() {
    LocalDate yesterday = LocalDate.now().minusDays(1);
    
    // 示例：调用第三方 API
    ThirdPartyResponse response = restTemplate.getForObject(
        "http://third-party-api/daily-data?date=" + yesterday,
        ThirdPartyResponse.class
    );
    
    // 转换为 EcommerceDataDTO
    EcommerceDataDTO dto = new EcommerceDataDTO();
    dto.setReportDate(yesterday);
    dto.setTodaySales(response.getTodaySales());
    dto.setYesterdaySales(response.getYesterdaySales());
    dto.setTodayRevenue(response.getTodayRevenue());
    dto.setYesterdayRevenue(response.getYesterdayRevenue());
    dto.setRisingLinks(response.getRisingLinks());
    dto.setFallingLinks(response.getFallingLinks());
    dto.setNoOrderLinks(response.getNoOrderLinks());
    
    return dto;
}
```

---

## 推送效果示例

飞书交互式卡片消息格式：

```
┌─────────────────────────────────────────────────────┐
│  📊 电商运营日报报告                    [查看完整报告] │
├─────────────────────────────────────────────────────┤
│  📅 报告日期：2026-04-24                            │
├─────────────────────────────────────────────────────┤
│  📌 今日概览                                        │
│  今日整体表现良好，销量和销售额实现双增长。          │
├─────────────────────────────────────────────────────┤
│  📈 核心数据                                        │
│  ┌─────────────────┬─────────────────┐              │
│  │ 📦 今日销量：12000 件              │ 💰 今日：98.76 万 │
│  │ 📉 昨日销量：11234 件              │ 💰 昨日：96.54 万 │
│  │ 📊 环比：🟢 +6.82%                │ 📊 环比：🟢 +2.30% │
│  └─────────────────┴─────────────────┘              │
├─────────────────────────────────────────────────────┤
│  🔍 商品健康度                                      │
│  🟢 上涨商品：124 个  🔴 下跌商品：45 个            │
│  ⚪ 7 天未出单：129 个                               │
│  💯 健康度评分：75 🟡 良好                          │
├─────────────────────────────────────────────────────┤
│  ✅ 亮点分析                                        │
│  • 销量环比增长 6.82%，表现优秀                      │
│  • 上涨商品数是下跌商品的 2.75 倍                    │
├─────────────────────────────────────────────────────┤
│  ⚠️ 风险预警                                        │
│  • 129 个链接 7 天未出单，存在滞销风险               │
├─────────────────────────────────────────────────────┤
│  💡 运营建议                                        │
│  🔴 止血层：对 129 个未出单商品进行促销或下架处理     │
│  🟡 优化层：分析 45 个下跌商品的原因（库存/评价/竞品）│
│  🟢 战略层：总结 124 个上涨商品的成功经验并复制       │
└─────────────────────────────────────────────────────┘
```

---

## 定时任务管理

### 启用/禁用定时任务

修改配置文件 `application.yml`：
```yaml
task:
  daily-report:
    enabled: false  # 禁用定时任务
```

或环境变量：
```bash
DAILY_REPORT_ENABLED=false
```

### 修改执行时间

```yaml
task:
  daily-report:
    cron: "0 0 9 * * MON-FRI"  # 每个工作日上午 9 点
```

常用 cron 表达式：
- `0 0 9 * * *` - 每天上午 9 点
- `0 0 9 * * MON-FRI` - 工作日每天上午 9 点
- `0 30 18 * * *` - 每天下午 6:30

---

## 故障排查

### 1. 定时任务未执行

检查日志：
```bash
grep "开始生成电商日报" logs/app.log
```

确认定时任务已启用：
```bash
grep "task.daily-report" logs/app.log
```

### 2. 飞书推送失败

检查 Webhook URL 是否正确：
```bash
echo $FEISHU_WEBHOOK_URL
```

测试 Webhook：
```bash
curl -X POST "$FEISHU_WEBHOOK_URL" \
  -H "Content-Type: application/json" \
  -d '{"msg_type":"text","content":{"text":"测试消息"}}'
```

### 3. AI 调用失败

检查 API Key：
```bash
echo $DASHSCOPE_API_KEY
```

查看通义千问控制台是否欠费或限额。

---

## 技术栈

- **Java 21**
- **Spring Boot 3.2.0**
- **Spring AI Alibaba** (通义千问)
- **飞书机器人** (交互式卡片推送)

---

## 常见问题

**Q: 如何修改推送时间？**
A: 修改 `application.yml` 中的 `task.daily-report.cron` 配置。

**Q: 如何推送到多个群？**
A: 创建多个飞书 Webhook，在 `FeishuPushService` 中循环推送。

**Q: 如何添加邮件推送？**
A: 参考 `FeishuPushService` 创建 `EmailPushService`，在 `DailyReportScheduler` 中调用。

**Q: 第三方数据接口如何对接？**
A: 修改 `DailyReportScheduler.fetchThirdPartyData()` 方法，调用实际 API。
