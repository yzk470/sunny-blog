# 狼人杀多 Agent 记忆隔离：谁应该知道什么

> 这是「月白小筑 AI 系统工程笔记」狼人杀系列第 2 篇。  
> 关键词：多 Agent、上下文隔离、私有信息、公开广播

## 一、狼人杀的记忆不是“一个记忆”

一局狼人杀里同时存在四种信息：

```text
公开信息：所有人都能看到
阵营信息：狼人知道狼队友
角色信息：预言家知道查验，女巫知道药和刀口
个人事件：谁在夜里做了什么
```

如果把这些都塞进一个共享上下文，游戏会立刻失去意义：

```text
村民知道了狼队友
好人知道了女巫的刀口
AI 看到了不属于自己的查验
```

所以这里的第一原则是：

```text
每个 Agent 只能看到自己的视角
```

---

## 二、游戏级隔离

每局游戏是一个 `WerewolfGame` 实例。

`WerewolfService` 用：

```text
Map<String, Session>
```

保存对局。

一局游戏的状态只存在于自己的实例里：

```text
玩家
身份
轮次
阶段
公开日志
私有日志
等待状态
```

没有跨局记忆。

原因：

```text
狼人杀是单局游戏
上一局的查验、票型、身份不能带到下一局
```

---

## 三、公开广播

公开事件包括：

```text
欢迎
天黑
狼人行动结果
天亮
死亡
发言
投票
放逐
胜负
```

`publicBroadcast()` 只过滤：

```text
privateTo == null
```

每条带：

```text
seq
round
phase
text
```

这是所有 Agent 共享的前缀。

---

## 四、私有信息

`privateKnowledge(viewer)` 按角色返回不同内容。

### 狼人

```text
你的身份：狼人
阵营：狼人阵营
你的狼队友：p2、p5
```

### 预言家

```text
你的身份：预言家
你的查验记录：
- p3：狼人
- p4：好人
```

### 女巫

```text
你的药剂：解药可用，毒药已用
今晚被狼人袭击的是：p6
```

### 猎人

```text
你的猎枪：可以开枪
```

### 村民

```text
你的身份：村民
阵营：好人阵营
```

注意：

```text
privateKnowledge 不是“所有历史”
而是“当前身份允许知道的摘要”
```

---

## 五、私有时间线

摘要不够，有时需要按发生顺序看私有事件。

`privateTimeline(viewer)` 返回：

```text
[seq][R轮次][阶段] 仅你可见的事件
```

例如：

```text
[12][R1][夜间] 查验结果：p3 是狼人。
[18][R1][夜间] 你使用解药救下了 p6。
```

它和公开广播分开：

```text
公开广播：共享前缀
私有时间线：角色提示尾部
```

---

## 六、Prompt 里的隔离

`WerewolfPromptBuilder` 为每个座位构造：

```text
[GAME_ID]

【系统提示】

【公共广播】

【角色提示】
  身份基础提示
  当前轮次
  私有信息
  私有时间线
  当前存活玩家
  当前任务
```

其中：

```text
公共广播：所有座位相同
角色提示：由 actor 决定
```

因此：

```text
p1 的狼人 prompt
p3 的预言家 prompt
p5 的村民 prompt
```

虽然共享前半段，但私有部分完全不同。

---

## 七、不同 Agent 的调用隔离

### 7.1 决策

每个角色调用自己的方法：

```text
chooseWolfTarget(game)
chooseSeerCheck(game, seer)
chooseWitchSave(game, witch, victim)
chooseWitchPoison(game, witch)
chooseVotes(game, voters)
chooseHunterShot(game, hunter)
```

方法入参已经限定了角色。

### 7.2 投票

每个 voter 一个独立任务：

```text
chooseVoteFor(game, voter)
```

不会把所有人的私有信息拼成一个上下文。

### 7.3 发言

`streamSpeech(game, speaker, ...)` 只给 speaker 的视角。

### 7.4 人类界面

`toView()`：

```text
public logs 全部可见
privateTo == humanId 的私有日志可见
其他私有日志不可见
```

人类玩家也不会看到 AI 的私有信息。

---

## 八、记忆生命周期

狼人杀没有持久记忆：

```text
开局 → 创建 game
局中 → logs / private knowledge 增长
结束 → 游戏对象保留一段时间
删除 → 对局消失
```

没有“上一局狼人经验”写进下一局。

这是有意的：

```text
多局之间共享记忆会破坏公平
也会让提示词缓存跨局污染
```

---

## 九、与 Agent 记忆的区别

| 系统 | 记忆范围 | 存储 |
|---|---|---|
| 月白 Agent | 跨任务会话 | 本地 JSON |
| 数字人 | 跨会话对话 | MySQL |
| 狼人杀 | 单局 | 内存 |

狼人杀只需要：

```text
本局公开广播
本局私有信息
```

不需要长期偏好，也不需要跨局画像。

---

## 十、建设顺序

```text
1. 先有公开日志和私有日志
2. 再按玩家过滤视图
3. 再按角色生成 privateKnowledge
4. 再做 privateTimeline
5. 最后才做提示词缓存
```

如果先做缓存，很容易把不该共享的私有信息拼进公共前缀。

---

## 十一、结论

多 Agent 的记忆隔离，不是“给每个 Agent 一个不同的 system prompt”这么简单。

真正要做的是：

```text
公开信息共享
私有信息按角色过滤
私有事件按时间追加
人类视图也只看到自己的部分
```

先保证“谁应该知道什么”，再谈“怎么让模型说得像”。
