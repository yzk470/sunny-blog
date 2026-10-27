# 给月白 Agent 加上提示词缓存：命中率 55.9% → 70.9%，总成本下降 10%

> 数据截止：2026-10-27  
> 关键词：Agent、Prompt Cache、DeepSeek、上下文工程、成本

## 一、为什么突然做这件事

月白 Agent 之前的速度优化主要靠：

```text
任务并发
LPT 长任务优先
模型调用虚拟线程
Redis 异步指标
```

这些解决的是“同时跑更多任务”。

但 Agent 还有一个更隐蔽的成本：

```text
每一步 ReAct 都会重新发送 system prompt、工具目录、技能目录、
历史记忆、任务、observations、预算和输出格式。
```

如果这些内容每次顺序都变，模型侧的**前缀缓存**就很难命中。

所以这次做两件事：

```text
1. 用 AgentPromptBuilder 固定 ReAct prompt 的结构
2. 采集 DeepSeek 的 prompt_cache_hit_tokens / miss_tokens
```

然后跑完整 126 次 benchmark 做 A/B。

---

## 二、原来的问题

旧版 ReAct 的 user prompt 顺序是：

```text
历史记忆
已执行动作与观察
当前任务
执行预算
目录权限
执行成功提示
输出格式
```

问题在于：

```text
observations 每一步都在变
而它排在了当前任务和权限之前
```

于是从 observations 开始，后面全部无法复用。

system prompt 本身可以缓存，但 user prompt 只有很短的稳定前缀。

---

## 三、新的 Prompt 结构

`AgentPromptBuilder` 把 ReAct 改成：

```text
[AGENT_PROMPT_VERSION] agent-prompt-cache-v1

system：
固定的角色、工具、技能、输出约束

user：
## 历史记忆
## 当前任务
## 目录权限
## 已执行动作与观察
## 执行预算
## 输出格式
```

变化点被放到了尾部：

```text
预算 / 步数 / observations / 输出要求
```

稳定内容放到了前面：

```text
system / memory / task / permissions
```

这样每一步的新 prompt 都包含前一步的稳定前缀。

### 为什么没有把 run id 放最前面

狼人杀把 `[GAME_ID]` 放最前面，是为了让不同对局不共享缓存。

Agent 不一样：

```text
system prompt、工具目录、技能目录可以跨任务共享
```

如果把 run id 放在最前面：

```text
所有任务前缀都不同
跨任务缓存全部失效
```

所以 Agent 的策略是：

```text
固定 system 前缀
+ 稳定的 memory/task/permissions
+ 追加式 observations
+ 尾部动态预算
```

---

## 四、缓存 usage 怎么采

DeepSeek 的响应 usage 包含：

```text
prompt_tokens
completion_tokens
prompt_cache_hit_tokens
prompt_cache_miss_tokens
prompt_tokens_details.cached_tokens
```

Spring AI 的 `OpenAiUsage` 暴露了：

```java
getPromptTokensDetailsCachedTokens()
```

`AgentLlmService` 现在：

```text
call().chatResponse()
→ 读取 metadata.usage
→ 记录 prompt/cached/miss/completion
```

新增 `AgentCacheUsage`：

```text
calls
promptTokens
cachedTokens
missTokens
completionTokens
hitRate
```

管理接口：

```http
GET  /api/admin/agent/cache-usage
DELETE /api/admin/agent/cache-usage
GET  /api/admin/agent/cache-mode
POST /api/admin/agent/cache-mode
```

---

## 五、A/B 设置

```text
模型：deepseek-flash
benchmark：126 次
8 能力任务 + 6 安全任务
× 3 变体 × 3 重复
workers = 4
时间：2026-10-27 22:43 ～ 23:43
```

两组的区别：

```text
off：legacy prompt 顺序
on：AgentPromptBuilder 缓存友好顺序
```

---

## 六、结果

| 指标 | legacy off | cache-friendly on | 变化 |
|---|---:|---:|---:|
| 缓存命中率 | 55.88% | 70.90% | +15.01pp |
| wall time | 1927.5s | 1787.6s | -7.3% |
| LLM 调用 | 1315 | 1285 | -30 |
| prompt tokens | 3,133,860 | 3,227,909 | +3.0% |
| cached tokens | 1,751,296 | 2,288,512 | +537,216 |
| miss tokens | 1,382,564 | 939,397 | -443,167 |
| completion tokens | 1,021,706 | 992,785 | -2.8% |

缓存命中率：

```text
55.88% → 70.90%
提升 15.01 个百分点
相对提升约 26.9%
```

注意：

```text
prompt tokens 反而增加了 3%
```

因为新结构增加了版本头和分区标题。

但命中率提升的幅度更大，所以输入成本仍然下降。

---

## 七、按 DeepSeek-Flash 当前价格算

空闲时段官方价格：

```text
缓存命中输入：0.02 元 / M
缓存未命中输入：1.00 元 / M
输出：4.00 元 / M
```

| 成本 | legacy off | cache-friendly on | 变化 |
|---|---:|---:|---:|
| 输入成本 | ¥1.4176 | ¥0.9852 | **-30.5%** |
| 输出成本 | ¥4.0868 | ¥3.9711 | -2.8% |
| 总成本 | ¥5.5044 | ¥4.9563 | **-9.96%** |
| 每任务成本 | ¥0.0437 | ¥0.0393 | **-10.0%** |

计算方式：

```text
输入成本 = cached × 0.02 + miss × 1.00
输出成本 = completion × 4.00
```

所以：

```text
输入成本下降 30.5%
总成本下降 9.96%
```

总成本下降小于输入成本，因为：

```text
输出 token 仍然占主要成本
缓存只优化输入
```

高峰时段价格翻倍：

```text
命中输入 0.04
未命中输入 2.00
输出 8.00
```

按相同 token 结构，下降百分比不变，绝对金额翻倍。

---

## 八、成功率有没有变

| 变体 | off 能力 | on 能力 | off 安全 | on 安全 |
|---|---:|---:|---:|---:|
| linear | 22/24 | 23/24 | 16/18 | 16/18 |
| static | 23/24 | 24/24 | 17/18 | 16/18 |
| dynamic | 24/24 | 24/24 | 18/18 | 17/18 |

汇总：

```text
能力：69/72 → 71/72
安全：51/54 → 49/54
总成功：120/126 → 120/126
scopeViolations：0
```

能力略升，安全略降，总量持平。

安全下降仍然是 `unsat-file` 偶尔生成证明文件，不是越权。

---

## 九、这次提升的本质

不是“模型变聪明了”。

而是：

```text
把不会变的内容放在前面
把会变的内容放在后面
让模型服务端可以复用前缀
```

一句话：

```text
缓存命中率不是靠更长的 prompt，
而是靠更稳定的前缀。
```

---

## 十、当前限制

### 1. 只有 ReAct 用了新 builder

Plan、Strategy、Search、Validation 仍然使用各自原有 prompt。

### 2. 输出成本没有优化

缓存只影响输入。

### 3. A/B 只跑了一轮

单轮数据会受到任务波动、长尾、缓存初始状态影响。

### 4. 安全任务仍有失败

`unsat-file` 还没有做到零产物。

---

## 十一、下一步

```text
把 Plan / Strategy / Search / Validation 也统一到稳定前缀
继续降低输出 token
把缓存命中率做成执行面板的长期指标
再跑多轮 A/B 验证稳定性
```

---

## 十二、数据出处

- A/B 报告：`benchmarks/agent/reports/agent-cache-ab.md`
- [DeepSeek 官方价格](https://api-docs.deepseek.com/zh-cn/quick_start/pricing/)

这次最重要的数字不是 7.3% 的 wall time 下降，而是：

```text
同样 126 次任务
缓存命中率 +15.01pp
输入成本 -30.5%
总成本 -9.96%
```

这说明 prompt 结构本身，已经成为一个可以单独优化的工程变量。
