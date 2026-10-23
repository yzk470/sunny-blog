import json
import statistics
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")

root = Path('benchmarks/agent/reports')
old = json.loads((root / 'fast-with-old-plan.json').read_text(encoding='utf-8'))
new = json.loads((root / 'latest.json').read_text(encoding='utf-8'))
rows = [r for r in old['rows'] if r['task'] != 'plan-three-points'] + new['rows']
variants = ['linear', 'static', 'dynamic']


def summary(items):
    total = len(items)
    successes = sum(1 for r in items if r['success'])
    failed = [r for r in items if not r['success']]
    recovered = [r for r in items if r['attemptFailed'] and r['success']]
    durations = [r['duration'] for r in items]
    budgets = [r['taskSpent'] for r in items]
    attempts = [r['attemptCount'] for r in items]
    return {
        'tasks': total,
        'successes': successes,
        'successRate': round(successes / total, 4) if total else 0,
        'firstAttemptSuccess': sum(1 for r in items if r['success'] and not r['attemptFailed']),
        'recovered': len(recovered),
        'recoveryRate': round(len(recovered) / max(1, sum(1 for r in items if r['attemptFailed'])), 4),
        'avgAttempts': round(statistics.mean(attempts), 2) if attempts else 0,
        'avgBudget': round(statistics.mean(budgets), 2) if budgets else 0,
        'costPerSuccess': round(sum(budgets) / max(1, successes), 2),
        'p95Seconds': sorted(durations)[max(0, int(len(durations) * 0.95) - 1)] if durations else 0,
        'failureTypes': sorted({f for r in failed for f in r['failureTypes']}),
    }


summary_map = {v: summary([r for r in rows if r['variant'] == v]) for v in variants}
report = {'base': old['base'], 'timestamp': new['timestamp'], 'rows': rows, 'summary': summary_map}
(root / 'latest.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')

lines = ['# Agent Benchmark', '', f"Base: `{old['base']}`", '',
         '| Variant | Tasks | Success | First attempt | Recovered | Avg attempts | Avg budget | Cost/success | P95 |',
         '|---|---:|---:|---:|---:|---:|---:|---:|---:|']
for v in variants:
    s = summary_map[v]
    lines.append(f"| {v} | {s['tasks']} | {s['successRate']*100:.1f}% | {s['firstAttemptSuccess']} | "
                 f"{s['recovered']} | {s['avgAttempts']} | {s['avgBudget']} | {s['costPerSuccess']} | {s['p95Seconds']}s |")
lines.extend(['', '## Task results', '',
              '| Variant | Task | Success | Status | Attempts | Budget | Duration | Failures |',
              '|---|---|---:|---|---:|---:|---:|---|'])
for r in rows:
    lines.append(f"| {r['variant']} | {r['task']} | {'✅' if r['success'] else '❌'} | {r['status']} | "
                 f"{r['attemptCount']} | {r['taskSpent']} | {r['duration']}s | {', '.join(r['failures'])} |")
(root / 'latest.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')
print('\n'.join(lines))
