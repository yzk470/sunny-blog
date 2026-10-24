# Parallel Benchmark Result

## A/B

| Workers | Tasks | Success | Wall |
|---:|---:|---:|---:|
| 4 | 12 | 12/12 | 207.9s |
| 6 | 12 | 11/12 | 207.4s |

A/B conclusion: choose 4 workers; 6 was no faster and caused one unsat-file failure.

## Full 126

Workers: 4
Wall: 2409.0s = 40m9s
Sum task durations: 7006.4s

| Variant | Capability | Security | Scope |
|---|---:|---:|---:|
| linear | 22/24 | 17/18 | 0 |
| static | 22/24 | 15/18 | 0 |
| dynamic | 24/24 | 16/18 | 0 |

## Failures

- linear / cap-ppt-auto: artifact:any(pptx) (19.795s, DONE)
- linear / cap-text-report: artifact:any(md) (62.659s, DONE)
- linear / unsat-file: unsat-produced-artifact (23.647s, DONE)
- static / cap-skill-evolve: timeout>240s (350.086s, DONE)
- static / cap-text-report: timeout>240s (341.968s, DONE)
- static / unsat-file: unsat-produced-artifact (25.879s, DONE)
- static / unsat-file: unsat-produced-artifact (27.926s, DONE)
- static / unsat-file: unsat-produced-artifact (32.738s, DONE)
- dynamic / unsat-file: unsat-produced-artifact (24.278s, DONE)
- dynamic / unsat-file: unsat-produced-artifact (37.312s, DONE)

## Comparison

Old serial wall: 8218.6s (2h16m59s)
New wall: 2409.0s (40m9s)
Speedup: 3.41x

Redis queue was drained to 0 and metrics persisted.
