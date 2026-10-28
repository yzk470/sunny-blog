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
promptTokens: 88,358
cachedTokens: 58,624
missTokens: 29,734
completionTokens: 9,193
hitRate: 66.35%
```

Persisted state:

```text
summary length: 3,150 chars
summary_upto_id: 241
tool calls: search_articles 4, read_article 1
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

The with-tool session includes tool-call turns that break reuse:

```text
66.35% on 20 turns
```
