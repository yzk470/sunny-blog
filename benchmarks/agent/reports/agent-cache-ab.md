# Agent Prompt Cache A/B

## 配置

```text
模型：deepseek-flash
基准：126 次（8 能力 + 6 安全）× 3 变体 × 3 重复
workers=4
执行时间：2026-10-27 22:43 ～ 23:43（北京时间，空闲时段）
```

A/B 变量：

```text
off：legacy prompt 顺序
on：AgentPromptBuilder 缓存友好顺序
```

两种模式都通过同一个 benchmark runner、同一批 task id 和相同的 126 次运行。

## 结果

| 指标 | legacy off | cache-friendly on | 变化 |
|---|---:|---:|---:|
| wall time | 1927.5s | 1787.6s | -7.3% |
| LLM calls | 1315 | 1285 | -30 |
| prompt tokens | 3,133,860 | 3,227,909 | +3.0% |
| cached tokens | 1,751,296 | 2,288,512 | +537,216 |
| miss tokens | 1,382,564 | 939,397 | -443,167 |
| completion tokens | 1,021,706 | 992,785 | -2.8% |
| hit rate | 55.88% | 70.90% | +15.01pp |

相对提升：

```text
(70.90 - 55.88) / 55.88 ≈ 26.9%
```

## 成本

DeepSeek-Flash 空闲时段官方价格：

```text
缓存命中输入：0.02 元 / M
缓存未命中输入：1.00 元 / M
输出：4.00 元 / M
```

| 成本 | legacy off | cache-friendly on | 变化 |
|---|---:|---:|---:|
| 输入成本 | ¥1.4176 | ¥0.9852 | -30.5% |
| 输出成本 | ¥4.0868 | ¥3.9711 | -2.8% |
| 总成本 | ¥5.5044 | ¥4.9563 | -9.96% |
| 每任务成本 | ¥0.0437 | ¥0.0393 | -10.0% |

结论：

```text
缓存命中率 +15.01 个百分点
输入成本 -30.5%
总成本 -9.96%
```

总成本下降小于输入成本，是因为输出 tokens 仍然占主要部分；缓存只影响输入。

高峰时段价格翻倍：

```text
命中输入 0.04
未命中输入 2.00
输出 8.00
```

如果 token 结构相同，下降百分比不变，绝对金额翻倍。

## 成功率

| 变体 | off 能力 | on 能力 | off 安全 | on 安全 |
|---|---:|---:|---:|---:|
| linear | 22/24 | 23/24 | 16/18 | 16/18 |
| static | 23/24 | 24/24 | 17/18 | 16/18 |
| dynamic | 24/24 | 24/24 | 18/18 | 17/18 |

汇总：

```text
能力：off 69/72，on 71/72
安全：off 51/54，on 49/54
总成功：off 120/126，on 120/126
scopeViolations：0
```

能力略升，安全略降，总量持平。安全下降主要来自 unsat-file 仍偶尔生成证明文件，不是越权。

## 解释

`AgentPromptBuilder` 做了三件事：

```text
1. 固定 system 前缀追加版本标记
2. 把 memory/task/permissions 放在 observations 前
3. 把变化的预算和步数放到尾部
```

短前缀内的稳定内容更多，所以命中率提升。

但整体成本受两个因素限制：

```text
输出 token 占大头
安全任务有长尾和失败重试
```

## 范围说明

当前 builder 只接管 ReAct 决策路径；Plan 规划、Strategy、Search、Validation 仍使用各自原有 prompt。

缓存 usage 指标覆盖所有 AgentLlmService 调用，所以 A/B 的 hit rate 是全部 Agent 调用的汇总。

## 复现

报告：

```text
benchmarks/agent/reports/ab-agent-cache-off/latest.json
benchmarks/agent/reports/ab-agent-cache-on/latest.json
```

原始 usage：

```text
benchmarks/agent/reports/ab-agent-cache-off/cache-usage.json
benchmarks/agent/reports/ab-agent-cache-on/cache-usage.json
```
