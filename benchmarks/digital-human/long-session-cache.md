# Digital Human Long-Session Cache Test

## Configuration

```text
endpoint: POST /api/digital-human/chat/plain
TTS: disabled
motion: disabled
model: deepseek-flash
date: 2026-10-28
```

The plain endpoint still stores user/assistant messages, so every turn uses the
same canonical message list as the normal chat path, without audio or motion.

## 20 turns, with article tools

```text
promptTokens: 123,016
cachedTokens: 74,624
missTokens: 48,392
completionTokens: 10,923
hitRate: 60.66%
```

Persisted state:

```text
summary length: 4,019 chars
summary_upto_id: 305
tool calls: search_articles 4, read_article 2
```

## 12 turns, no article tools

```text
promptTokens: 23,232
cachedTokens: 19,584
missTokens: 3,648
completionTokens: 488
hitRate: 84.30%
```

## Why not 99%

```text
first-turn cold start
tool calls change the request shape
summary replaces older context at the threshold
new user/assistant tokens are unavoidable
session is still short
```

The no-tool session is the cleaner measurement of the append-only message list:

```text
84.30% on 12 turns
```

The with-tool session now replays standard tool call / tool response messages.
The article text itself is a large fresh payload, so prefix reuse is lower:

```text
60.66% on 20 turns
```
