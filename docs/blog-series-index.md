# 月白小筑 AI 系统工程笔记：系列总览

> 这不是一篇“功能介绍”，而是一组开发记录。  
> 记录每个能力先做什么、遇到什么、怎么改、最后怎么验证。

月白小筑的三套 AI 系统是并行长出来的：

```text
月白 Agent
数字人
狼人杀
```

它们共享一些底层问题：

```text
上下文怎么拼
记忆怎么存
工具怎么调
多 Agent 怎么隔离
失败怎么降级
结果怎么验收
性能怎么量化
```

这套系列文章按“从能力到优化”的顺序展开。

---

## 系列结构

### 0. 总览

当前这篇：三套系统的开发顺序和文章地图。

### 1. 月白 Agent

1. [从一次问答到可验收执行：Agent 的执行循环、工具和产物](https://www.bluish-white.top/posts/agent-from-answer-to-artifact)
2. [Agent 记忆、预检与任务前检索：让模型在动手前知道得更多](https://www.bluish-white.top/posts/agent-memory-preflight-retrieval)
3. [SubAgent 独立验收：让另一个模型判断“真的完成了吗”](https://www.bluish-white.top/posts/agent-subagent-validation)
4. [月白 Agent 性能复盘：126 次评测从 2 小时 17 分到 40 分 9 秒](https://www.bluish-white.top/posts/moonlight-agent-performance-review)

### 2. 狼人杀

1. [狼人杀引擎与人机协作：等待、发言、投票和自动托管](https://www.bluish-white.top/posts/werewolf-engine-human-ai)
2. [狼人杀多 Agent 记忆隔离：谁应该知道什么](https://www.bluish-white.top/posts/werewolf-memory-isolation)
3. [狼人杀提示词缓存：从 46% 到 83% 的可缓存前缀](https://www.bluish-white.top/posts/werewolf-prompt-cache-46-to-83)

### 3. 数字人

1. [数字人人设工程：从公开资料到 persona v2](https://www.bluish-white.top/posts/digital-human-persona-engineering)
2. [数字人对话流水线：读博客、查工具、记住上下文](https://www.bluish-white.top/posts/digital-human-chat-pipeline)
3. [数字人的声音与动作：两条流怎么同时跑起来](https://www.bluish-white.top/posts/digital-human-voice-motion-sync)

---

## 三条开发主线

### 月白 Agent

```text
先能回答
→ 再能调工具
→ 再能产出文件并验收
→ 再能失败重试、暂停、恢复
→ 再能隔离 run / attempt
→ 再做 benchmark
→ 最后做并发和缓存
```

### 狼人杀

```text
先有可玩的规则引擎
→ 再接 AI 决策
→ 再做人类操作和 SSE
→ 再隔离每个座位的信息
→ 再做提示词缓存
→ 最后补重试和降级
```

### 数字人

```text
先有 3D 模型和 TTS
→ 再有人设
→ 再接 Spring AI 对话
→ 再做博客文章工具
→ 再做流式语音
→ 最后做动作 / 表情 / 口型
```

---

## 一条共同的原则

三个项目最后都收敛到同一句话：

```text
模型负责判断，工程负责边界。
```

边界包括：

```text
哪些工具能调
哪些文件能写
哪些记忆能给
哪些 Agent 能看到什么
失败之后谁来兜底
```

接下来的文章，按上面的顺序展开。
