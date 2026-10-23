# Agent Benchmark

Base: `https://www.bluish-white.top`

| Variant | Tasks | Success | First attempt | Recovered | Avg attempts | Avg budget | Cost/success | P95 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| linear | 5 | 100.0% | 4 | 1 | 1 | 9.4 | 9.4 | 30.136s |
| static | 5 | 100.0% | 3 | 2 | 1.6 | 17.8 | 17.8 | 71.298s |
| dynamic | 5 | 100.0% | 4 | 1 | 1.2 | 12.6 | 12.6 | 35.946s |

## Task results

| Variant | Task | Success | Status | Attempts | Budget | Duration | Failures |
|---|---|---:|---|---:|---:|---:|---|
| linear | simple-answer | ✅ | DONE | 1 | 4 | 9.729s |  |
| linear | script-run | ✅ | DONE | 1 | 3 | 10.345s |  |
| linear | artifact-text | ✅ | DONE | 1 | 12 | 30.136s |  |
| linear | artifact-html | ✅ | DONE | 1 | 12 | 38.738s |  |
| static | simple-answer | ✅ | DONE | 1 | 4 | 9.018s |  |
| static | script-run | ✅ | DONE | 1 | 3 | 9.789s |  |
| static | artifact-text | ✅ | DONE | 3 | 36 | 143.536s |  |
| static | artifact-html | ✅ | DONE | 2 | 22 | 71.298s |  |
| dynamic | simple-answer | ✅ | DONE | 1 | 4 | 11.099s |  |
| dynamic | script-run | ✅ | DONE | 1 | 3 | 12.051s |  |
| dynamic | artifact-text | ✅ | DONE | 1 | 12 | 35.946s |  |
| dynamic | artifact-html | ✅ | DONE | 2 | 20 | 58.702s |  |
| linear | plan-three-points | ✅ | DONE | 1 | 16 | 12.441s |  |
| static | plan-three-points | ✅ | DONE | 1 | 24 | 17.912s |  |
| dynamic | plan-three-points | ✅ | DONE | 1 | 24 | 11.78s |  |
