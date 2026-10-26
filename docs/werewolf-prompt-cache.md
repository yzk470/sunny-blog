# 狼人杀提示词缓存结构

## 一、目标

把每个 AI 座位的提示词改成固定结构：

```text
系统提示词
+ 公共广播
+ 角色提示词
```

同时做到：

```text
同一局内前缀稳定，提高缓存命中
不同局开头不同，避免跨局复用同一段缓存
公共广播和私有事件都按发生顺序追加
```

## 二、最终拼接格式

```text
[GAME_ID] <gameId>
[PROMPT_VERSION] werewolf-prompt-v2

【系统提示】
固定的规则、隐私边界、发言要求

【公共广播（按发生顺序追加）】
[seq][R轮次][阶段] 公开事件 1
[seq][R轮次][阶段] 公开事件 2
...

【角色提示】
角色基础提示（狼人/村民/预言家/女巫/猎人）
【当前轮次】
【你的私有信息】
【你的私有事件（按发生顺序）】
【当前存活玩家】
【当前任务】
【输出规则】
```

用户消息固定为：

```text
请执行【当前任务】。
```

这样变化只发生在角色提示尾部。

## 三、为什么游戏 ID 放最前面

DeepSeek 等模型的前缀缓存要求从第一个 token 开始一致。

```text
不同局：[GAME_ID] 不同
→ 不会错误复用上一局的推理缓存
→ 每局对话更容易产生不同走向
```

同一局：

```text
[GAME_ID] 相同
【系统提示】相同
【公共广播】只追加
→ 系统 + 早期广播成为可命中前缀
```

## 四、轮次为什么放在角色尾部

如果把轮次放在最前面：

```text
[GAME_ID] xxx
[ROUND] 1  ← 第二轮变成 2
```

每轮开头都会变化，整局几乎无法命中缓存。

因此这里把：

```text
当前轮次
```

放在【角色提示】的尾部；轮次变化只影响尾部，不影响系统提示 + 公共广播前缀。

如果更看重“每轮强制换一种思路”，可以再把轮次移到最前面，但会牺牲跨轮缓存。

## 五、角色提示词

`WerewolfPromptBuilder` 为五种身份分别提供固定提示：

```text
WEREWOLF  狼人
VILLAGER  村民
SEER      预言家
WITCH     女巫
HUNTER    猎人
```

每种身份只保留一份基础提示，具体行动要求放进【当前任务】尾部。

这样避免同一身份因为夜间技能不同而产生多套完全不同的系统前缀。

## 六、广播不裁剪

`WerewolfGame.log()` 不再删除旧日志。

原因：

```text
裁剪最早的广播
→ 公共广播前缀变化
→ 缓存全部失效
```

一局游戏有行动上限，日志量有限，保留完整广播换来稳定前缀是值得的。

## 七、代码位置

- `backend/src/main/java/com/sunnyblog/werewolf/WerewolfPromptBuilder.java`
- `backend/src/main/java/com/sunnyblog/werewolf/WerewolfGame.java`
  - `publicBroadcast()`
  - `privateTimeline(Player)`
- `backend/src/main/java/com/sunnyblog/werewolf/WerewolfAiService.java`
  - 所有决策和发言统一使用 `WerewolfPromptBuilder.build(...)`

## 八、测试

```text
WerewolfPromptBuilderTest
- 游戏 ID 在开头
- 公共广播在角色提示之前
- 角色提示在当前任务之前
- 任务变化只影响尾部
- 不同游戏前缀不同
- 五种身份都有角色提示
```

后端测试：

```text
17 个测试全部通过
```
