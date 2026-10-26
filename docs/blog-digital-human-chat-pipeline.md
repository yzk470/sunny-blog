# 数字人对话流水线：读博客、查工具、记住上下文

> 这是「月白小筑 AI 系统工程笔记」数字人系列第 1 篇。  
> 重点：数字人不是“人设 + 模型”，而是一条会读文章、会查工具、会记忆的流水线。

## 一、一次对话经过什么

```text
前端创建 session
→ 读取角色
→ 读取最近历史
→ 拼系统提示词
→ 注入当前博客
→ 注入文章上下文
→ 调模型
→ SSE 流式输出
→ 动作 / 情绪
→ TTS
→ 保存消息
```

这条链路里，人设只是第一层。

---

## 二、会话

`DigitalHumanSessionService` 用 MySQL 保存：

```text
dh_session
dh_message
```

session 绑定：

```text
characterCode
status
createdAt
updatedAt
```

message 保存：

```text
role
content
emotion
audioUrl
createdAt
```

---

## 三、历史记忆

`history(sessionId, maxHistory)`：

```text
按时间倒序查最近 N 条
→ 反转成时间正序
```

默认最多：

```text
10 条
```

为什么不是全部：

```text
数字人聊天需要自然
历史太长会拖慢首字延迟
也会让角色被旧话题带偏
```

当前没有做摘要压缩。

所以记忆策略是：

```text
短期：最近 10 条
中期：当前文章上下文
长期：暂无独立长期记忆
```

---

## 四、系统提示词拼装

`DigitalHumanChatService.buildSystemPrompt`：

```text
角色 systemPrompt
+ 当前博客
+ 文章工具说明
+ 文章上下文
+ 最近对话
+ 保持角色口吻
```

关键点：

```text
角色提示词负责“是谁”
博客上下文负责“现在在聊什么”
历史负责“刚才说到哪”
```

三者不能混成一段没有边界的文本。

---

## 五、读博客文章

### 5.1 当前文章

如果页面带：

```text
articleSlug
```

`blogContext(articleSlug)` 会查：

```text
标题
摘要
正文（截断）
```

正文上限：

```text
5000 字符
```

### 5.2 最近文章

无论是否带 slug，都会附带最近 6 篇：

```text
标题 + 摘要
```

让数字人知道博客最近在写什么。

### 5.3 工具读取

如果用户说“讲讲那篇 Harness 的文章”，数字人可以调用：

```text
search_articles(keyword)
read_article(slugOrTitle)
```

`search_articles`：

```text
只查已发布文章
标题 / 摘要 / 正文 LIKE
最多 6 条
返回标题、slug、摘要
```

`read_article`：

```text
先按 slug 或标题精确查
再按标题模糊查
返回标题、slug、摘要、正文
正文最多 8000 字符
```

为什么需要工具：

```text
系统上下文只截断一部分正文
用户问到的文章可能不在当前上下文
工具可以按需读取完整文章
```

### 5.4 读取边界

只读已发布文章：

```text
status = PUBLISHED
```

草稿、后台文章不会进入数字人上下文。

---

## 六、文章上下文和工具的分工

```text
当前文章：
自动放进 system prompt，适合“这篇文章讲了什么”

文章工具：
用户主动提及时调用，适合“找一篇具体文章”
```

两者不是重复，而是：

```text
上下文负责默认视野
工具负责按需深挖
```

---

## 七、记忆的边界

当前数字人记忆有三个限制：

```text
只保留最近 10 条
不做长期摘要
不跨 session 共享
```

这意味着：

```text
换一个 session，之前的聊天不会自动带过去
长对话中很早的约定可能丢失
```

这是有意的取舍：

```text
先保证响应速度和角色稳定
再考虑长期记忆
```

下一步可以加：

```text
用户级长期偏好
会话摘要
重要事实抽取
文章阅读历史
```

但不能简单把所有历史塞进 prompt。

---

## 八、流式输出

`DigitalHumanStreamService` 使用：

```text
SSE
```

事件：

```text
meta
delta
motion
audio-pcm
done
error
```

文本先到，语音和动作并行流水线。

---

## 九、建设顺序

```text
1. 先有 session / message 存储
2. 再接 ChatClient
3. 再做角色 systemPrompt
4. 再注入最近历史
5. 再接当前文章上下文
6. 再做文章搜索 / 读取工具
7. 最后做流式语音和动作
```

先做语音会让问题很难定位：

```text
是模型答错
是文章没读到
还是历史没带上
```

---

## 十、结论

数字人的对话流水线可以概括为：

```text
角色决定是谁
历史决定关系
文章决定知识
工具决定深挖
SSE 决定体验
```

记忆不是越多越好，而是：

```text
在正确的时机，给模型正确的上下文
```
