# Agent 当前问题与解决方案

> 更新时间：2026-10-24  
> 文档状态：执行链路问题已修复；性能优化第一版已实施并完成小规模验证

## 〇、性能优化第一版

已经实现：

```text
Benchmark runner 并发
workers 默认 6
LPT 长任务优先
cap-skill-evolve 串行锁
AgentLlmService 专用虚拟线程执行器
模型调用失败重试 1 次
Redis 异步指标队列
Redis 队列批量落库
```

小规模实测：

| 任务数 | 串行总耗时 | 6 并发 wall time | 加速比 |
|---|---:|---:|---:|
| 4 | 507.3s | 363.2s | 1.4x |
| 8 | 783.8s | 368.3s | 2.1x |

两次测试里都有一个 `cap-text-report` 长任务接近 360 秒，成为尾部瓶颈，因此加速比没有达到理论上限。

Redis 指标队列验证：

```text
LLEN agent:metrics:queue = 0
任务指标已通过队列落入 MySQL
```

完整 126 次并发复测尚未执行。下一步需要先确定：

```text
workers = 4 还是 6
长文本报告是否放宽 timeout
模型 API 是否真的支持 6 并发
```

## 一、当前结论

### 1. 后端已启动

实测：

```text
GET https://www.bluish-white.top/api/agent/status  -> 200
GET https://www.bluish-white.top/api/site          -> 200
POST /api/admin/auth/login                       -> 200
GET /api/admin/agent/runs?limit=30               -> 200
```

### 2. 执行链路页面问题已修复

原来页面报：

```text
无法连接服务器
```

不是后端没有启动，而是 `/api/admin/agent/runs` 返回了过大的响应：

```text
旧响应大小：11,250,621 bytes（约 11 MB）
旧响应耗时：76.83 秒
```

前端/反向代理在等待期间超时，所以看起来像“无法连接服务器”。

当前已经改成轻量接口：

```text
GET /api/admin/agent/runs?limit=30
响应：约 17 KB
耗时：约 0.52 秒

GET /api/admin/agent/runs/{id}
响应：约 5.7 KB
耗时：约 0.38 秒
```

### 3. Benchmark 速度问题仍然存在

完整 126 次运行的实测：

```text
实际总耗时：8218.6 秒 = 2 小时 16 分 59 秒
所有任务耗时之和：8201 秒
串行度：8201 / 8219 ≈ 0.998
```

说明任务基本是**一个接一个执行**，没有并发。

---

## 二、执行链路问题：根因与修复

### 2.1 根因

旧接口：

```java
@GetMapping("/runs")
public ApiResponse<List<AgentRunService.AgentRun>> runs() {
    return ApiResponse.ok(runs.list());
}
```

返回的是完整 `AgentRun`：

```text
steps
events
checkpoint
attempts
tool records
```

单条记录可能达到数 MB。一次返回 200 条时：

```text
11 MB
76 秒
```

浏览器/网关等待超时，于是报连接失败。

### 2.2 修复

列表接口只返回摘要：

```text
id
sessionId
clientTaskId
mode
question
status
validation
createdAt
updatedAt
eventSeq
stepCount
```

不再返回：

```text
events
checkpoint
完整 attempts
```

详情接口只返回执行链路所需字段：

```text
steps
answer
validation
基础元数据
```

### 2.3 当前效果

```text
旧列表：11 MB / 76.83s
新列表：17 KB / 0.52s

旧详情：数 MB / 慢
新详情：5.7 KB / 0.38s
```

执行链路页面现在可以正常打开。

---

## 三、Benchmark 当前性能问题

### 3.1 任务串行

当前 benchmark runner 执行方式：

```text
variant
→ task
→ repeat
→ 等待完成
→ 下一个
```

所有 126 次运行基本串行。

### 3.2 模型等待占主导

单任务平均：

```text
约 65 秒
```

P95：

```text
约 180 秒
```

大部分时间在等待：

```text
模型 API 返回
工具执行
产物校验
```

CPU 在大部分时间是空闲的。

### 3.3 模型调用公共池瓶颈

`AgentLlmService.complete` 当前使用：

```java
CompletableFuture.supplyAsync(...)
    .get(timeout)
```

默认使用 ForkJoin 公共池，并发度受 CPU 核数影响。

结果：

```text
表面并发多个任务
实际模型调用可能被公共池排队
```

### 3.4 高价值搜索分支串行

当前搜索是：

```text
分支 A 执行
→ 失败
→ 分支 B
→ 失败
→ 分支 C
```

不是并行探索，也不是第一个成功即取消其他分支。

### 3.5 并发写入冲突

`cap-skill-evolve` 会写：

```text
skills/benchmark-helper
```

多个 repeat 并发时会冲突，需要：

```text
独立 skill namespace
或该任务串行
```

---

## 四、性能优化方案

## P0：Benchmark 任务并发

目标：

```text
126 个任务进入队列
N 个 worker 并发执行
```

推荐：

```text
workers = 6
max_in_flight = 12
per_task_timeout = 360s
```

调度：

```text
LPT：先提交 PPT、DOCX、长报告等慢任务
再用快任务填充尾部
```

预计：

```text
2h17m → 35～50 分钟
```

## P0：修复模型调用公共池

改为：

```text
专用虚拟线程执行器
```

或者因为外层已经是虚拟线程，直接阻塞调用。

同时配置：

```text
HTTP 连接池
keep-alive
连接超时
读超时
最大并发连接
```

## P1：限流与退避

并发提高后必须处理：

```text
429
rate limit
timeout
连接失败
```

需要：

```text
全局 token bucket
provider 并发上限
瞬时错误退避重试
永久错误不重试
```

## P1：数据库写入优化

当前每完成一个任务就写：

```text
agent_task_metric
agent_attempt_metric
agent_metric_daily
```

并发后建议：

```text
批量写入
或异步队列
```

## P2：单任务内部并行

高价值任务可以：

```text
并行生成多条策略
并行执行 2～3 个分支
第一个通过验收的分支胜出
取消其他分支
```

必须保证：

```text
attempt artifacts 隔离
预算隔离
取消机制
技能写入隔离
```

## P2：减少 LLM 调用

```text
简单任务单路线
不生成多策略
不评分
不反思
工具/技能目录缓存
产物格式校验缓存
模型路由
```

---

## 五、预期收益

按 6 并发：

```text
理论下限：约 23 分钟
实际目标：35～50 分钟
```

按 8 并发：

```text
理论下限：约 17 分钟
实际目标：25～40 分钟
```

如果模型 API 允许足够并发，且公共池修复完成：

```text
3～5 倍提速是合理目标
```

注意：

```text
CPU 不是主要瓶颈
真正的瓶颈是串行等待模型 I/O
```

---

## 六、当前状态

### 已完成

```text
执行链路列表轻量化
执行链路详情轻量化
Benchmark runner 并发 workers=6
LPT 长任务优先调度
cap-skill-evolve 串行锁
AgentLlmService 专用虚拟线程执行器
模型调用失败重试
Redis 异步指标队列
Redis 队列批量落库
UNSAT 明确拒绝
```

### 未完成

```text
完整 126 次并发复测
模型 API 实际并发上限探测
429 全局 token bucket
高价值任务并行分支
长文本报告 timeout 调优
workers=4/6 稳定性对比
```

### 当前验证数据

```text
能力任务：13
能力成功：13
能力成功率：100%

安全任务：9
安全通过：9
安全通过率：100%

越权次数：0
```

---

## 七、建议下一步

建议按顺序：

```text
1. benchmark runner 并发 workers=6
2. AgentLlmService 专用虚拟线程执行器
3. 增加 429 退避和连接池
4. 小规模 A/B：12 个任务
5. 观察 429、P95、失败率
6. 再跑完整 126 次
7. 最后做高价值任务并行分支
```

不要一开始直接开 20 并发，否则很容易出现：

```text
模型限流
Office 进程争 CPU
沙箱资源竞争
数据库写入阻塞
```

---

## 八、部署状态

```text
后端：已启动
health：200
执行链路列表：正常
执行链路详情：正常
```

页面：

```text
/admin/agent-runs
/admin/agent-metrics
/admin/agent-benchmark
```
