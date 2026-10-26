# 月白 Agent 性能复盘：126 次评测从 2 小时 17 分到 40 分 9 秒

> 数据截止：2026-10-26  
> 关键词：Agent、Benchmark、并发、Redis、执行链路

月白 Agent 不是一次模型调用，而是一个完整的执行系统：

```text
用户任务
→ ReAct / Plan
→ 工具调用
→ 结果观察
→ 反思、重试、搜索分支
→ 产物验收
→ 最终回答
```

这篇文章只聊两件事：

1. 这套 Agent 是怎么设计和验收的。
2. 为什么完整 126 次 benchmark 能从 2 小时 17 分降到 40 分 9 秒。

---

## 一、Agent 的执行链路

### 1.1 两种策略

短任务走 ReAct：

```text
思考
→ 调工具
→ 看结果
→ 继续思考
```

长任务走 Plan：

```text
拆计划
→ 逐步执行
→ 每一步重新观察
→ 最后汇总
```

高价值任务还会启用简化版 best-first LATS：

```text
生成多个分支
→ 执行
→ 评分
→ 剪枝
→ 修复或重开分支
```

### 1.2 产物必须能验收

Agent 的产物不是“模型说完成了”就算完成，而是：

```text
文件真的存在
格式真的正确
页数/字数符合要求
脚本真的能运行
```

每个 attempt 的产物放在独立目录：

```text
artifacts/<runId>/attempts/<attemptId>/
```

这样可以追溯每一次尝试，而不是只保留最后一次。

### 1.3 UNSAT 明确拒绝

如果任务要求当前环境不存在的工具，或者要求读取不存在的文件，Agent 不应该编造结果。

现在的处理是：

```text
预检发现工具不存在
→ 任务状态 BLOCKED
→ 清理本次产物
→ 明确拒绝
```

文件工具只能写：

```text
workspace
artifacts
skills
```

其他路径直接返回 `POLICY_DENIED`。

脚本运行在独立沙箱中，使用只读的 `/tmp`、独立 HOME 和受限用户组。

---

## 二、Benchmark 怎么设计

评测分成两组，不混算成功率。

### 能力测试

```text
8 个能力任务
× 3 种策略（linear / static / dynamic）
× 3 次重复
= 72 次
```

### 安全测试

```text
6 个安全任务
× 3 种策略
× 3 次重复
= 54 次
```

合计：

```text
126 次
```

指标写入 MySQL：

```text
agent_task_metric
agent_attempt_metric
agent_metric_daily
```

分组字段：

```text
test_group
benchmark_pass
scope_violation
```

能力看：

```text
是否完成
是否一次成功
产物是否正确
```

安全看：

```text
是否拒绝
是否越权
是否产生不该产生的文件
```

---

## 三、优化前后的数据

### 3.1 总耗时

旧版串行跑完整个套件：

```text
8218.6 秒
= 2 小时 16 分 59 秒
```

优化后：

```text
workers = 4
2409.0 秒
= 40 分 9 秒
```

提升：

```text
8218.6 / 2409.0 ≈ 3.4 倍
```

所有任务耗时之和：

```text
7006.4 秒
```

实际并发效率：

```text
7006.4 / 2409.0 ≈ 2.9
```

为什么不是 4 倍？

因为有长尾任务。PPT、DOCX、长报告明显慢于脚本和 HTML，最后一个长任务决定 wall time。

### 3.2 A/B

12 个任务、1 次重复、dynamic 变体：

| workers | wall time | 成功 |
|---:|---:|---:|
| 4 | 207.9 秒 | 12 / 12 |
| 6 | 207.4 秒 | 11 / 12 |

6 并发没有更快，却多了一次 `unsat-file` 安全失败。

最终选择：

```text
workers = 4
```

---

## 四、速度来自哪里

### 4.1 任务并发

benchmark runner 从串行改成线程池：

```text
workers
LPT 长任务优先
cap-skill-evolve 串行锁
```

LPT 先把 PPT、DOCX、长报告等慢任务放进去，避免最后只剩一个长任务。

`cap-skill-evolve` 会写同一个 skill 目录，所以单独串行。

### 4.2 模型调用不再挤公共池

原来：

```java
CompletableFuture.supplyAsync(...)
```

默认使用 ForkJoin 公共池，并发度受 CPU 核数影响。

现在模型调用使用专用虚拟线程执行器：

```text
模型调用是 I/O 等待
不应该被 CPU 公共池限制
```

同时保留了一次失败重试。

### 4.3 Redis 异步指标队列

任务完成后，不再同步写三张指标表，而是：

```text
任务完成
→ 写入 Redis list
→ 定时批量取出
→ saveAll 到 MySQL
```

验证结果：

```text
LLEN agent:metrics:queue = 0
能力任务：72
安全任务：54
```

Redis 不可用时自动回退同步写库。

### 4.4 执行链路接口轻量化

原来执行链路列表接口返回完整运行记录：

```text
11.25 MB
76.83 秒
```

浏览器或网关超时，看起来像“无法连接服务器”。

改成摘要和详情分离后：

```text
列表：约 17 KB / 0.51 秒
详情：约 5.7 KB / 0.38 秒
```

---

## 五、最终结果

| 变体 | 能力成功 | 安全通过 | 越权 |
|---|---:|---:|---:|
| linear | 22 / 24 | 17 / 18 | 0 |
| static | 22 / 24 | 15 / 18 | 0 |
| dynamic | 24 / 24 | 16 / 18 | 0 |

汇总：

```text
能力：68 / 72 = 94.4%
安全：48 / 54 = 88.9%
scopeViolations：0
```

剩余问题：

```text
unsat-file 仍偶尔生成证明文件
部分长报告在 240 秒阈值上超时
```

`unsat-tool` 已经做到 9/9 正确 BLOCKED。

---

## 六、下一步

```text
继续降低 benchmark 长尾
补 unsat-file 零产物
把能力、安全统计做成长期趋势
增加缓存命中率和队列等待指标
```

从 2 小时 17 分到 40 分 9 秒，最重要的不是“开了几个线程”，而是：

```text
并发
+ 长任务优先
+ 异步指标
+ 失败可降级
+ 产物可验收
```

这些一起，才让速度提升变得可复现。

---

*相关实现：`AgentOrchestrator`、`AgentReActService`、`AgentPlanExecuteService`、`AgentMetricsAsyncQueue`、`run_benchmark.py`。*
