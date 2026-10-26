# 从一次问答到可验收执行：Agent 的执行循环、工具和产物

> 这是「月白小筑 AI 系统工程笔记」Agent 系列第 1 篇。  
> 目标：先让 Agent 真的干活，再谈性能和缓存。

## 一、最初的问题

最初的 Agent 很像一个会聊天的脚本：

```text
用户问题
→ 模型回答
```

问题很明显：

```text
模型说“已完成”，但文件不存在
模型说“脚本已运行”，但没有输出
模型说“PPT 已生成”，但只有文字说明
```

所以第一版真正要做的不是“更聪明”，而是：

```text
让 Agent 能调工具
让产物真的落盘
让结果可以被检查
```

---

## 二、四层结构

### 2.1 Session

一次持续对话。

```text
sessionId
→ 保存历史记忆
→ 同一 session 串行执行
```

### 2.2 Run

一次用户任务。

```text
runId
→ 一次任务的状态
→ 可以暂停、恢复、取消
```

### 2.3 Attempt

一次尝试。

```text
attemptId
→ 一个策略的一次执行
→ 独立产物目录
```

### 2.4 Tool

一次具体动作。

```text
read_file / write_file / list_files
run_script / run_command
search_knowledge / inspect_artifact
```

这四层解决了一个核心问题：

```text
会话可以有记忆
任务可以有重试
尝试可以独立
工具可以有边界
```

---

## 三、执行循环

### 3.1 先预检

任务进入执行前先做一次低成本检查：

```text
workspace / artifacts 是否可写
工具目录是否为空
任务是否需要执行能力
run_script 是否存在
```

代码：

`AgentPreflightService`

这不是语义理解，而是避免把预算花在明显坏掉的环境上。

### 3.2 再生成策略

`AgentStrategyService` 会生成 2～3 条不同路线：

```text
直接工具路线
拆分降级路线
替代实现路线
```

每条路线包含：

```text
方法
主要风险
失败后的备选
```

失败被视为常态，而不是异常。

### 3.3 ReAct 或 Plan

短任务走 ReAct：

```text
思考
→ 调工具
→ 看结果
→ 继续思考
```

长任务走 Plan：

```text
拆步骤
→ 逐步执行
→ 每步观察
→ 最后汇总
```

### 3.4 工具执行

工具调用不是随便执行字符串。

`AgentToolExecutor` 有明确动作白名单：

```text
list_skills
read_skill
read_file
write_file
list_files
delete_file
inspect_artifact
search_knowledge
run_script
run_command
```

写文件只能落在：

```text
workspace/
artifacts/
skills/
```

其他路径返回 `POLICY_DENIED`。

### 3.5 产物落盘

每个 attempt 有独立目录：

```text
artifacts/<runId>/attempts/<attemptId>/
```

这样做的原因：

```text
重试不会覆盖前一次
失败可以追溯
验收能看到真实文件
```

---

## 四、产物验收

### 4.1 先看真实文件

如果任务要求：

```text
pptx / docx / xlsx / pdf / html / 图片
```

产物清单里必须有对应文件。

只有文字说明，不能通过。

### 4.2 再看内容

不同格式有不同检查：

```text
PPT：页数、文件存在、可打开
DOCX：段落、文件存在
脚本：真实执行输出
HTML：文件存在、可读取
```

### 4.3 最后独立验收

执行者说完成不算完成。

会再调用一个独立验收员：

```text
只能看任务、候选结果、产物清单、成功标准
不能看执行者思考过程
```

输出：

```json
{"passed": true, "reason": "产物存在且符合要求"}
```

失败则进入修复或重试。

> 独立验收的细节单独一篇讲。

---

## 五、UNSAT：明确拒绝

Agent 最危险的不是失败，而是“为了完成而编造”。

现在遇到以下情况会进入 `BLOCKED`：

```text
当前环境没有要求的工具
任务要求读取不存在的文件
工具结果明确不可用
```

处理：

```text
不生成假产物
删除本次 run 的 artifacts
回答明确说明不可满足
记录 BLOCKED 状态
```

这比“看起来完成”更重要。

---

## 六、暂停与恢复

Run 有 checkpoint：

```text
phase
current strategy
tool records
attempt records
next step
```

用户取消：

```text
状态改为 CANCEL_REQUESTED
写入 checkpoint
停止继续消耗预算
```

恢复：

```text
从 checkpoint 继续
不重新跑已经完成的步骤
```

---

## 七、隔离

### 7.1 Session 串行

同一 session 同时只允许一个 run：

```text
ReentrantLock per session
```

避免两个任务同时改同一份记忆。

### 7.2 Run 隔离

不同 run 有不同：

```text
artifact 目录
预算
状态
工具记录
```

### 7.3 Attempt 隔离

不同 attempt 有不同：

```text
artifact 目录
策略
验证结果
```

---

## 八、建设顺序

这部分如果重来一次，会按这个顺序做：

```text
1. 先做 ReAct 单轮工具调用
2. 再做 artifact 目录和真实文件检查
3. 再做策略与失败重试
4. 再做 run / attempt 隔离
5. 再做预检和 UNSAT
6. 再做 checkpoint 与恢复
7. 最后做独立验收和 benchmark
```

原因：

```text
没有产物，验收没有意义
没有隔离，重试会互相污染
没有预检，预算会浪费在环境错误上
```

---

## 九、这一篇的结论

Agent 的第一性原理不是“会思考”，而是：

```text
能在边界内行动
能留下真实证据
能被另一个角色检查
失败时能停下来
```

先做到这四件事，后面的并发、缓存、性能优化才有意义。
