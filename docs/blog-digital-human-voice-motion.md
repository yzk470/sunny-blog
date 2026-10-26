# 数字人的声音与动作：两条流怎么同时跑起来

> 这是「月白小筑 AI 系统工程笔记」数字人系列第 2 篇。  
> 关键词：SSE、流式 TTS、PCM、动作队列、口型

## 一、要同时做到三件事

数字人回复时，用户会同时感知：

```text
文字在出现
声音在播放
身体在动
```

如果串行：

```text
等文字全部生成
→ 再合成语音
→ 再等语音完成
→ 再播放动作
```

体验会很慢。

所以目标是：

```text
文字先流
语音边生成边播
动作按语义排队
口型跟随音频
```

不是三条流完全同一时刻开始，而是三条流同时推进。

---

## 二、后端先发 meta

`DigitalHumanStreamService` 一开始发送：

```json
{
  "sessionId": 84,
  "action": "none",
  "emotion": "neutral",
  "motionPrompt": "",
  "motionStreaming": true,
  "audioStreaming": true,
  "audioFormat": "pcm",
  "audioSampleRate": 24000
}
```

前端据此知道：

```text
后端会主动推动作
后端会主动推 PCM 音频
```

---

## 三、动作怎么来

模型输出里带控制标签：

```text
[[motion:wave,emotion:happy]]
早上好。
```

`MotionStreamParser` 逐 token 解析：

```text
文本 token → 立即转发
motion 标签 → 转成 motion 事件
```

标签可能被拆成多个 token，所以要缓存到 `]]` 结束。

允许的动作：

```text
wave / salute / nod / bow / think / happy / shy /
stretch / shrug / cheer / letsgo / handclap / spin /
warn / calm / lonely / beckon / rubhands
```

后端发送：

```json
{"seq": 1, "motion": "wave", "emotion": "happy"}
```

---

## 四、语音怎么流

### 4.1 文本先进入缓冲

`textConsumer` 同时做两件事：

```text
把文字 delta 发给前端
把文字追加到 speechBuffer
```

### 4.2 什么时候开始 TTS

第一次：

```text
语音文本 >= 12 字符
或 >= 5 字符且遇到句读
```

之后：

```text
>= 24 字符
或遇到句读
```

这样可以：

```text
尽快开口
又不会每两个字就发一次 TTS
```

### 4.3 PCM 分片

TTS 返回 PCM：

```text
base64 编码
seq
sampleRate
```

通过 SSE：

```text
audio-pcm
```

发给前端。

### 4.4 文本结束不等于语音结束

后端先发送 `done`，但保持 SSE 连接：

```text
tts.awaitFinished(90s)
emitter.complete()
```

原因：

```text
文字可以先完成
音频还在流
不能提前断开连接
```

---

## 五、前端怎么同时播放

### 5.1 动作队列

前端收到 `motion`：

```text
setEmotion
motionQueue.push(motion)
```

动作队列串行播放：

```text
一个动作完成
→ 下一个
```

不会所有动作同时叠加。

### 5.2 音频调度

前端收到 `audio-pcm`：

```text
AudioContext 创建 buffer
→ 计算 startAt = max(now + 0.025, pcmNextTime)
→ source.start(startAt)
→ pcmNextTime = startAt + duration
```

这样多个 PCM 分片不会互相覆盖，而是按时间连续播放。

### 5.3 口型

音频接入 `SceneManager` 的 lipSync：

```text
audio element / audio context
→ 分析音量
→ 驱动口型
```

没有音频时，口型不应自己动。

### 5.4 动作和语音的时序

前端在以下时机启动动作队列：

```text
收到第一个文字 delta（没有服务端音频时）
收到第一个音频
收到 audio error
```

如果动作标签先到，动作会先入队，等文本/音频开始后播放。

所以是：

```text
动作准备
→ 文本开始
→ 音频开始
→ 动作与音频并行
```

不是严格帧级同步，但用户感知是同时发生。

---

## 六、失败降级

### 6.1 TTS 失败

发送：

```text
audio-pcm-error
```

前端：

```text
提示语音不可用
文字继续
动作继续
```

### 6.2 动作失败

`playGesture` 失败会：

```text
恢复表情
回到 idle
继续播放
```

不阻塞整条回复。

### 6.3 流超时

前端首字超时：

```text
15 秒
abort
```

后端流最大：

```text
5 分钟
```

---

## 七、为什么不用等语音再显示文字

因为用户对延迟最敏感的是：

```text
第一个字什么时候出现
```

所以：

```text
文字先出
语音追上
动作穿插
```

这是体验层的并行。

---

## 八、建设顺序

```text
1. 先有文字流
2. 再做动作标签解析
3. 再做分段 TTS
4. 再做 PCM 流式返回
5. 再做前端音频调度
6. 最后做口型和动作队列
```

先做口型，会被音频分片和文字流的问题拖死。

---

## 九、结论

数字人的“同时”不是一条线程同时干三件事，而是：

```text
文字流：LLM token
动作流：motion 事件
语音流：TTS PCM
口型：音频分析
```

四条流各自推进，在时间轴上汇合。

工程上要保证的是：

```text
文字不阻塞
语音不丢片
动作不叠乱
连接不早断
```
