# 狼人杀引擎与人机协作：等待、发言、投票和自动托管

> 这是「月白小筑 AI 系统工程笔记」狼人杀系列第 1 篇。  
> 重点：先让规则跑通，再让人类和 AI 坐在同一张桌子上。

## 一、先有规则，不是先有 AI

狼人杀最容易写错的地方，是让模型同时负责：

```text
规则
身份
夜间行动
投票
胜负
```

正确顺序是：

```text
先写一个不依赖任何模型的规则引擎
再把 AI 接到每个座位上
```

引擎负责：

```text
角色分配
夜间顺序
公开广播
私有信息
投票统计
放逐与死亡
胜负判断
```

AI 只负责：

```text
当前座位看到什么
当前座位想做什么
当前座位说什么
```

---

## 二、角色和阶段

角色：

```text
狼人
村民
预言家
女巫
猎人
```

阶段：

```text
NIGHT_WOLF
NIGHT_WITCH_SAVE
NIGHT_WITCH_POISON
NIGHT_SEER
DAWN
DISCUSSION
VOTE
HUNTER_SHOOT
GAME_OVER
```

每个阶段只允许特定动作。

---

## 三、等待状态：人机协作的核心

AI 可以自己连续行动，但人类需要暂停。

引擎用 `Waiting` 表示：

```text
WOLF_KILL
WITCH_SAVE
WITCH_POISON
SEER_CHECK
VOTE
HUNTER_SHOOT
```

等待对象包含：

```text
type
title
prompt
options
```

前端拿到等待状态后展示按钮：

```text
选目标
使用解药 / 保留
使用毒药 / 不用
查验
投票
开枪
```

人类提交：

```http
POST /api/werewolf/games/{id}/action
```

```json
{"type": "VOTE", "targetId": "p3", "text": null}
```

引擎会校验：

```text
是否当前阶段
是否有效目标
是否存活
是否允许该动作
```

校验通过才推进状态。

---

## 四、发言：人类插队

讨论阶段不是一次性让所有 AI 说完。

引擎维护发言队列：

```text
按座位顺序
→ 下一位
→ 如果是人类，暂停
→ 前端收到 speaking_turn
→ 人类提交 SPEAK
→ 继续下一位
```

SSE 事件：

```text
speech_start
token
speech_end
speaking_turn
state
```

人类提交发言：

```json
{"action": "SPEAK", "text": "我觉得 p3 的票型有问题"}
```

引擎把发言写入公开广播，继续讨论。

---

## 五、AI 发言流

AI 发言走同一个流：

```text
speech_start
→ 模型 token
→ speech_end
```

如果模型失败：

```text
fallbackSpeech
→ 本地台词
→ 按 2 字符 / 18ms 模拟流式
```

这样前端不需要区分 AI 和 fallback，用户体验一致。

---

## 六、AI 决策

不同角色调用不同方法：

```text
chooseWolfTarget
chooseSeerCheck
chooseWitchSave
chooseWitchPoison
chooseVotes
chooseHunterShot
```

投票是并发的：

```text
每个 AI 一个 future
→ 45 秒轮询
→ 收集票
→ 缺失的用规则补票
```

这样即使某个 AI 失败，投票也能完成。

---

## 七、自动托管

如果用户不想操作，可以走：

```text
AUTO
```

`streamAuto` 会循环：

```text
讨论
→ 自动发言
→ 投票
→ 夜间
→ 直到游戏结束
```

人类座位由 `autoResolveWaiting` 自动选择。

但自动模式仍保留：

```text
阶段日志
发言流
投票记录
```

不是静默跑完。

---

## 八、引擎如何保证不卡死

### 8.1 阶段推进有 guard

`advance()` 和 `autoPlay()` 都有最大步数保护。

### 8.2 等待超时

AI 决策有 35～45 秒超时。

投票 future 超时后，剩余玩家补规则票。

### 8.3 异常不冻结整局

模型调用失败会：

```text
记录日志
→ 规则降级
→ 游戏继续
```

### 8.4 SSE 客户端断开

发送失败会吞掉异常，游戏状态仍然有效。

---

## 九、人机协作的时序

```text
前端发起 stream
→ 引擎 advance
→ 如果轮到人类：
     返回 waiting / speaking_turn
     前端展示操作
     人类 action / speak
     继续 stream
→ 如果 AI：
     模型决策
     引擎记录
     推送 state
```

核心不是“人类和 AI 同时说”，而是：

```text
在同一个状态机上，按阶段交替推进
```

---

## 十、建设顺序

```text
1. 先写无 AI 的规则引擎
2. 再做 AI 决策接口
3. 再做 waiting 和人类 action
4. 再做讨论队列和 SSE
5. 再做自动托管
6. 最后做多 Agent 隔离和提示词缓存
```

如果先做 AI，很容易把规则写进 prompt，最后既难测也难解释。

---

## 十一、结论

狼人杀的人机协作不是“让 AI 陪人聊天”，而是：

```text
共享同一套规则
人类在需要时接管
AI 在其余时间推进
所有动作都经过引擎校验
```

引擎是裁判，AI 是玩家。
