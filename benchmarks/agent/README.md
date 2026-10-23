# Agent Benchmark

离线/在线 Agent 基准测试。它通过真实 HTTP/SSE 调用月白智能体，不使用模拟模型。

## 任务集

`tasks.json` 覆盖：

- 简单问答
- 脚本执行
- 文本产物
- HTML 产物
- Plan 多步骤
- 高价值系统设计

每个任务包含：

```json
{
  "id": "script-run",
  "message": "写一个 Python 脚本计算 2+2 并运行，告诉我结果。",
  "mode": "react",
  "checks": {
    "contains": ["4"],
    "execution": true,
    "artifactExt": ["txt"],
    "maxSeconds": 120
  }
}
```

## 变体

通过请求字段 `benchmarkVariant` 对比：

| 变体 | 说明 |
|---|---|
| `linear` | 单路线、只取一条策略，16 预算 |
| `static` | 生成 3 条静态路线，不动态扩展 |
| `dynamic` | 当前实现：反思、剪枝、重评分、修复分支、高价值搜索 |

## 运行

```bash
# 线上
python benchmarks/agent/run_benchmark.py

# 本地
$env:BENCHMARK_BASE_URL="http://127.0.0.1:8090"
python benchmarks/agent/run_benchmark.py

# 只跑当前实现
python benchmarks/agent/run_benchmark.py --variants dynamic --repeats 1

# 多轮（LLM 有随机性，建议 3 轮）
python benchmarks/agent/run_benchmark.py --repeats 3
```

## 输出

```text
benchmarks/agent/reports/latest.json
benchmarks/agent/reports/latest.md
```

指标：

- 任务成功率
- 首次尝试成功率
- 失败后恢复成功率
- 平均尝试次数
- 平均预算
- 单次成功成本
- P95 耗时
- 失败类型

## 说明

- 这是**基准任务集**，不是单元测试；每次会真实调用模型和工具。
- LLM 输出具有随机性，单轮结果仅供参考，建议多轮。
- 高价值任务（如系统设计）会启用更贵的最佳优先搜索。
- 失败注入的单元测试在 `AgentSearchTest`；该 runner 负责真实端到端任务指标。
