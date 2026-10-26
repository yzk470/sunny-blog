# Agent 记忆、预检与任务前检索：让模型在动手前知道得更多

> 这是「月白小筑 AI 系统工程笔记」Agent 系列第 2 篇。  
> 重点：记忆不是把所有历史塞进去，预检也不是“多问一次模型”。

## 一、为什么记忆不能简单追加

最早的记忆就是：

```text
把最近对话全部拼进 prompt
```

问题：

```text
越聊越长
旧信息被新信息淹没
重要偏好和普通闲聊权重一样
prompt 超出预算后只能硬截断
```

所以记忆系统要解决三件事：

```text
存什么
怎么分层
拿多少进 prompt
```

---

## 二、Agent 记忆的存储

`AgentMemoryService` 使用会话级 JSON 文件：

```text
data/agent/sessions/<sessionId>.json
```

一条记忆：

```json
{
  "id": "uuid",
  "role": "user",
  "content": "以后写文章先给结论",
  "importance": "HIGH",
  "kind": "question",
  "createdAt": "..."
}
```

字段含义：

```text
role：谁说的
content：内容
importance：重要程度
kind：问题 / 回答 / 错误 / blocked
createdAt：时间
```

一个 session 最多保留 240 条，超过后删除最旧。

---

## 三、重要度怎么判断

当前使用规则分类：

```text
HIGH：
记住 / 重要 / 目标 / 计划 / 偏好 / 以后 / 必须 /
长期 / 不要再 / 我的名字 / 我是

MEDIUM：
帮我 / 请 / 如何 / 为什么 / 实现 /
写一个 / 分析 / 任务

其余：LOW
```

这不是语义模型，而是确定性规则。

优点：

```text
快
可解释
不会因为模型抖动改变记忆层级
```

缺点：

```text
反讽、隐含偏好、上下文中的“重要”可能误判
```

所以它只是第一层筛选，不是最终理解。

---

## 四、U 形 prompt 组装

`buildPromptContext(sessionId, 7000)` 会按重要度分块：

```text
高优先级长期记忆
中优先级记忆（最近 12 条）
近期低优先级对话（最近 10 条）
```

预算：

```text
HIGH：1/3
MEDIUM：1/3
LOW：1/4
```

为什么叫 U 形：

```text
开头放长期重要信息
中间放中优先级
结尾放最近对话
```

这样模型既能看到稳定偏好，也能看到当前上下文。

如果总长度仍然超出，保留尾部最新内容。

---

## 五、记忆生命周期

### 写入

```text
用户提问前
→ append user question
→ classify importance
```

### 读取

```text
每次执行前
→ buildPromptContext
→ 注入 ReAct / Plan 的 user prompt
```

### 成功

```text
最终回答
→ append assistant answer
→ importance = MEDIUM
```

### 失败

```text
预检失败 / 执行失败
→ append error
→ 让下一次知道哪里失败过
```

### 隔离

```text
session 级锁
→ 同一 session 不并发写
run / attempt 通过 TaskContext 隔离
```

---

## 六、预检：不花模型预算的检查

`AgentPreflightService` 在每次 attempt 前做：

```text
workspace 是否可写
artifacts 是否可写
工具目录是否为空
任务是否需要执行
run_script 是否在工具目录
write_file 是否存在
```

结果：

```text
ok
blockers
warnings
summary
```

失败时：

```text
不消耗执行预算
直接 FAILED
记录预检摘要
```

这不是“提前检索”，而是“提前排除环境错误”。

---

## 七、任务前检索：现在到底做了什么

严格说，当前不是把所有网页资料自动检索进 prompt。

执行前实际发生的是：

```text
1. 读取历史记忆
2. 做环境预检
3. 读取工具目录 / 技能目录
4. 生成候选策略
5. 高价值任务才启用搜索策略
```

### 7.1 工具目录

`AgentToolExecutor.catalog()` 会把可用工具和参数说明注入 prompt。

模型不会调用不存在的工具。

### 7.2 技能目录

`AgentSkillsService` 提供技能列表，模型按需 `read_skill`。

### 7.3 策略检索

`AgentStrategyService` 会生成多条候选路线，并按成功概率排序。

这不是资料检索，而是“解法检索”。

### 7.4 知识检索

真正的知识检索是工具：

```text
search_knowledge
→ 向量粗检索
→ 独立 rerank Agent 过滤
→ 返回相关片段
```

它是按需调用，不是每轮自动执行。

原因：

```text
自动检索会增加延迟和噪声
很多任务根本不需要知识库
把检索做成工具，模型可以自己决定何时用
```

### 7.5 高价值任务

`AgentSearchPolicy` 只对以下任务启用更贵的搜索：

```text
架构、审计、完整方案、端到端、复杂、
生产环境、迁移、安全评估、性能优化、
系统设计、调研报告、多步骤、多个文件、全流程
```

高价值任务：

```text
更大预算
更多策略
更多分支
```

普通任务：

```text
线性尝试，避免过度设计
```

---

## 八、一个真实的执行顺序

```text
用户问题
→ 读取 memory context
→ 写入当前问题
→ 预检
→ 策略规划
→ 搜索策略决策
→ ReAct / Plan
→ 工具调用
→ 产物检查
→ 独立验收
→ 写入回答
```

记忆和预检都在模型真正开始调工具之前。

---

## 九、建设顺序

```text
1. 先做会话历史
2. 再做 importance 分层和 prompt 预算
3. 再做预检
4. 再做策略组合
5. 再做按需知识检索和 rerank
6. 最后做高价值搜索策略
```

如果顺序反过来，很容易出现：

```text
记忆太长
工具不存在
检索污染
预算失控
```

---

## 十、结论

记忆系统不是“上下文越多越好”，而是：

```text
重要的放前面
最近的放后面
不重要的少放
不存在的工具不要放
```

任务前检索也不是“先搜一堆资料”，而是：

```text
先知道环境能不能做
再知道有哪些工具和技能
再决定要不要查知识库
```

先把这些边界做对，Agent 才不会“知道很多，但做不对”。
